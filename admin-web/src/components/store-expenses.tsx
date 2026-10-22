"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { CATEGORIES, CATEGORY_LABELS, KIND_LABELS, type Expense, type ExpenseCategory, type ExpenseKind, type ExpenseMonth, formatDay, lineText, monthLabel, parseAmount, shiftMonth } from "@/lib/expenses";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";

type Draft = { category: ExpenseCategory | ""; kind: ExpenseKind | ""; description: string; amount: string; competenceDate: string; paidOn: string };
type Errors = Partial<Record<keyof Draft, string>>;
const EMPTY: Draft = { category: "", kind: "", description: "", amount: "", competenceDate: "", paidOn: "" };

// SC-020: manual expenses of the month (ADR-0015 F6). Nothing is deleted: "Corrigir" cancels the
// expense with a reason and opens the form with its values, so the new one replaces it in the
// totals while the old one stays listed with who cancelled and why.
export default function StoreExpenses({ tenantId, month }: { tenantId: string; month: string }) {
  const [data, setData] = useState<ExpenseMonth | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<Draft>({ ...EMPTY, competenceDate: `${month}-01` });
  const [errors, setErrors] = useState<Errors>({});
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [cancelling, setCancelling] = useState<{ expense: Expense; correct: boolean } | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const formTitleRef = useRef<HTMLHeadingElement>(null);
  const reasonRef = useRef<HTMLInputElement>(null);
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/expenses`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`${api}?month=${month}`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: ExpenseMonth = await response.json();
      if (controller.signal.aborted) return;
      setData(current); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, month, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => { if (cancelling) reasonRef.current?.focus(); }, [cancelling]);

  function set<K extends keyof Draft>(name: K, value: Draft[K]) {
    setDraft(current => ({ ...current, [name]: value }));
    setErrors(current => ({ ...current, [name]: undefined }));
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const next: Errors = {};
    if (!draft.category) next.category = "Escolha a categoria.";
    if (!draft.kind) next.kind = "Escolha se a despesa é fixa ou variável.";
    const description = lineText(draft.description, 200, "a descrição");
    if ("error" in description) next.description = description.error;
    const amount = parseAmount(draft.amount);
    if ("error" in amount) next.amount = amount.error;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(draft.competenceDate)) next.competenceDate = "Informe a data da despesa.";
    setErrors(next);
    const first = (Object.keys(next) as (keyof Draft)[])[0];
    if (first) { document.getElementById(`expense-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(api, { method: "POST", body: {
        requestId, category: draft.category, kind: draft.kind,
        description: "text" in description ? description.text : "", amount: "amount" in amount ? amount.amount : "",
        competenceDate: draft.competenceDate, paidOn: draft.paidOn || null } });
      if (response.ok) {
        setDraft({ ...EMPTY, competenceDate: draft.competenceDate }); setRequestId(crypto.randomUUID());
        setFeedback({ text: "Despesa registrada.", error: false }); setAttempt(current => current + 1);
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de registrar a despesa.", error: true }); return; }
      if (response.status === 409) { setRequestId(crypto.randomUUID()); setFeedback({ text: "Esta tentativa já registrou outra despesa. Confira a lista e, se precisar, registre de novo.", error: true }); setAttempt(current => current + 1); return; }
      if (response.status === 400) { setFeedback({ text: "Confira os dados: a data do pagamento não pode ser futura e o valor precisa ter até duas casas decimais.", error: true }); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar o registro. Tente novamente: a mesma tentativa será reaproveitada, sem duplicar a despesa.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  async function cancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !cancelling) return;
    const checked = lineText(reason, 300, "o motivo");
    if ("error" in checked) { setReasonError(checked.error); reasonRef.current?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    const { expense, correct } = cancelling;
    try {
      const response = await apiRequest(`${api}/${encodeURIComponent(expense.id)}/cancel`, { method: "POST", body: { reason: checked.text } });
      if (response.ok) {
        setCancelling(null); setReason(""); setAttempt(current => current + 1);
        if (correct) {
          setDraft({ category: expense.category, kind: expense.kind, description: expense.description, amount: expense.amount.replace(".", ","), competenceDate: expense.competenceDate, paidOn: expense.paidOn ?? "" });
          setRequestId(crypto.randomUUID());
          setFeedback({ text: "Despesa cancelada. Ajuste os valores abaixo e registre a despesa correta.", error: false });
          formTitleRef.current?.scrollIntoView({ block: "start" });
        } else setFeedback({ text: "Despesa cancelada. Ela continua na lista para consulta.", error: false });
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de cancelar.", error: true }); return; }
      if (response.status === 400) { setReasonError("Informe o motivo em uma linha, com 3 a 300 caracteres."); reasonRef.current?.focus(); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar o cancelamento. Consulte a lista antes de tentar de novo.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  const field = (name: keyof Draft, label: string, control: React.ReactNode, help?: string) => <div className="form-field">
    <label htmlFor={`expense-${name}`}>{label}</label>
    {help ? <p id={`expense-${name}-help`} className="field-help">{help}</p> : null}
    {control}
    {errors[name] ? <p id={`expense-${name}-error`} className="field-error">{errors[name]}</p> : null}
  </div>;
  const described = (name: keyof Draft, help?: boolean) => [help ? `expense-${name}-help` : null, errors[name] ? `expense-${name}-error` : null].filter(Boolean).join(" ") || undefined;

  return <section className="page" aria-labelledby="expenses-title">
    <PageHeader id="expenses-title" title="Despesas" description="Despesas da loja por mês de competência. Para corrigir, cancele com um motivo e registre de novo: nada é apagado." />
    <nav className="pagination" aria-label="Mês das despesas">
      <Link href={`${storePath}/despesas?mes=${shiftMonth(month, -1)}`} prefetch={false}>Mês anterior</Link>
      <strong aria-current="page">{monthLabel(month)}</strong>
      <Link href={`${storePath}/despesas?mes=${shiftMonth(month, 1)}`} prefetch={false}>Próximo mês</Link>
    </nav>
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback ${feedback.error ? "feedback-error" : "feedback-success"}`}>{feedback.text}</p> : null}
    {state === "loading" ? <p role="status">Consultando despesas…</p> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar as despesas.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Loja indisponível. Volte para suas lojas e escolha uma loja à qual você tem acesso.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar as despesas. Tente novamente.</p><button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar despesas novamente</button></> : null}
    {state === "ready" && data ? <>
      <dl className="order-facts">
        <div><dt>Total do mês</dt><dd className="numeric"><strong>{formatBRL(data.total)}</strong></dd></div>
        <div><dt>Fixas</dt><dd className="numeric">{formatBRL(data.fixed)}</dd></div>
        <div><dt>Variáveis</dt><dd className="numeric">{formatBRL(data.variable)}</dd></div>
      </dl>
      {data.expenses.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
        <caption>Despesas de {monthLabel(month)}</caption>
        <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Despesa</th><th role="columnheader" scope="col">Data</th><th role="columnheader" scope="col">Pagamento</th><th role="columnheader" scope="col" className="numeric">Valor</th><th role="columnheader" scope="col">Ações</th></tr></thead>
        <tbody role="rowgroup">{data.expenses.map(expense => <tr role="row" key={expense.id}>
          <th role="rowheader" scope="row">{expense.description}<span className="cell-hint">{CATEGORY_LABELS[expense.category]} · {KIND_LABELS[expense.kind]}</span>
            {expense.status === "CANCELLED" ? <span className="cell-hint"><StatusBadge tone="neutral">Cancelada</StatusBadge> {expense.cancelReason}</span> : null}</th>
          <td role="cell" data-label="Data">{formatDay(expense.competenceDate)}</td>
          <td role="cell" data-label="Pagamento">{expense.paidOn ? `Paga em ${formatDay(expense.paidOn)}` : "Não paga"}</td>
          <td role="cell" data-label="Valor" className="numeric">{expense.status === "CANCELLED" ? <s>{formatBRL(expense.amount)}</s> : formatBRL(expense.amount)}</td>
          <td role="cell" data-label="Ações">{expense.status === "ACTIVE" ? <div className="form-actions">
            <button type="button" className="button-secondary" disabled={busy} aria-label={`Corrigir ${expense.description}`} onClick={() => { setFeedback(null); setReason(""); setReasonError(""); setCancelling({ expense, correct: true }); }}>Corrigir</button>
            <button type="button" className="button-secondary" disabled={busy} aria-label={`Cancelar ${expense.description}`} onClick={() => { setFeedback(null); setReason(""); setReasonError(""); setCancelling({ expense, correct: false }); }}>Cancelar</button>
          </div> : null}</td>
        </tr>)}</tbody>
      </table></div> : <p>Nenhuma despesa registrada em {monthLabel(month)}.</p>}
      {cancelling ? <form className="side-card" onSubmit={event => void cancel(event)} noValidate aria-labelledby="expense-cancel-title">
        <h2 id="expense-cancel-title">{cancelling.correct ? "Corrigir" : "Cancelar"} “{cancelling.expense.description}”</h2>
        <p>{cancelling.correct ? "A despesa atual será cancelada e o formulário abrirá com os valores dela para você registrar a correta." : "A despesa sai dos totais, mas continua na lista com o motivo."}</p>
        <label htmlFor="expense-cancel-reason">Motivo</label>
        <p id="expense-cancel-help" className="field-help">Visível só para a loja. De 3 a 300 caracteres.</p>
        <input ref={reasonRef} id="expense-cancel-reason" value={reason} maxLength={600} autoComplete="off" onChange={event => { setReason(event.target.value); setReasonError(""); }}
          aria-invalid={reasonError ? true : undefined} aria-describedby={reasonError ? "expense-cancel-help expense-cancel-error" : "expense-cancel-help"} />
        {reasonError ? <p id="expense-cancel-error" className="field-error" role="alert">{reasonError}</p> : null}
        <div className="form-actions">
          <button type="submit" className="button-danger" disabled={busy}>{busy ? "Cancelando…" : cancelling.correct ? "Cancelar e corrigir" : "Cancelar despesa"}</button>
          <button type="button" className="button-secondary" disabled={busy} onClick={() => setCancelling(null)}>Manter despesa</button>
        </div>
      </form> : null}
    </> : null}
    {state === "ready" ? <form className="side-card" onSubmit={event => void create(event)} noValidate aria-labelledby="expense-form-title">
      <h2 id="expense-form-title" ref={formTitleRef} tabIndex={-1}>Registrar despesa</h2>
      {field("description", "Descrição", <input id="expense-description" value={draft.description} maxLength={400} autoComplete="off" onChange={event => set("description", event.target.value)} aria-invalid={errors.description ? true : undefined} aria-describedby={described("description")} />)}
      {field("category", "Categoria", <select id="expense-category" value={draft.category} onChange={event => set("category", event.target.value as ExpenseCategory)} aria-invalid={errors.category ? true : undefined} aria-describedby={described("category")}>
        <option value="">Escolha</option>{CATEGORIES.map(value => <option key={value} value={value}>{CATEGORY_LABELS[value]}</option>)}</select>)}
      {field("kind", "Tipo", <select id="expense-kind" value={draft.kind} onChange={event => set("kind", event.target.value as ExpenseKind)} aria-invalid={errors.kind ? true : undefined} aria-describedby={described("kind", true)}>
        <option value="">Escolha</option><option value="FIXED">Fixa</option><option value="VARIABLE">Variável</option></select>, "Variáveis (frete, embalagem, comissões) entram na margem de contribuição; fixas (aluguel, pessoal) não.")}
      {field("amount", "Valor (R$)", <input id="expense-amount" value={draft.amount} inputMode="decimal" autoComplete="off" maxLength={24} onChange={event => set("amount", event.target.value)} aria-invalid={errors.amount ? true : undefined} aria-describedby={described("amount")} />)}
      {field("competenceDate", "Data da despesa", <input id="expense-competenceDate" type="date" value={draft.competenceDate} onChange={event => set("competenceDate", event.target.value)} aria-invalid={errors.competenceDate ? true : undefined} aria-describedby={described("competenceDate", true)} />, "Define o mês em que a despesa conta.")}
      {field("paidOn", "Data do pagamento (opcional)", <input id="expense-paidOn" type="date" value={draft.paidOn} onChange={event => set("paidOn", event.target.value)} aria-describedby={described("paidOn", true)} />, "Deixe em branco se ainda não foi paga. Não pode ser uma data futura.")}
      <div className="form-actions"><button type="submit" disabled={busy}>{busy ? "Registrando…" : "Registrar despesa"}</button></div>
    </form> : null}
  </section>;
}
