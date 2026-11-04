"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { accountRefusal, KIND_LABELS, lineError, parseAccounts, parseSignedAmount, parseStatement, todayInStore, TYPE_LABELS, type Statement } from "@/lib/accounts";
import { formatBRL } from "@/lib/catalog";
import { formatDay } from "@/lib/expenses";
import PageHeader from "@/components/page-header";
import ListPager from "@/components/list-pager";
import { AddButton, FormSheet } from "@/components/form-sheet";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";

// SC-050 (ADR-0019 C2): one account's statement for a period, with the balance before and after
// and the balance right after each movement, all computed by the backend. Adjustments (the opening
// balance included) are signed and need a reason; nothing is ever edited or deleted.
export default function AccountStatement({ tenantId, accountId }: { tenantId: string; accountId: string }) {
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/finance`;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  const [statement, setStatement] = useState<Statement | null>(null);
  const [names, setNames] = useState<Record<string, string>>({});
  const [period, setPeriod] = useState({ from: "", to: "" });
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const [form, setForm] = useState({ amount: "", day: "", reason: "", request: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [adjusting, setAdjusting] = useState(false);
  const [before, setBefore] = useState<string | null>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams();
    if (period.from && period.to) { params.set("from", period.from); params.set("to", period.to); }
    if (before) params.set("before", before);
    const query = params.size ? `?${params}` : "";
    Promise.all([
      apiRequest(`${api}/accounts/${encodeURIComponent(accountId)}/statement${query}`, { signal: controller.signal }),
      apiRequest(`${api}/accounts`, { signal: controller.signal }),
    ]).then(async ([response, list]) => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 404) { setState("missing"); return; }
      if (response.status === 400) { setFeedback({ text: "Escolha um período de até um ano, com o início antes do fim.", error: true }); return; }
      const parsed = response.ok ? parseStatement(await response.json()) : null;
      if (!parsed) throw new Error();
      const accounts = list.ok ? parseAccounts(await list.json()) ?? [] : [];
      setNames(Object.fromEntries(accounts.map(item => [item.id, item.name])));
      setStatement(parsed); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, accountId, period, attempt, before]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  async function adjust(event: FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    const amount = parseSignedAmount(form.amount, true);
    if ("error" in amount) next.amount = amount.error;
    const today = todayInStore(new Date());
    const day = form.day || today;
    if (day > today) next.day = "A data não pode ser no futuro.";
    const reason = lineError(form.reason, "Motivo", 200);
    if (reason) next.reason = reason;
    setErrors(next);
    const first = ["amount", "day", "reason"].find(key => next[key]);
    if (first) { document.getElementById(`adjust-${first}`)?.focus(); return; }
    const request = form.request || crypto.randomUUID();
    setForm(current => ({ ...current, request }));
    setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`${api}/accounts/${encodeURIComponent(accountId)}/adjustments`, { method: "POST", body: { requestId: request, amount: "amount" in amount ? amount.amount : "", occurredOn: day, reason: form.reason.trim() } });
      if (!response.ok) {
        const json: unknown = await response.json().catch(() => null);
        setFeedback({ text: accountRefusal(response.status, json) ?? requestError(response.status), error: true });
        // A conflict means this request id already recorded something else: the next try is new.
        if (response.status === 409) setForm(current => ({ ...current, request: "" }));
        return;
      }
      setForm({ amount: "", day: "", reason: "", request: "" }); setAdjusting(false); setBefore(null);
      setFeedback({ text: "Ajuste registrado.", error: false }); setAttempt(current => current + 1);
    } catch { setFeedback({ text: "Não foi possível falar com o servidor. Tente novamente.", error: true }); }
    finally { setBusy(false); }
  }

  function choosePeriod(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const from = String(data.get("from") ?? ""), to = String(data.get("to") ?? "");
    if (!from || !to) { setFeedback({ text: "Preencha as duas datas do período.", error: true }); return; }
    setFeedback(null);
    setPeriod({ from, to }); setBefore(null);
  }

  const field = (key: string) => ({ id: `adjust-${key}`, "aria-invalid": errors[key] ? true : undefined, "aria-describedby": errors[key] ? `adjust-${key}-error` : undefined });
  const error = (key: string) => errors[key] ? <p id={`adjust-${key}-error`} className="field-error">{errors[key]}</p> : null;
  const account = statement?.account;
  const feedbackNode = feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null;

  return <section className="page store-categories" aria-labelledby="statement-title" aria-busy={state === "loading" || busy}>
    <PageHeader id="statement-title" title={account ? account.name : "Extrato"} breadcrumbs={[{ href: `${storePath}/contas`, label: "Contas" }]}
      description={account ? `${TYPE_LABELS[account.type]} · saldo atual ${formatBRL(account.balance)}` : undefined}
      action={state === "ready" && statement && statement.account.systemKey !== "MERCADO_PAGO" ? <AddButton onClick={() => { setFeedback(null); setErrors({}); setAdjusting(true); }}>Novo ajuste</AddButton> : undefined} />
    {state === "loading" ? <Loading context="o extrato" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para ver o extrato.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Conta indisponível" href={`${storePath}/contas`} actionLabel="Voltar para as contas">Escolha uma conta desta loja.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="box" title="Não foi possível consultar o extrato" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {adjusting ? null : feedbackNode}
    {state === "ready" && statement ? <>
      <form className="flex flex-wrap items-end gap-4" onSubmit={choosePeriod} aria-label="Período do extrato">
        <div className="form-field"><label htmlFor="period-from">De</label><input id="period-from" name="from" type="date" defaultValue={statement.from} /></div>
        <div className="form-field"><label htmlFor="period-to">Até</label><input id="period-to" name="to" type="date" defaultValue={statement.to} /></div>
        <button type="submit" className="button-secondary">Ver período</button>
      </form>
      <dl className="order-facts">
        <div><dt>Saldo em {formatDay(statement.from)}, antes dos movimentos</dt><dd className="tabular-nums">{formatBRL(statement.openingBalance)}</dd></div>
        <div><dt>Saldo ao fim de {formatDay(statement.to)}</dt><dd className="tabular-nums">{formatBRL(statement.closingBalance)}</dd></div>
      </dl>
      {statement.movements.length === 0 ? <PageState icon="box" title="Nenhum movimento no período">Escolha outro período ou registre um ajuste.</PageState> : <div className="table-scroll"><table>
        <caption className="visually-hidden">Movimentos de {statement.account.name}, do mais recente ao mais antigo</caption>
        <thead><tr><th scope="col">Data</th><th scope="col">Movimento</th><th scope="col" className="numeric">Valor</th><th scope="col" className="numeric">Saldo depois</th></tr></thead>
        <tbody>{statement.movements.map(item => <tr key={item.id}>
          <td>{formatDay(item.occurredOn)}</td>
          <td>{KIND_LABELS[item.kind]}{item.counterpartAccountId ? ` ${item.kind === "TRANSFER_OUT" ? "para" : "de"} ${names[item.counterpartAccountId] ?? "outra conta"}` : ""}{item.description ? <span className="cell-hint">{item.description}</span> : null}</td>
          <td className={`numeric tabular-nums ${item.amount.startsWith("-") ? "text-destructive" : ""}`}>{formatBRL(item.amount)}</td>
          <td className="numeric tabular-nums">{formatBRL(item.balanceAfter)}</td>
        </tr>)}</tbody>
      </table></div>}
      <ListPager label="Páginas do extrato" disabled={busy}
        first={before ? { text: "Voltar aos mais recentes", onClick: () => setBefore(null) } : null}
        next={statement.nextCursor ? { text: "Ver movimentos mais antigos", onClick: () => setBefore(statement.nextCursor) } : null} />

      {statement.account.systemKey === "MERCADO_PAGO" ? <p className="field-help">Esta conta segue os pagamentos do próprio Mercado Pago e não aceita ajustes nem transferências manuais.</p> :
      <FormSheet open={adjusting} onOpenChange={setAdjusting} titleId="adjust-title" title="Registrar ajuste" busy={busy} feedbackRef={feedbackRef}>{feedbackNode}
      <form onSubmit={event => void adjust(event)} noValidate aria-labelledby="adjust-title">
        <p className="field-help">Use para o saldo inicial da conta ou para corrigir uma diferença. Valor negativo diminui o saldo (por exemplo, -20,00).</p>
        <div className="form-field"><label htmlFor="adjust-amount">Valor (R$)</label>
          <input {...field("amount")} inputMode="decimal" autoComplete="off" value={form.amount} disabled={busy} onChange={event => setForm(current => ({ ...current, amount: event.target.value }))} />{error("amount")}</div>
        <div className="form-field"><label htmlFor="adjust-day">Data</label>
          <input {...field("day")} type="date" max={todayInStore(new Date())} value={form.day || todayInStore(new Date())} disabled={busy} onChange={event => setForm(current => ({ ...current, day: event.target.value }))} />{error("day")}</div>
        <div className="form-field"><label htmlFor="adjust-reason">Motivo</label>
          <input {...field("reason")} maxLength={200} autoComplete="off" value={form.reason} disabled={busy} onChange={event => setForm(current => ({ ...current, reason: event.target.value }))} />{error("reason")}</div>
        <button type="submit" disabled={busy}>{busy ? "Registrando…" : "Registrar ajuste"}</button>
      </form>
      </FormSheet>}
    </> : null}
  </section>;
}
