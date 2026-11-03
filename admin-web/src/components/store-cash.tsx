"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { type CashOverview, type CashSession, type Drawer, MOVE_TITLES, MOVEMENT_LABELS, type MoveType, differenceText, formatDateTime, otherAccounts, parseCount } from "@/lib/cash";
import { CATEGORIES, CATEGORY_LABELS, type ExpenseCategory, lineText, parseAmount } from "@/lib/expenses";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";

type Action = { drawer: Drawer; kind: "OPEN" | "CLOSE" | MoveType } | { drawer: null; kind: "REOPEN"; session: CashSession };
type Fields = { amount: string; reason: string; accountId: string; category: ExpenseCategory | "" };
const EMPTY: Fields = { amount: "", reason: "", accountId: "", category: "" };

// SC-052 (ADR-0019 C5): the store's cash register, for the OWNER and the employees. Closing is
// blind: nobody sees the expected amount while counting, and employees never do. Only the OWNER
// sees the history with expected amounts and differences, and reopens a closed session.
export default function StoreCash({ tenantId }: { tenantId: string }) {
  const [data, setData] = useState<CashOverview | null>(null);
  const [history, setHistory] = useState<CashSession[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [action, setAction] = useState<Action | null>(null);
  const [fields, setFields] = useState<Fields>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<keyof Fields, string>>>({});
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const formTitleRef = useRef<HTMLHeadingElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/cash`;
  const owner = data?.role === "OWNER";
  useUnsavedChanges(Boolean(action) && JSON.stringify(fields) !== JSON.stringify(EMPTY) || busy);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(api, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: CashOverview = await response.json();
      let sessions: CashSession[] | null = null;
      if (current.role === "OWNER") {
        const past = await apiRequest(`${api}/sessions`, { signal: controller.signal });
        if (!past.ok) throw new Error();
        sessions = await past.json();
      }
      if (controller.signal.aborted) return;
      setData(current); setHistory(sessions); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => { if (action) formTitleRef.current?.focus(); }, [action]);

  function start(next: Action, button: HTMLButtonElement) {
    trigger.current = button; setFeedback(null); setErrors({}); setFields(EMPTY); setRequestId(crypto.randomUUID()); setAction(next);
  }
  function set<K extends keyof Fields>(name: K, value: Fields[K]) {
    setFields(current => ({ ...current, [name]: value })); setErrors(current => ({ ...current, [name]: undefined }));
  }
  function cancel() { setAction(null); trigger.current?.focus(); }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !action) return;
    const next: Partial<Record<keyof Fields, string>> = {};
    let path = ""; let body: Record<string, unknown> = {};
    if (action.kind === "REOPEN") {
      const reason = lineText(fields.reason, 300, "o motivo");
      if ("error" in reason) next.reason = reason.error;
      else { path = `${api}/sessions/${encodeURIComponent(action.session.id)}/reopen`; body = { reason: reason.text }; }
    } else if (action.kind === "OPEN" || action.kind === "CLOSE") {
      const amount = parseCount(fields.amount);
      if ("error" in amount) next.amount = amount.error;
      else if (action.kind === "OPEN") { path = `${api}/sessions`; body = { requestId, accountId: action.drawer.id, amount: amount.amount }; }
      else { path = `${api}/sessions/${encodeURIComponent(action.drawer.session!.id)}/close`; body = { requestId, counted: amount.amount }; }
    } else {
      const amount = parseAmount(fields.amount);
      if ("error" in amount) next.amount = amount.error;
      const reason = lineText(fields.reason, 200, action.kind === "EXPENSE" ? "a descrição" : "o motivo");
      if ("error" in reason) next.reason = reason.error;
      if (action.kind === "EXPENSE" && !fields.category) next.category = "Escolha a categoria.";
      if ("amount" in amount && "text" in reason) {
        path = `${api}/sessions/${encodeURIComponent(action.drawer.session!.id)}/movements`;
        body = { requestId, type: action.kind, amount: amount.amount, reason: reason.text, accountId: action.kind === "EXPENSE" ? null : fields.accountId || null, category: action.kind === "EXPENSE" ? fields.category : null };
      }
    }
    setErrors(next);
    const first = (["amount", "reason", "category"] as const).find(field => next[field]);
    if (first) { document.getElementById(`cash-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    const done = action;
    try {
      const response = await apiRequest(path, { method: "POST", body });
      if (response.ok) {
        const session: CashSession = await response.json();
        setAction(null); setAttempt(current => current + 1);
        if (done.kind === "CLOSE") setFeedback({ text: owner && session.difference ? `Caixa fechado. Contado: ${formatBRL(session.countedAmount!)}. ${differenceText(session.difference, formatBRL)}.` : `Caixa fechado. Contagem registrada: ${formatBRL(session.countedAmount!)}.`, error: false });
        else if (done.kind === "OPEN") setFeedback({ text: `Caixa “${done.drawer.name}” aberto com ${formatBRL(session.openingAmount)}.`, error: false });
        else if (done.kind === "REOPEN") setFeedback({ text: `Caixa “${session.accountName}” reaberto.`, error: false });
        else setFeedback({ text: { DROP: "Sangria registrada.", TOP_UP: "Suprimento registrado.", EXPENSE: "Despesa registrada e paga com o dinheiro da gaveta." }[done.kind], error: false });
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente para continuar.", error: true }); return; }
      if (response.status === 400) { setFeedback({ text: "Confira os dados: valor com até duas casas decimais e motivo em uma linha, com 3 a 200 caracteres.", error: true }); return; }
      if (response.status === 409) {
        const answer = await response.json().catch(() => null) as { message?: string } | null;
        setAction(null); setAttempt(current => current + 1);
        setFeedback({ text: answer?.message ?? "O caixa mudou. A página foi atualizada.", error: true }); return;
      }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar. Confira o caixa antes de tentar de novo: repetir a mesma tentativa não duplica o lançamento.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  const field = (name: keyof Fields, label: string, control: React.ReactNode, help?: string) => <div className="form-field">
    <label htmlFor={`cash-${name}`}>{label}</label>
    {help ? <p id={`cash-${name}-help`} className="field-help">{help}</p> : null}
    {control}
    {errors[name] ? <p id={`cash-${name}-error`} className="field-error">{errors[name]}</p> : null}
  </div>;
  const described = (name: keyof Fields, help?: boolean) => [help ? `cash-${name}-help` : null, errors[name] ? `cash-${name}-error` : null].filter(Boolean).join(" ") || undefined;
  const amountInput = <input id="cash-amount" disabled={busy} value={fields.amount} inputMode="decimal" autoComplete="off" maxLength={24} onChange={event => set("amount", event.target.value)} aria-invalid={errors.amount ? true : undefined} aria-describedby={described("amount", true)} />;
  const reopenable = (session: CashSession) => session.status === "CLOSED" && !history?.some(item => item.reopenedFrom === session.id) && !data?.drawers.some(drawer => drawer.id === session.accountId && drawer.session);

  function form() {
    if (!action) return null;
    const title = action.kind === "OPEN" ? `Abrir “${action.drawer.name}”` : action.kind === "CLOSE" ? `Fechar “${action.drawer.name}”` : action.kind === "REOPEN" ? `Reabrir “${action.session.accountName}”` : `${MOVE_TITLES[action.kind]} em “${action.drawer.name}”`;
    const accounts = action.drawer && data ? otherAccounts(data.destinations, action.drawer.id) : [];
    return <form className="side-card" onSubmit={event => void submit(event)} noValidate aria-labelledby="cash-form-title">
      <h2 id="cash-form-title" ref={formTitleRef} tabIndex={-1}>{title}</h2>
      {action.kind === "OPEN" ? field("amount", "Fundo de troco (R$)", amountInput, "Conte o dinheiro que está na gaveta agora. Pode ser zero.") : null}
      {action.kind === "CLOSE" ? field("amount", "Dinheiro contado na gaveta (R$)", amountInput, "Conte todo o dinheiro da gaveta antes de informar. O valor esperado não é mostrado.") : null}
      {action.kind === "DROP" || action.kind === "TOP_UP" || action.kind === "EXPENSE" ? <>
        {field("amount", "Valor (R$)", amountInput, action.kind === "DROP" ? "Dinheiro que sai da gaveta." : action.kind === "TOP_UP" ? "Dinheiro que entra na gaveta." : "Pago com o dinheiro da gaveta.")}
        {field("reason", action.kind === "EXPENSE" ? "Descrição" : "Motivo", <input id="cash-reason" disabled={busy} value={fields.reason} maxLength={400} autoComplete="off" onChange={event => set("reason", event.target.value)} aria-invalid={errors.reason ? true : undefined} aria-describedby={described("reason")} />)}
        {action.kind === "EXPENSE" ? field("category", "Categoria", <select id="cash-category" disabled={busy} value={fields.category} onChange={event => set("category", event.target.value as ExpenseCategory)} aria-invalid={errors.category ? true : undefined} aria-describedby={described("category")}>
          <option value="">Escolha</option>{CATEGORIES.map(value => <option key={value} value={value}>{CATEGORY_LABELS[value]}</option>)}</select>) : field("accountId", action.kind === "DROP" ? "Para onde vai o dinheiro" : "De onde vem o dinheiro", <select id="cash-accountId" disabled={busy} value={fields.accountId} onChange={event => set("accountId", event.target.value)} aria-describedby={described("accountId", true)}>
          <option value="">{action.kind === "DROP" ? "Retirada do dono (sai da loja)" : "Aporte do dono (entra na loja)"}</option>{accounts.map(account => <option key={account.id} value={account.id}>{account.name}</option>)}</select>,
          "Escolha uma conta da loja para registrar uma transferência. Retirada e aporte do dono não entram como despesa nem receita.")}
      </> : null}
      {action.kind === "REOPEN" ? field("reason", "Motivo", <input id="cash-reason" disabled={busy} value={fields.reason} maxLength={600} autoComplete="off" onChange={event => set("reason", event.target.value)} aria-invalid={errors.reason ? true : undefined} aria-describedby={described("reason", true)} />, "Fica registrado no histórico. De 3 a 300 caracteres.") : null}
      <div className="form-actions">
        <button type="submit" disabled={busy}>{busy ? "Registrando…" : action.kind === "OPEN" ? "Abrir caixa" : action.kind === "CLOSE" ? "Fechar caixa" : action.kind === "REOPEN" ? "Reabrir caixa" : "Registrar"}</button>
        <button type="button" className="button-secondary" disabled={busy} onClick={cancel}>Voltar</button>
      </div>
    </form>;
  }

  return <section className="page" aria-labelledby="cash-title">
    <PageHeader id="cash-title" title="Caixa" description="Abertura, sangria, suprimento, despesas pagas com dinheiro da gaveta e fechamento. Nada é apagado." />
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback ${feedback.error ? "feedback-error" : "feedback-success"}`}>{feedback.text}</p> : null}
    {state === "loading" ? <Loading context="caixa" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar o caixa.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="wallet" title="Não foi possível consultar o caixa" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar o caixa novamente">Confira a conexão e tente novamente. Os lançamentos continuam registrados.</PageState> : null}
    {state === "ready" && data ? <>
      {data.drawers.map(drawer => <section key={drawer.id} className="side-card" aria-labelledby={`drawer-${drawer.id}`}>
        <h2 id={`drawer-${drawer.id}`}>{drawer.name} <StatusBadge tone={drawer.session ? "positive" : "neutral"}>{drawer.session ? "Aberto" : "Fechado"}</StatusBadge></h2>
        {drawer.session ? <>
          <p>Aberto por {drawer.session.openedBy} em {formatDateTime(drawer.session.openedAt)}, com fundo de troco de {formatBRL(drawer.session.openingAmount)}.{drawer.session.reopenReason ? ` Reaberto: ${drawer.session.reopenReason}.` : ""}</p>
          {drawer.session.movements?.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
            <caption>Movimentos deste caixa</caption>
            <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Movimento</th><th role="columnheader" scope="col">Quando</th><th role="columnheader" scope="col" className="numeric">Valor</th></tr></thead>
            <tbody role="rowgroup">{drawer.session.movements.map(item => <tr role="row" key={item.id}>
              <th role="rowheader" scope="row">{MOVEMENT_LABELS[item.kind]}{item.description ? <span className="cell-hint">{item.description}</span> : null}<span className="cell-hint">Por {item.recordedBy}</span></th>
              <td role="cell" data-label="Quando">{formatDateTime(item.createdAt)}</td>
              <td role="cell" data-label="Valor" className="numeric">{formatBRL(item.amount)}</td>
            </tr>)}</tbody>
          </table></div> : <p className="field-help">Nenhum movimento neste caixa ainda.</p>}
          <div className="form-actions">
            {(["DROP", "TOP_UP", "EXPENSE"] as const).map(kind => <button key={kind} type="button" className="button-secondary" disabled={busy} aria-label={`${MOVE_TITLES[kind]} em ${drawer.name}`} onClick={event => start({ drawer, kind }, event.currentTarget)}>{MOVE_TITLES[kind]}</button>)}
            <button type="button" disabled={busy} aria-label={`Fechar ${drawer.name}`} onClick={event => start({ drawer, kind: "CLOSE" }, event.currentTarget)}>Fechar caixa</button>
          </div>
        </> : <div className="form-actions"><button type="button" disabled={busy} aria-label={`Abrir ${drawer.name}`} onClick={event => start({ drawer, kind: "OPEN" }, event.currentTarget)}>Abrir caixa</button></div>}
      </section>)}
      {form()}
      {owner && history ? <section aria-labelledby="cash-history-title">
        <h2 id="cash-history-title">Histórico de caixas</h2>
        {history.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
          <caption>Últimos caixas abertos, com o valor esperado e a diferença no fechamento</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Caixa</th><th role="columnheader" scope="col">Fechamento</th><th role="columnheader" scope="col" className="numeric">Contado</th><th role="columnheader" scope="col" className="numeric">Esperado</th><th role="columnheader" scope="col">Diferença</th><th role="columnheader" scope="col">Ações</th></tr></thead>
          <tbody role="rowgroup">{history.map(session => <tr role="row" key={session.id}>
            <th role="rowheader" scope="row">{session.accountName}<span className="cell-hint">Aberto por {session.openedBy} em {formatDateTime(session.openedAt)}</span>{session.reopenReason ? <span className="cell-hint">Reaberto: {session.reopenReason}</span> : null}</th>
            <td role="cell" data-label="Fechamento">{session.closedAt ? <>{formatDateTime(session.closedAt)}<span className="cell-hint">Por {session.closedBy}</span></> : "Em aberto"}</td>
            <td role="cell" data-label="Contado" className="numeric">{session.countedAmount ? formatBRL(session.countedAmount) : "—"}</td>
            <td role="cell" data-label="Esperado" className="numeric">{session.expectedAmount ? formatBRL(session.expectedAmount) : "—"}</td>
            <td role="cell" data-label="Diferença">{session.difference ? <StatusBadge tone={session.difference.startsWith("-") ? "danger" : "neutral"}>{differenceText(session.difference, formatBRL)}</StatusBadge> : "—"}</td>
            <td role="cell" data-label="Ações">{reopenable(session) ? <button type="button" className="button-secondary" disabled={busy} aria-label={`Reabrir ${session.accountName} fechado em ${formatDateTime(session.closedAt!)}`} onClick={event => start({ drawer: null, kind: "REOPEN", session }, event.currentTarget)}>Reabrir</button> : null}</td>
          </tr>)}</tbody>
        </table></div> : <p className="field-help">Nenhum caixa aberto até agora.</p>}
        <p className="field-help">As diferenças entram no relatório financeiro como “Diferenças de caixa”, fora da margem de contribuição. <Link href={`/lojas/${encodeURIComponent(tenantId)}/financeiro`}>Ver financeiro</Link>.</p>
      </section> : null}
    </> : null}
  </section>;
}
