"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { ArrowRightLeft, Banknote, Landmark, Wallet } from "lucide-react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { accountRefusal, lineError, manualAccounts, parseAccounts, parseSignedAmount, todayInStore, TYPE_LABELS, type Account } from "@/lib/accounts";
import { formatBRL } from "@/lib/catalog";
import PageHeader from "@/components/page-header";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";

const ICON = { CASH: Banknote, BANK: Landmark, PAYMENT_PROVIDER: Wallet } as const;

// SC-050 (ADR-0019 C2): where the store's money is. Balances are the sum of each account's
// movements, computed by the backend; this page only shows them and sends new accounts and
// transfers, each with a request id so a retry never records twice.
export default function StoreAccounts({ tenantId }: { tenantId: string }) {
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/finance`;
  const [accounts, setAccounts] = useState<Account[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const [account, setAccount] = useState({ type: "BANK", name: "", request: "" });
  const [transfer, setTransfer] = useState({ from: "", to: "", amount: "", day: "", note: "", request: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const feedbackRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`${api}/accounts`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 404) { setState("missing"); return; }
      const parsed = response.ok ? parseAccounts(await response.json()) : null;
      if (!parsed) throw new Error();
      setAccounts(parsed); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  /** Sends one write; on a conflict the caller gets "conflict" and must use a new request id. */
  async function send(path: string, body: object, success: string): Promise<boolean | "conflict"> {
    setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(path, { method: "POST", body });
      if (!response.ok) {
        const json: unknown = await response.json().catch(() => null);
        setFeedback({ text: accountRefusal(response.status, json) ?? requestError(response.status), error: true });
        return response.status === 409 ? "conflict" : false;
      }
      setFeedback({ text: success, error: false }); setAttempt(current => current + 1);
      return true;
    } catch {
      setFeedback({ text: "Não foi possível falar com o servidor. Tente novamente.", error: true });
      return false;
    } finally { setBusy(false); }
  }

  async function createAccount(event: FormEvent) {
    event.preventDefault();
    const problem = lineError(account.name, "Nome", 60);
    setErrors(problem ? { name: problem } : {});
    if (problem) { document.getElementById("account-name")?.focus(); return; }
    const request = account.request || crypto.randomUUID();
    setAccount(current => ({ ...current, request }));
    const sent = await send(`${api}/accounts`, { requestId: request, type: account.type, name: account.name.trim() }, `Conta “${account.name.trim()}” criada.`);
    if (sent === true) setAccount({ type: "BANK", name: "", request: "" });
    else if (sent === "conflict") setAccount(current => ({ ...current, request: "" }));
  }

  async function sendTransfer(event: FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!transfer.from) next.from = "Escolha a conta de origem.";
    if (!transfer.to) next.to = "Escolha a conta de destino.";
    else if (transfer.to === transfer.from) next.to = "Escolha uma conta diferente da origem.";
    const amount = parseSignedAmount(transfer.amount, false);
    if ("error" in amount) next.amount = amount.error;
    const day = transfer.day || todayInStore(new Date());
    if (day > todayInStore(new Date())) next.day = "A data não pode ser no futuro.";
    if (transfer.note.trim() && lineError(transfer.note, "Observação", 200)) next.note = lineError(transfer.note, "Observação", 200)!;
    setErrors(next);
    const first = ["from", "to", "amount", "day", "note"].find(key => next[key]);
    if (first) { document.getElementById(`transfer-${first}`)?.focus(); return; }
    const request = transfer.request || crypto.randomUUID();
    setTransfer(current => ({ ...current, request }));
    const sent = await send(`${api}/transfers`, { requestId: request, fromAccountId: transfer.from, toAccountId: transfer.to, amount: "amount" in amount ? amount.amount : "", occurredOn: day, note: transfer.note.trim() || null }, "Transferência registrada.");
    if (sent === true) setTransfer({ from: "", to: "", amount: "", day: "", note: "", request: "" });
    else if (sent === "conflict") setTransfer(current => ({ ...current, request: "" }));
  }

  const field = (id: string, key: string) => ({ id, "aria-invalid": errors[key] ? true : undefined, "aria-describedby": errors[key] ? `${id}-error` : undefined });
  const error = (id: string, key: string) => errors[key] ? <p id={`${id}-error`} className="field-error">{errors[key]}</p> : null;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

  return <section className="page store-categories" aria-labelledby="accounts-title" aria-busy={state === "loading" || busy}>
    <PageHeader id="accounts-title" title="Contas"
      description="Onde está o dinheiro da loja: gaveta, banco e Mercado Pago. O saldo é a soma dos movimentos de cada conta; nada é apagado, uma correção é um novo ajuste." />
    {state === "loading" ? <Loading context="as contas" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para ver as contas.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Só o dono da loja vê as contas.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="box" title="Não foi possível consultar as contas" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {state === "ready" && accounts ? <>
      <ul className="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 xl:grid-cols-3">
        {accounts.map(item => { const Icon = ICON[item.type]; return <li key={item.id} className="flex items-start gap-4 rounded-lg border border-border bg-card p-5 shadow-card">
          <span className="inline-flex size-11 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden="true"><Icon className="size-5" /></span>
          <div className="min-w-0 flex-1">
            <h2 className="m-0 text-base font-semibold"><Link href={`${storePath}/contas/${encodeURIComponent(item.id)}`}>{item.name}</Link></h2>
            <p className="m-0 text-sm text-muted-foreground">{TYPE_LABELS[item.type]}</p>
            <p className={`m-0 mt-1 text-2xl font-bold tabular-nums ${item.balance.startsWith("-") ? "text-destructive" : "text-foreground"}`}>{formatBRL(item.balance)}</p>
          </div>
        </li>; })}
      </ul>
      {accounts.some(item => item.type === "PAYMENT_PROVIDER") ? null : <p className="field-help">A conta do Mercado Pago aparece aqui quando você conectar o Mercado Pago em <Link href={`${storePath}/pagamentos`}>Pagamentos</Link>.</p>}

      <form className="side-card" onSubmit={event => void sendTransfer(event)} noValidate aria-labelledby="transfer-title">
        <h2 id="transfer-title" className="flex items-center gap-2"><ArrowRightLeft className="size-5" aria-hidden="true" />Transferir entre contas</h2>
        <p className="field-help">Por exemplo, o depósito do dinheiro da gaveta no banco. Os dois lados são registrados juntos. A conta do Mercado Pago não entra aqui: ela segue os pagamentos do próprio Mercado Pago.</p>
        <div className="form-field"><label htmlFor="transfer-from">De</label>
          <select {...field("transfer-from", "from")} value={transfer.from} disabled={busy} onChange={event => setTransfer(current => ({ ...current, from: event.target.value }))}>
            <option value="">Escolha a conta</option>{manualAccounts(accounts).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>{error("transfer-from", "from")}</div>
        <div className="form-field"><label htmlFor="transfer-to">Para</label>
          <select {...field("transfer-to", "to")} value={transfer.to} disabled={busy} onChange={event => setTransfer(current => ({ ...current, to: event.target.value }))}>
            <option value="">Escolha a conta</option>{manualAccounts(accounts).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>{error("transfer-to", "to")}</div>
        <div className="form-field"><label htmlFor="transfer-amount">Valor (R$)</label>
          <input {...field("transfer-amount", "amount")} inputMode="decimal" autoComplete="off" value={transfer.amount} disabled={busy} onChange={event => setTransfer(current => ({ ...current, amount: event.target.value }))} />{error("transfer-amount", "amount")}</div>
        <div className="form-field"><label htmlFor="transfer-day">Data</label>
          <input {...field("transfer-day", "day")} type="date" max={todayInStore(new Date())} value={transfer.day || todayInStore(new Date())} disabled={busy} onChange={event => setTransfer(current => ({ ...current, day: event.target.value }))} />{error("transfer-day", "day")}</div>
        <div className="form-field"><label htmlFor="transfer-note">Observação (opcional)</label>
          <input {...field("transfer-note", "note")} maxLength={200} autoComplete="off" value={transfer.note} disabled={busy} onChange={event => setTransfer(current => ({ ...current, note: event.target.value }))} />{error("transfer-note", "note")}</div>
        <button type="submit" disabled={busy}>{busy ? "Registrando…" : "Registrar transferência"}</button>
      </form>

      <form className="side-card" onSubmit={event => void createAccount(event)} noValidate aria-labelledby="account-create-title">
        <h2 id="account-create-title">Nova conta</h2>
        <div className="form-field"><label htmlFor="account-type">Tipo</label>
          <select id="account-type" value={account.type} disabled={busy} onChange={event => setAccount(current => ({ ...current, type: event.target.value }))}>
            <option value="BANK">Banco</option><option value="CASH">Dinheiro (outra gaveta)</option>
          </select></div>
        <div className="form-field"><label htmlFor="account-name">Nome</label>
          <p id="account-name-help" className="field-help">Por exemplo, “Banco Inter” ou “Gaveta 2”. Até 20 contas por loja.</p>
          <input id="account-name" maxLength={60} autoComplete="off" value={account.name} disabled={busy} aria-invalid={errors.name ? true : undefined}
            aria-describedby={`account-name-help${errors.name ? " account-name-error" : ""}`} onChange={event => setAccount(current => ({ ...current, name: event.target.value }))} />
          {error("account-name", "name")}</div>
        <button type="submit" disabled={busy}>{busy ? "Criando…" : "Criar conta"}</button>
      </form>
    </> : null}
  </section>;
}
