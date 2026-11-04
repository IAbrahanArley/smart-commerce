"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { formatDateTime } from "@/lib/cash";
import { type CreditCustomer, type CreditFilter, type CreditOverview, type Customer, FILTERS, installmentText, nameError, phoneDigits, phoneText, withinOwed } from "@/lib/credit";
import { formatDay, parseAmount, parseOptionalAmount } from "@/lib/expenses";
import type { TenantStore } from "@/lib/tenant-api";
import CustomerPicker from "@/components/customer-picker";
import PageHeader from "@/components/page-header";
import ListPager from "@/components/list-pager";
import { AddButton, FormSheet } from "@/components/form-sheet";
import { Button } from "@/components/ui/button";
import StatusBadge from "@/components/status-badge";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";

type State = "loading" | "ready" | "signed-out" | "missing" | "error";
type Draft = { name: string; phone: string; email: string; note: string; limit: string };
type DraftErrors = Partial<Record<keyof Draft | "form", string>>;

const EMPTY: Draft = { name: "", phone: "", email: "", note: "", limit: "" };

// SC-055 (ADR-0019 C9): store credit. The OWNER sees every customer with what they owe and what is
// overdue, opens a customer's file, edits it and receives; an employee only finds the customer
// being served (phone masked) and receives what they pay. The backend decides every figure.
export default function StoreCredit({ tenantId, filter, customerId }: { tenantId: string; filter: CreditFilter; customerId: string | null }) {
  const [role, setRole] = useState<TenantStore["role"] | null>(null);
  const [state, setState] = useState<State>("loading");
  const [attempt, setAttempt] = useState(0);
  const router = useRouter();
  const base = `/lojas/${encodeURIComponent(tenantId)}/crediario`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const store: TenantStore = await response.json();
      if (!controller.signal.aborted) { setRole(store.role); setState("ready"); }
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [tenantId, attempt]);

  const owner = role === "OWNER";
  return <section className="page" aria-labelledby="credit-title">
    <PageHeader id="credit-title" title="Crediário"
      description={owner ? "Clientes que compram na loja para pagar depois: o que cada um deve, o que venceu e o que já foi recebido." : "Receba as parcelas dos clientes do crediário, no caixa aberto ou numa conta bancária."} />
    {state === "loading" ? <Loading context="crediário" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar o crediário.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="wallet" title="Não foi possível consultar o crediário" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {state === "ready" && role ? customerId
      ? <CustomerFile key={customerId} tenantId={tenantId} customerId={customerId} owner={owner} back={base} />
      : owner ? <OwnerList tenantId={tenantId} filter={filter} base={base} />
        : <div className="grid gap-3">
          <p className="field-help">Encontre o cliente para ver o que ele deve e registrar o pagamento.</p>
          <CustomerPicker tenantId={tenantId} idPrefix="credit-customer" onSelect={customer => router.push(`${base}?cliente=${encodeURIComponent(customer.id)}`)} />
        </div> : null}
  </section>;
}

function OwnerList({ tenantId, filter, base }: { tenantId: string; filter: CreditFilter; base: string }) {
  const [data, setData] = useState<CreditOverview | null>(null);
  const [state, setState] = useState<State>("loading");
  const [attempt, setAttempt] = useState(0);
  const [before, setBefore] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const router = useRouter();
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/credit`;
  const slug = (value: CreditFilter) => FILTERS.find(item => item.value === value)!.slug;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`${api}?filter=${filter}${before ? `&before=${encodeURIComponent(before)}` : ""}`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: CreditOverview = await response.json();
      if (!controller.signal.aborted) { setData(current); setState("ready"); }
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, filter, before, attempt]);

  return <>
    <div className="form-actions"><AddButton onClick={() => setCreating(true)}>Novo cliente</AddButton></div>
    <nav className="status-tabs" aria-label="Filtrar clientes do crediário">
      {FILTERS.map(item => <Link key={item.value} href={item.value === "ALL" ? base : `${base}?situacao=${item.slug}`} prefetch={false} aria-current={filter === item.value ? "page" : undefined}>{item.label}</Link>)}
    </nav>
    {state === "loading" ? <Loading context="clientes do crediário" /> : null}
    {state === "signed-out" ? <p role="status">Sua sessão expirou. <Link href="/entrar">Entre novamente</Link>.</p> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="wallet" title="Não foi possível consultar os clientes" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {state === "ready" && data ? <>
      <dl className="order-facts expense-totals">
        <div><dt>A receber no crediário</dt><dd className="numeric"><strong>{formatBRL(data.open)}</strong></dd></div>
        <div><dt>Vencido</dt><dd className="numeric">{data.overdueCustomers ? <Link href={`${base}?situacao=${slug("OVERDUE")}`} prefetch={false}>{formatBRL(data.overdue)} de {data.overdueCustomers} {data.overdueCustomers === 1 ? "cliente" : "clientes"}</Link> : "Nada vencido"}</dd></div>
      </dl>
      {data.customers.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
        <caption>{filter === "OVERDUE" ? "Clientes com parcelas vencidas" : filter === "OPEN" ? "Clientes com saldo em aberto" : "Clientes da loja, os mais recentes primeiro"}</caption>
        <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Cliente</th><th role="columnheader" scope="col" className="numeric">Em aberto</th><th role="columnheader" scope="col">Próximo vencimento</th><th role="columnheader" scope="col">Situação</th></tr></thead>
        <tbody role="rowgroup">{data.customers.map(line => <tr role="row" key={line.id}>
          <th role="rowheader" scope="row"><Link href={`${base}?cliente=${encodeURIComponent(line.id)}`} prefetch={false}>{line.name}</Link><span className="cell-hint">{phoneText(line.phone)}{line.creditLimit ? ` · limite ${formatBRL(line.creditLimit)}` : ""}</span></th>
          <td role="cell" data-label="Em aberto" className="numeric">{formatBRL(line.open)}{line.overdue !== "0.00" ? <span className="cell-hint">{formatBRL(line.overdue)} vencido</span> : null}</td>
          <td role="cell" data-label="Próximo vencimento">{formatDay(line.nextDueOn)}</td>
          <td role="cell" data-label="Situação"><StatusBadge tone={line.overdue !== "0.00" ? "danger" : line.open !== "0.00" ? "neutral" : "positive"}>{line.overdue !== "0.00" ? "Vencido" : line.open !== "0.00" ? "Em dia" : "Sem saldo"}</StatusBadge></td>
        </tr>)}</tbody>
      </table></div> : <PageState icon="users" title={filter === "ALL" ? "Nenhum cliente cadastrado" : "Nenhum cliente nesta situação"}>{filter === "OVERDUE" ? "Nenhuma parcela vencida. Tudo em dia." : "Os clientes aparecem aqui quando são cadastrados, aqui ou numa venda no crediário."}</PageState>}
      <ListPager label="Páginas de clientes do crediário"
        first={before ? { text: "Voltar aos mais recentes", onClick: () => setBefore(null) } : null}
        next={data.nextCursor ? { text: "Ver clientes mais antigos", onClick: () => setBefore(data.nextCursor) } : null} />
    </> : null}
    <CustomerSheet key={creating ? "open" : "closed"} tenantId={tenantId} open={creating} customer={null} onClose={() => setCreating(false)}
      onSaved={customer => { setCreating(false); router.push(`${base}?cliente=${encodeURIComponent(customer.id)}`); }} />
  </>;
}

function CustomerFile({ tenantId, customerId, owner, back }: { tenantId: string; customerId: string; owner: boolean; back: string }) {
  const [data, setData] = useState<CreditCustomer | null>(null);
  const [state, setState] = useState<State>("loading");
  const [attempt, setAttempt] = useState(0);
  const [before, setBefore] = useState<string | null>(null);
  const [amount, setAmount] = useState("");
  const [destination, setDestination] = useState("");
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [errors, setErrors] = useState<{ amount?: string; destination?: string }>({});
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/credit/customers/${encodeURIComponent(customerId)}`;
  useUnsavedChanges(Boolean(amount) || busy);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`${api}${before ? `?before=${encodeURIComponent(before)}` : ""}`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: CreditCustomer = await response.json();
      if (controller.signal.aborted) return;
      setData(current); setState("ready");
      // A drawer only through its open session; otherwise a bank account (the backend enforces it).
      const options = [...current.sessions.map(item => `session:${item.id}`), ...current.banks.map(item => `bank:${item.id}`)];
      setDestination(chosen => options.includes(chosen) ? chosen : options[0] ?? "");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, before, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  async function receive(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !data) return;
    const parsed = parseAmount(amount);
    const next: typeof errors = {};
    if ("error" in parsed) next.amount = parsed.error;
    else if (!withinOwed(parsed.amount, data.open)) next.amount = `O valor passa do que o cliente deve (${formatBRL(data.open)}).`;
    if (!destination) next.destination = "Abra o caixa ou cadastre uma conta bancária para receber.";
    setErrors(next);
    if (next.amount || next.destination) { document.getElementById(next.amount ? "credit-amount" : "credit-destination")?.focus(); return; }
    const [kind, id] = destination.split(":");
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`${api}/receipts`, { method: "POST", body: {
        requestId, amount: "amount" in parsed ? parsed.amount : null, cashSessionId: kind === "session" ? id : null, accountId: kind === "bank" ? id : null } });
      if (response.ok) {
        const current: CreditCustomer = await response.json();
        setData(current); setAmount(""); setRequestId(crypto.randomUUID()); setBefore(null);
        setFeedback({ text: `Recebimento de ${formatBRL("amount" in parsed ? parsed.amount : "0.00")} registrado. ${current.open === "0.00" ? "O cliente não deve mais nada." : `Ainda em aberto: ${formatBRL(current.open)}.`}`, error: false });
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de registrar o recebimento.", error: true }); return; }
      const answer = await response.json().catch(() => null) as { message?: string; code?: string } | null;
      if (answer?.code === "REQUEST_CONFLICT") setRequestId(crypto.randomUUID());
      if (response.status === 409 || response.status === 404) setAttempt(current => current + 1);
      setFeedback({ text: answer?.message ?? requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar o recebimento. Tente de novo: a mesma tentativa não registra duas vezes.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  const feedbackNode = feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback ${feedback.error ? "feedback-error" : "feedback-success"}`}>{feedback.text}</p> : null;
  const customer = data?.customer;
  return <>
    <p><Link href={back} prefetch={false}><span aria-hidden="true">‹</span> {owner ? "Voltar aos clientes" : "Buscar outro cliente"}</Link></p>
    {state === "loading" ? <Loading context="ficha do cliente" /> : null}
    {state === "signed-out" ? <p role="status">Sua sessão expirou. <Link href="/entrar">Entre novamente</Link>.</p> : null}
    {state === "missing" ? <PageState icon="users" title="Cliente não encontrado" href={back} actionLabel="Voltar ao crediário">Este cliente não existe nesta loja.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="wallet" title="Não foi possível consultar o cliente" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {state === "ready" && data && customer ? <>
      <section aria-labelledby="credit-customer-title" className="grid gap-2">
        <h2 id="credit-customer-title" className="m-0">{customer.name}</h2>
        <p className="m-0 field-help">{phoneText(customer.phone)}{customer.email ? ` · ${customer.email}` : ""}</p>
        {customer.note ? <p className="m-0">{customer.note}</p> : null}
        {owner ? <div><Button type="button" variant="outline" onClick={() => { setFeedback(null); setEditing(true); }}>Editar cliente</Button></div> : null}
      </section>
      {editing ? null : feedbackNode}
      <dl className="order-facts expense-totals">
        <div><dt>Em aberto</dt><dd className="numeric"><strong>{formatBRL(data.open)}</strong></dd></div>
        <div><dt>Vencido</dt><dd className="numeric">{data.overdue === "0.00" ? "Nada vencido" : formatBRL(data.overdue)}</dd></div>
        {owner ? <div><dt>Crédito disponível</dt><dd className="numeric">{data.availableCredit === null ? "Sem limite" : formatBRL(data.availableCredit)}{customer.creditLimit ? <span className="cell-hint">limite {formatBRL(customer.creditLimit)}</span> : null}</dd></div> : null}
      </dl>
      <section aria-labelledby="credit-installments-title">
        <h2 id="credit-installments-title">Parcelas em aberto</h2>
        {data.installments.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
          <caption>Mais antigas primeiro: um pagamento quita primeiro a parcela mais antiga.</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Parcela</th><th role="columnheader" scope="col">Vencimento</th><th role="columnheader" scope="col" className="numeric">Valor</th><th role="columnheader" scope="col" className="numeric">Falta</th></tr></thead>
          <tbody role="rowgroup">{data.installments.map(item => <tr role="row" key={item.id}>
            <th role="rowheader" scope="row">{installmentText(item)}<span className="cell-hint">Venda de {formatDateTime(item.soldAt)}</span></th>
            <td role="cell" data-label="Vencimento">{formatDay(item.dueOn)}{item.overdue ? <> <StatusBadge tone="danger">Vencida</StatusBadge></> : null}</td>
            <td role="cell" data-label="Valor" className="numeric">{formatBRL(item.amount)}{item.received !== "0.00" ? <span className="cell-hint">recebido {formatBRL(item.received)}</span> : null}</td>
            <td role="cell" data-label="Falta" className="numeric">{formatBRL(item.open)}</td>
          </tr>)}</tbody>
        </table></div> : <p className="field-help">Nenhuma parcela em aberto.</p>}
      </section>
      {data.open !== "0.00" ? <section aria-labelledby="credit-receive-title">
        <h2 id="credit-receive-title">Registrar recebimento</h2>
        <form onSubmit={event => void receive(event)} noValidate aria-labelledby="credit-receive-title">
          <div className="form-field">
            <label htmlFor="credit-amount">Valor recebido (R$)</label>
            <p id="credit-amount-help" className="field-help">Qualquer valor até {formatBRL(data.open)}. Pode ser só parte de uma parcela.</p>
            <input id="credit-amount" disabled={busy} value={amount} inputMode="decimal" autoComplete="off" maxLength={24} onChange={event => { setAmount(event.target.value); setErrors(current => ({ ...current, amount: undefined })); }}
              aria-invalid={errors.amount ? true : undefined} aria-describedby={["credit-amount-help", errors.amount ? "credit-amount-error" : null].filter(Boolean).join(" ")} />
            {errors.amount ? <p id="credit-amount-error" className="field-error">{errors.amount}</p> : null}
          </div>
          <div className="form-field">
            <label htmlFor="credit-destination">Onde o dinheiro entrou</label>
            {data.sessions.length || data.banks.length ? <select id="credit-destination" disabled={busy} value={destination} onChange={event => { setDestination(event.target.value); setErrors(current => ({ ...current, destination: undefined })); }}
              aria-invalid={errors.destination ? true : undefined} aria-describedby={errors.destination ? "credit-destination-error" : undefined}>
              {data.sessions.map(item => <option key={item.id} value={`session:${item.id}`}>Dinheiro no caixa aberto: {item.name}</option>)}
              {data.banks.map(item => <option key={item.id} value={`bank:${item.id}`}>Conta bancária: {item.name}</option>)}
            </select> : <p id="credit-destination" tabIndex={-1} className="field-help">Nenhum caixa aberto nem conta bancária. <Link href={`/lojas/${encodeURIComponent(tenantId)}/caixa`}>Abrir o caixa</Link> para receber em dinheiro.</p>}
            {errors.destination ? <p id="credit-destination-error" className="field-error">{errors.destination}</p> : null}
          </div>
          <div className="form-actions"><button type="submit" disabled={busy}>{busy ? "Registrando…" : "Registrar recebimento"}</button></div>
        </form>
      </section> : null}
      {owner ? <section aria-labelledby="credit-history-title">
        <h2 id="credit-history-title">Histórico</h2>
        {data.history.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
          <caption>Vendas no crediário e recebimentos, os mais recentes primeiro</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Lançamento</th><th role="columnheader" scope="col" className="numeric">Valor</th><th role="columnheader" scope="col">Quando</th></tr></thead>
          <tbody role="rowgroup">{data.history.map(item => <tr role="row" key={`${item.kind}-${item.id}`}>
            <th role="rowheader" scope="row">{item.kind === "SALE" ? `Venda no crediário${item.installmentCount && item.installmentCount > 1 ? ` em ${item.installmentCount} parcelas` : ""}` : `Recebimento em ${item.accountName}`}
              {item.status === "CANCELLED" ? <> <StatusBadge tone="neutral">Cancelada</StatusBadge></> : null}<span className="cell-hint">por {item.by}</span></th>
            <td role="cell" data-label="Valor" className="numeric">{item.status === "CANCELLED" ? <s>{formatBRL(item.amount)}</s> : formatBRL(item.amount)}</td>
            <td role="cell" data-label="Quando">{formatDateTime(item.at)}</td>
          </tr>)}</tbody>
        </table></div> : <p className="field-help">Nenhuma venda nem recebimento ainda.</p>}
        <ListPager label="Páginas do histórico do cliente" disabled={busy}
          first={before ? { text: "Voltar aos mais recentes", onClick: () => setBefore(null) } : null}
          next={data.nextCursor ? { text: "Ver lançamentos mais antigos", onClick: () => setBefore(data.nextCursor) } : null} />
      </section> : null}
      {owner ? <CustomerSheet key={editing ? "open" : "closed"} tenantId={tenantId} open={editing} customer={customer} onClose={() => setEditing(false)}
        onSaved={() => { setEditing(false); setAttempt(current => current + 1); setFeedback({ text: "Cliente atualizado.", error: false }); }} /> : null}
    </> : null}
  </>;
}

// The OWNER's form: new customer (POST, idempotent by request id) or edit (PUT).
function CustomerSheet({ tenantId, open, customer, onClose, onSaved }: { tenantId: string; open: boolean; customer: Customer | null; onClose: () => void; onSaved: (customer: Customer) => void }) {
  // Mounted again on every opening (key in the callers), so the form starts from the customer.
  const [draft, setDraft] = useState<Draft>(() => customer ? { name: customer.name, phone: phoneText(customer.phone), email: customer.email ?? "", note: customer.note ?? "", limit: customer.creditLimit?.replace(".", ",") ?? "" } : EMPTY);
  const [errors, setErrors] = useState<DraftErrors>({});
  const [busy, setBusy] = useState(false);
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const submitting = useRef(false);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const next: DraftErrors = {};
    const nameProblem = nameError(draft.name);
    if (nameProblem) next.name = nameProblem;
    const digits = phoneDigits(draft.phone);
    if (!digits) next.phone = "Informe o telefone com DDD, de 10 a 15 dígitos.";
    if (draft.email.trim() && !/^[^\s@]+@[^\s@]+$/.test(draft.email.trim())) next.email = "Confira o e-mail, ou deixe em branco.";
    if ([...draft.note.trim()].length > 500 || /[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(draft.note)) next.note = "Use até 500 caracteres, em uma única linha.";
    const limit = parseOptionalAmount(draft.limit);
    if ("error" in limit) next.limit = limit.error;
    setErrors(next);
    const first = (["name", "phone", "email", "note", "limit"] as const).find(field => next[field]);
    if (first) { document.getElementById(`customer-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true);
    try {
      const body = { requestId: customer ? null : requestId, name: draft.name.trim(), phone: digits, email: draft.email.trim() || null, note: draft.note.trim() || null,
        creditLimit: draft.limit.trim() && "amount" in limit ? limit.amount : null };
      const path = `/api/tenants/${encodeURIComponent(tenantId)}/customers${customer ? `/${encodeURIComponent(customer.id)}` : ""}`;
      const response = await apiRequest(path, { method: customer ? "PUT" : "POST", body });
      if (response.ok) { setRequestId(crypto.randomUUID()); onSaved(await response.json()); return; }
      const answer = await response.json().catch(() => null) as { message?: string; code?: string } | null;
      if (answer?.code === "REQUEST_CONFLICT") setRequestId(crypto.randomUUID());
      if (answer?.code === "EMAIL_TAKEN") { setErrors({ email: answer.message }); document.getElementById("customer-email")?.focus(); return; }
      setErrors({ form: response.status === 400 ? "Confira os dados do cliente." : answer?.message ?? requestError(response.status) });
    } catch {
      setErrors({ form: "Não foi possível salvar o cliente. Tente de novo." });
    } finally { submitting.current = false; setBusy(false); }
  }

  const field = (name: keyof Draft, label: string, help: string, props: { inputMode?: "decimal" | "tel" | "email"; maxLength: number; type?: string }) => <div className="form-field">
    <label htmlFor={`customer-${name}`}>{label}</label>
    {help ? <p id={`customer-${name}-help`} className="field-help">{help}</p> : null}
    <input id={`customer-${name}`} type={props.type ?? "text"} disabled={busy} value={draft[name]} inputMode={props.inputMode} maxLength={props.maxLength} autoComplete="off"
      onChange={event => { setDraft({ ...draft, [name]: event.target.value }); setErrors(current => ({ ...current, [name]: undefined })); }}
      aria-invalid={errors[name] ? true : undefined} aria-describedby={[help ? `customer-${name}-help` : null, errors[name] ? `customer-${name}-error` : null].filter(Boolean).join(" ") || undefined} />
    {errors[name] ? <p id={`customer-${name}-error`} className="field-error">{errors[name]}</p> : null}
  </div>;

  return <FormSheet open={open} onOpenChange={next => { if (!next) onClose(); }} busy={busy} feedbackRef={feedbackRef} titleId="customer-form-title" title={customer ? "Editar cliente" : "Novo cliente"}>
    {errors.form ? <p ref={feedbackRef} tabIndex={-1} role="alert" className="feedback feedback-error">{errors.form}</p> : null}
    <form onSubmit={event => void save(event)} noValidate aria-labelledby="customer-form-title">
      {field("name", "Nome", "", { maxLength: 240 })}
      {field("phone", "Telefone com DDD", "", { inputMode: "tel", maxLength: 24 })}
      {field("email", "E-mail (opcional)", "Um e-mail só pode ser de um cliente da loja.", { inputMode: "email", maxLength: 320, type: "email" })}
      {field("note", "Observação (opcional)", "Até 500 caracteres. Não escreva dados sensíveis, como saúde ou documentos.", { maxLength: 1000 })}
      {field("limit", "Limite de crédito (R$, opcional)", "Em branco: sem limite. A venda no crediário é recusada se passar do limite, contando o que o cliente já deve.", { inputMode: "decimal", maxLength: 24 })}
      <div className="form-actions"><button type="submit" disabled={busy}>{busy ? "Salvando…" : customer ? "Salvar cliente" : "Cadastrar cliente"}</button></div>
    </form>
  </FormSheet>;
}
