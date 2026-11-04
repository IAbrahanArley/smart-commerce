"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { type Account, manualAccounts, parseAccounts, todayInStore } from "@/lib/accounts";
import { CATEGORIES, CATEGORY_LABELS, KIND_LABELS, MAX_INSTALLMENTS, SITUATION_FILTERS, SITUATION_LABELS, SITUATION_SLUGS, SITUATIONS, type Expense, type ExpenseCategory, type ExpenseKind, type ExpenseMonth, type Situation, expenseDateErrors, formatDay, installmentAmounts, lineText, monthLabel, monthOf, parseAmount, parseOptionalAmount, paymentTotal, shiftMonth } from "@/lib/expenses";
import PageHeader from "@/components/page-header";
import ListPager from "@/components/list-pager";
import { AddButton, FormSheet } from "@/components/form-sheet";
import StatusBadge from "@/components/status-badge";
import { useConfirm } from "@/components/confirm-dialog";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";

type Draft = {
  description: string; supplier: string; documentNumber: string; category: ExpenseCategory | ""; kind: ExpenseKind | "";
  amount: string; installments: string; dueDate: string; competenceDate: string; monthly: boolean; alreadyPaid: boolean; paidOn: string; paidAccountId: string;
};
type Errors = Partial<Record<keyof Draft, string>>;
type Payment = { paidOn: string; accountId: string; surcharge: string; discount: string };
type PaymentErrors = Partial<Record<keyof Payment, string>>;
const EMPTY: Draft = { description: "", supplier: "", documentNumber: "", category: "", kind: "", amount: "", installments: "1", dueDate: "", competenceDate: "", monthly: false, alreadyPaid: false, paidOn: "", paidAccountId: "" };
const ORDER = ["description", "supplier", "documentNumber", "category", "kind", "amount", "installments", "dueDate", "competenceDate", "paidOn", "paidAccountId"] as const;

const today = () => todayInStore(new Date());

function tone(situation: Situation): "positive" | "neutral" | "danger" {
  return situation === "PAID" ? "positive" : situation === "OVERDUE" ? "danger" : "neutral";
}

// SC-051: payables by due month (ADR-0019 C4). Installments split the total, monthly bills create
// next month's one when paid, and paying records the day and the account the money left. Nothing
// is deleted: "Corrigir" cancels with a reason (a paid bill gets its money back in the account)
// and opens the form with its values.
export default function StoreExpenses({ tenantId, month, situation }: { tenantId: string; month: string; situation: Situation | null }) {
  const firstDue = month === monthOf(new Date()) ? today() : `${month}-01`;
  const [data, setData] = useState<ExpenseMonth | null>(null);
  const [accounts, setAccounts] = useState<Account[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<Draft>({ ...EMPTY, dueDate: firstDue });
  const [baseline, setBaseline] = useState(() => JSON.stringify({ ...EMPTY, dueDate: firstDue }));
  const [errors, setErrors] = useState<Errors>({});
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [cancelling, setCancelling] = useState<{ expense: Expense; correct: boolean } | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [paying, setPaying] = useState<Expense | null>(null);
  const [payment, setPayment] = useState<Payment>({ paidOn: "", accountId: "", surcharge: "", discount: "" });
  const [paymentErrors, setPaymentErrors] = useState<PaymentErrors>({});
  const [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false);
  // The page cursor belongs to one month and filter; another month or filter starts on page one.
  const listKey = `${month}|${situation ?? ""}`;
  const [cursor, setCursor] = useState<{ key: string; after: string } | null>(null);
  const after = cursor?.key === listKey ? cursor.after : null;
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const reasonRef = useRef<HTMLInputElement>(null);
  const actionTrigger = useRef<HTMLButtonElement | null>(null);
  const { confirm, dialog } = useConfirm();
  const draftDirty = JSON.stringify(draft) !== baseline;
  const dirty = draftDirty || Boolean(reason) || Boolean(paying) || busy;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/expenses`;
  // The Mercado Pago account follows the provider's own facts and pays nothing by hand.
  const payingAccounts = manualAccounts(accounts ?? []);
  const pagePath = (target: string, filter: Situation | null) => `${storePath}/despesas?mes=${target}${filter ? `&situacao=${SITUATION_SLUGS[filter]}` : ""}`;

  useEffect(() => {
    const controller = new AbortController();
    const query = `${api}?month=${month}${situation ? `&situation=${situation}` : ""}${after ? `&after=${encodeURIComponent(after)}` : ""}`;
    Promise.all([apiRequest(query, { signal: controller.signal }), apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/finance/accounts`, { signal: controller.signal })]).then(async ([response, accountResponse]) => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok || !accountResponse.ok) throw new Error();
      const current: ExpenseMonth = await response.json();
      const list = parseAccounts(await accountResponse.json());
      if (!list) throw new Error();
      if (controller.signal.aborted) return;
      setData(current); setAccounts(list); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, tenantId, month, situation, attempt, after]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => { if (cancelling) reasonRef.current?.focus(); }, [cancelling]);
  useUnsavedChanges(dirty);

  function set<K extends keyof Draft>(name: K, value: Draft[K]) {
    setDraft(current => ({ ...current, [name]: value }));
    setErrors(current => ({ ...current, [name]: undefined }));
  }

  const count = Number(draft.installments);
  const split = count > 1 && Number.isInteger(count) && count <= MAX_INSTALLMENTS;

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const next: Errors = {};
    const description = lineText(draft.description, 200, "a descrição");
    if ("error" in description) next.description = description.error;
    const supplier = draft.supplier.trim();
    if (supplier && ([...supplier].length < 2 || [...supplier].length > 120 || /[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(draft.supplier))) next.supplier = "Informe o fornecedor com 2 a 120 caracteres, em uma linha, ou deixe em branco.";
    const documentNumber = draft.documentNumber.trim();
    if ([...documentNumber].length > 40 || /[\p{Cc}\p{Cf}]/u.test(documentNumber)) next.documentNumber = "Use até 40 caracteres, em uma linha.";
    if (!draft.category) next.category = "Escolha a categoria.";
    if (!draft.kind) next.kind = "Escolha se a despesa é fixa ou variável.";
    const amount = parseAmount(draft.amount);
    if ("error" in amount) next.amount = amount.error;
    if (!/^\d{1,2}$/.test(draft.installments) || count < 1 || count > MAX_INSTALLMENTS) next.installments = `Informe de 1 a ${MAX_INSTALLMENTS} parcelas.`;
    else if (split && "amount" in amount && installmentAmounts(amount.amount, count)[0] === "0.00") next.amount = "O valor total precisa cobrir ao menos um centavo por parcela.";
    const dueErrors = expenseDateErrors(draft.dueDate, "", new Date());
    if (dueErrors.competenceDate) next.dueDate = dueErrors.competenceDate;
    if (!split && draft.competenceDate) {
      const competenceErrors = expenseDateErrors(draft.competenceDate, "", new Date());
      if (competenceErrors.competenceDate) next.competenceDate = competenceErrors.competenceDate;
    }
    if (!split && draft.alreadyPaid) {
      const paidErrors = expenseDateErrors(draft.dueDate || "2000-01-01", draft.paidOn, new Date());
      if (!draft.paidOn) next.paidOn = "Informe a data do pagamento.";
      else if (paidErrors.paidOn) next.paidOn = paidErrors.paidOn;
      if (!draft.paidAccountId) next.paidAccountId = "Escolha a conta de onde o dinheiro saiu.";
    }
    setErrors(next);
    const first = ORDER.find(field => next[field]);
    if (first) { document.getElementById(`expense-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(api, { method: "POST", body: {
        requestId, category: draft.category, kind: draft.kind,
        description: "text" in description ? description.text : "",
        supplier: supplier || null, documentNumber: documentNumber || null,
        amount: "amount" in amount ? amount.amount : "", installments: count,
        dueDate: draft.dueDate, competenceDate: split ? null : draft.competenceDate || null,
        recurrence: !split && draft.monthly ? "MONTHLY" : "NONE",
        paidOn: !split && draft.alreadyPaid ? draft.paidOn : null, paidAccountId: !split && draft.alreadyPaid ? draft.paidAccountId : null } });
      if (response.ok) {
        const clean = { ...EMPTY, dueDate: draft.dueDate };
        setDraft(clean); setBaseline(JSON.stringify(clean)); setRequestId(crypto.randomUUID());
        setCreating(false); setFeedback({ text: split ? `Conta registrada em ${count} parcelas.` : "Conta registrada.", error: false }); setAttempt(current => current + 1);
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de registrar a conta.", error: true }); return; }
      if (response.status === 409) {
        const body = await response.json().catch(() => null) as { code?: string } | null;
        if (body?.code === "PROVIDER_READ_ONLY") { setErrors({ paidAccountId: "A conta do Mercado Pago não paga contas manualmente. Escolha outra conta." }); document.getElementById("expense-paidAccountId")?.focus(); return; }
        setRequestId(crypto.randomUUID()); setFeedback({ text: "Esta tentativa já registrou outra conta. Confira a lista e, se precisar, registre de novo.", error: true }); setAttempt(current => current + 1); return;
      }
      if (response.status === 400) { setFeedback({ text: "Confira os dados: datas desde 2000 até um ano à frente, pagamento até hoje e valor com até duas casas decimais.", error: true }); return; }
      if (response.status === 404 && !split && draft.alreadyPaid) { setFeedback({ text: "A conta de pagamento escolhida não existe mais. Recarregue a página.", error: true }); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar o registro. Tente novamente: a mesma tentativa será reaproveitada, sem duplicar a conta.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  function openPayment(expense: Expense, trigger: HTMLButtonElement) {
    actionTrigger.current = trigger; setFeedback(null); setCancelling(null);
    setPaymentErrors({}); setPayment({ paidOn: today(), accountId: payingAccounts[0]?.id ?? "", surcharge: "", discount: "" });
    setPaying(expense);
  }

  const surcharge = parseOptionalAmount(payment.surcharge);
  const discount = parseOptionalAmount(payment.discount);
  const total = paying && "amount" in surcharge && "amount" in discount ? paymentTotal(paying.amount, surcharge.amount, discount.amount) : null;

  async function pay(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !paying) return;
    const next: PaymentErrors = {};
    const dateErrors = expenseDateErrors("2000-01-01", payment.paidOn, new Date());
    if (!payment.paidOn) next.paidOn = "Informe a data do pagamento.";
    else if (dateErrors.paidOn) next.paidOn = "Informe uma data válida desde 2000 até hoje.";
    if (!payment.accountId) next.accountId = "Escolha a conta de onde o dinheiro saiu.";
    if ("error" in surcharge) next.surcharge = surcharge.error;
    if ("error" in discount) next.discount = discount.error;
    else if (total && (total.startsWith("-") || total === "0.00")) next.discount = "O desconto precisa ser menor que o valor da conta com juros.";
    setPaymentErrors(next);
    const first = (["paidOn", "accountId", "surcharge", "discount"] as const).find(field => next[field]);
    if (first) { document.getElementById(`payment-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    const expense = paying;
    try {
      const response = await apiRequest(`${api}/${encodeURIComponent(expense.id)}/pay`, { method: "POST", body: {
        paidOn: payment.paidOn, accountId: payment.accountId,
        surcharge: "amount" in surcharge ? surcharge.amount : "", discount: "amount" in discount ? discount.amount : "" } });
      if (response.ok) {
        setPaying(null); setAttempt(current => current + 1);
        setFeedback({ text: expense.recurrence === "MONTHLY" ? `Pagamento registrado. A conta do próximo mês de “${expense.description}” já foi criada.` : "Pagamento registrado.", error: false });
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de registrar o pagamento.", error: true }); return; }
      if (response.status === 409) {
        const body = await response.json().catch(() => null) as { message?: string } | null;
        setPaying(null); setAttempt(current => current + 1);
        setFeedback({ text: body?.message ?? "Esta conta mudou. Recarregue a lista.", error: true }); return;
      }
      if (response.status === 400) { setFeedback({ text: "Confira os dados do pagamento: a data não pode ser futura e o desconto precisa ser menor que o valor.", error: true }); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar o pagamento. Consulte a lista antes de tentar de novo: repetir os mesmos dados não paga duas vezes.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  async function cancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !cancelling) return;
    const checked = lineText(reason, 300, "o motivo");
    if ("error" in checked) { setReasonError(checked.error); reasonRef.current?.focus(); return; }
    if (cancelling.correct && draftDirty && !(await confirm({ title: "Substituir o rascunho?", message: "Há uma conta não registrada no formulário. Para corrigir a conta selecionada, o rascunho será descartado.", confirmLabel: "Descartar rascunho" }))) return;
    submitting.current = true; setBusy(true); setFeedback(null);
    const { expense, correct } = cancelling;
    try {
      const response = await apiRequest(`${api}/${encodeURIComponent(expense.id)}/cancel`, { method: "POST", body: { reason: checked.text } });
      if (response.ok) {
        setCancelling(null); setReason(""); setAttempt(current => current + 1);
        const refunded = expense.paidAccountName ? ` O valor pago voltou para “${expense.paidAccountName}”.` : "";
        if (correct) {
          setErrors({}); setCreating(true);
          setDraft({ ...EMPTY, description: expense.description, supplier: expense.supplier ?? "", documentNumber: expense.documentNumber ?? "", category: expense.category, kind: expense.kind,
            amount: expense.amount.replace(".", ","), dueDate: expense.dueDate, competenceDate: expense.competenceDate === expense.dueDate ? "" : expense.competenceDate, monthly: expense.recurrence === "MONTHLY" });
          setRequestId(crypto.randomUUID());
          const paidAgain = expense.paidOn ? " Se ela já foi paga, marque “Já está paga” de novo." : "";
          setFeedback({ text: `Conta cancelada.${refunded} Ajuste os valores e registre a conta correta.${paidAgain}`, error: false });
        } else setFeedback({ text: `Conta cancelada.${refunded} Ela continua na lista para consulta.`, error: false });
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
  const paymentField = (name: keyof Payment, label: string, control: React.ReactNode, help?: string) => <div className="form-field">
    <label htmlFor={`payment-${name}`}>{label}</label>
    {help ? <p id={`payment-${name}-help`} className="field-help">{help}</p> : null}
    {control}
    {paymentErrors[name] ? <p id={`payment-${name}-error`} className="field-error">{paymentErrors[name]}</p> : null}
  </div>;
  const paymentDescribed = (name: keyof Payment, help?: boolean) => [help ? `payment-${name}-help` : null, paymentErrors[name] ? `payment-${name}-error` : null].filter(Boolean).join(" ") || undefined;
  const setPaymentField = (name: keyof Payment, value: string) => { setPayment(current => ({ ...current, [name]: value })); setPaymentErrors(current => ({ ...current, [name]: undefined })); };
  const preview = split && "amount" in parseAmount(draft.amount) ? installmentAmounts((parseAmount(draft.amount) as { amount: string }).amount, count) : null;
  const caption = situation === "OVERDUE" ? "Contas vencidas de todos os meses" : `Contas com vencimento em ${monthLabel(month)}${situation ? ` · ${SITUATION_FILTERS[situation]}` : ""}`;

  const sheetOpen = creating || Boolean(paying) || Boolean(cancelling);
  const feedbackNode = feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback ${feedback.error ? "feedback-error" : "feedback-success"}`}>{feedback.text}</p> : null;

  return <section className="page" aria-labelledby="expenses-title">
    <PageHeader id="expenses-title" title="Contas a pagar" description="Duplicatas, salários e despesas da loja por mês de vencimento. Para corrigir, cancele com um motivo e registre de novo: nada é apagado."
      action={state === "ready" ? <AddButton onClick={() => { setFeedback(null); setPaying(null); setCancelling(null); setCreating(true); }}>Nova conta</AddButton> : undefined} />
    {situation !== "OVERDUE" ? <nav className="period-navigation" aria-label="Mês de vencimento">
      <Link className="button-link button-secondary" href={pagePath(shiftMonth(month, -1), situation)} prefetch={false}><span aria-hidden="true">‹</span> Mês anterior</Link>
      <div className="period-navigation-current"><span className="field-help">Vencimento</span><strong>{monthLabel(month)}</strong></div>
      <Link className="button-link button-secondary" href={pagePath(shiftMonth(month, 1), situation)} prefetch={false}>Próximo mês <span aria-hidden="true">›</span></Link>
    </nav> : null}
    <nav className="status-tabs" aria-label="Filtrar contas por situação">
      <Link href={pagePath(month, null)} prefetch={false} aria-current={situation === null ? "page" : undefined}>Todas</Link>
      {SITUATIONS.map(value => <Link key={value} href={pagePath(month, value)} prefetch={false} aria-current={situation === value ? "page" : undefined}>{SITUATION_FILTERS[value]}</Link>)}
    </nav>
    {sheetOpen ? null : feedbackNode}
    {state === "loading" ? <Loading context="contas a pagar" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar as contas a pagar.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="wallet" title="Não foi possível consultar as contas a pagar" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar contas novamente">Confira a conexão e tente novamente. Seus lançamentos continuam registrados.</PageState> : null}
    {state === "ready" && data ? <>
      <dl className="order-facts expense-totals">
        <div><dt>A pagar em {monthLabel(month)}</dt><dd className="numeric"><strong>{formatBRL(data.toPay)}</strong></dd></div>
        <div><dt>Pago em {monthLabel(month)}</dt><dd className="numeric">{formatBRL(data.paid)}</dd></div>
        <div><dt>Vencidas (todos os meses)</dt><dd className="numeric">{data.overdueCount ? <Link href={pagePath(month, "OVERDUE")} prefetch={false}>{formatBRL(data.overdueTotal)} em {data.overdueCount} {data.overdueCount === 1 ? "conta" : "contas"}</Link> : "Nenhuma"}</dd></div>
      </dl>
      {data.expenses.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
        <caption>{caption}</caption>
        <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Conta</th><th role="columnheader" scope="col">Vencimento</th><th role="columnheader" scope="col">Situação</th><th role="columnheader" scope="col" className="numeric">Valor</th><th role="columnheader" scope="col">Ações</th></tr></thead>
        <tbody role="rowgroup">{data.expenses.map(expense => <tr role="row" key={expense.id}>
          <th role="rowheader" scope="row" className="expense-description">{expense.description}
            {expense.supplier || expense.documentNumber ? <span className="cell-hint">{[expense.supplier, expense.documentNumber ? `Doc. ${expense.documentNumber}` : null].filter(Boolean).join(" · ")}</span> : null}
            <span className="cell-hint">{[CATEGORY_LABELS[expense.category], KIND_LABELS[expense.kind], expense.installmentCount ? `Parcela ${expense.installmentNumber} de ${expense.installmentCount}` : null, expense.recurrence === "MONTHLY" ? "Mensal" : null].filter(Boolean).join(" · ")}</span>
            {expense.status === "CANCELLED" && expense.cancelReason ? <span className="cell-hint">Motivo: {expense.cancelReason}</span> : null}</th>
          <td role="cell" data-label="Vencimento">{formatDay(expense.dueDate)}{expense.competenceDate !== expense.dueDate ? <span className="cell-hint">Competência {formatDay(expense.competenceDate)}</span> : null}</td>
          <td role="cell" data-label="Situação"><StatusBadge tone={tone(expense.situation)}>{SITUATION_LABELS[expense.situation]}</StatusBadge>
            {expense.paidOn ? <span className="cell-hint">Paga em {formatDay(expense.paidOn)} · {expense.paidAccountName ?? "conta não informada"}</span> : null}</td>
          <td role="cell" data-label="Valor" className="numeric">{expense.status === "CANCELLED" ? <s>{formatBRL(expense.amount)}</s> : formatBRL(expense.amount)}
            {expense.paidTotal && expense.paidTotal !== expense.amount ? <span className="cell-hint">Pago {formatBRL(expense.paidTotal)}</span> : null}</td>
          <td role="cell" data-label="Ações">{expense.status === "ACTIVE" ? <div className="form-actions">
            {!expense.paidOn ? <button type="button" disabled={busy || !payingAccounts.length} aria-label={`Pagar ${expense.description}`} onClick={event => openPayment(expense, event.currentTarget)}>Pagar</button> : null}
            <button type="button" className="button-secondary" disabled={busy} aria-label={`Corrigir ${expense.description}`} onClick={event => { actionTrigger.current = event.currentTarget; setFeedback(null); setPaying(null); setReason(""); setReasonError(""); setCancelling({ expense, correct: true }); }}>Corrigir</button>
            <button type="button" className="button-secondary" disabled={busy} aria-label={`Cancelar ${expense.description}`} onClick={event => { actionTrigger.current = event.currentTarget; setFeedback(null); setPaying(null); setReason(""); setReasonError(""); setCancelling({ expense, correct: false }); }}>Cancelar</button>
          </div> : null}</td>
        </tr>)}</tbody>
      </table></div> : null}
      <ListPager label="Páginas de contas a pagar" disabled={busy}
        first={after ? { text: "Voltar ao início", onClick: () => setCursor(null) } : null}
        next={data.nextCursor ? { text: "Ver próximas contas", onClick: () => setCursor({ key: listKey, after: data.nextCursor! }) } : null} />
      {data.expenses.length ? null : <PageState icon="wallet" title={situation ? "Nenhuma conta nesta situação" : "Nenhuma conta neste mês"}>{situation === "OVERDUE" ? "Nenhuma conta vencida. Tudo em dia." : `Nenhuma conta ${situation ? SITUATION_FILTERS[situation].toLowerCase() : "registrada"} com vencimento em ${monthLabel(month)}.`}</PageState>}
      <FormSheet open={Boolean(paying)} onOpenChange={open => { if (!open) setPaying(null); }} titleId="payment-title" title={paying ? `Pagar “${paying.description}”` : "Pagar"} busy={busy} feedbackRef={feedbackRef}>{feedbackNode}
      {paying ? <form onSubmit={event => void pay(event)} noValidate aria-labelledby="payment-title">
        <p>Valor da conta: <strong>{formatBRL(paying.amount)}</strong>, com vencimento em {formatDay(paying.dueDate)}.{paying.recurrence === "MONTHLY" ? " Ao pagar, a conta do próximo mês será criada." : ""}</p>
        {paymentField("paidOn", "Data do pagamento", <input id="payment-paidOn" type="date" min="2000-01-01" max={today()} disabled={busy} value={payment.paidOn} onChange={event => setPaymentField("paidOn", event.target.value)} aria-invalid={paymentErrors.paidOn ? true : undefined} aria-describedby={paymentDescribed("paidOn")} />)}
        {paymentField("accountId", "Conta de saída", <select id="payment-accountId" disabled={busy} value={payment.accountId} onChange={event => setPaymentField("accountId", event.target.value)} aria-invalid={paymentErrors.accountId ? true : undefined} aria-describedby={paymentDescribed("accountId", true)}>
          <option value="">Escolha</option>{payingAccounts.map(account => <option key={account.id} value={account.id}>{account.name}</option>)}</select>, "O valor pago sai do saldo desta conta. A conta do Mercado Pago não aparece: ela segue só os pagamentos do próprio Mercado Pago.")}
        {paymentField("surcharge", "Juros ou multa (R$, opcional)", <input id="payment-surcharge" disabled={busy} value={payment.surcharge} inputMode="decimal" autoComplete="off" maxLength={24} onChange={event => setPaymentField("surcharge", event.target.value)} aria-invalid={paymentErrors.surcharge ? true : undefined} aria-describedby={paymentDescribed("surcharge")} />)}
        {paymentField("discount", "Desconto (R$, opcional)", <input id="payment-discount" disabled={busy} value={payment.discount} inputMode="decimal" autoComplete="off" maxLength={24} onChange={event => setPaymentField("discount", event.target.value)} aria-invalid={paymentErrors.discount ? true : undefined} aria-describedby={paymentDescribed("discount")} />)}
        <p aria-live="polite">Total que sai da conta: <strong>{total && !total.startsWith("-") ? formatBRL(total) : "—"}</strong></p>
        <div className="form-actions">
          <button type="submit" disabled={busy}>{busy ? "Registrando…" : "Registrar pagamento"}</button>
          <button type="button" className="button-secondary" disabled={busy} onClick={() => { setPaying(null); actionTrigger.current?.focus(); }}>Voltar</button>
        </div>
      </form> : null}
      </FormSheet>
      <FormSheet open={Boolean(cancelling)} onOpenChange={open => { if (!open) { setCancelling(null); setReason(""); } }} titleId="expense-cancel-title" title={cancelling ? `${cancelling.correct ? "Corrigir" : "Cancelar"} “${cancelling.expense.description}”` : "Cancelar"} busy={busy} feedbackRef={feedbackRef}>{feedbackNode}{dialog}
      {cancelling ? <form onSubmit={event => void cancel(event)} noValidate aria-labelledby="expense-cancel-title">
        <p>{cancelling.correct ? "A conta atual será cancelada e o formulário abrirá com os valores dela para você registrar a correta." : "A conta sai dos totais, mas continua na lista com o motivo."}
          {cancelling.expense.paidAccountName ? ` O pagamento de ${formatBRL(cancelling.expense.paidTotal ?? cancelling.expense.amount)} volta para “${cancelling.expense.paidAccountName}” hoje.` : ""}
          {cancelling.expense.recurrence === "MONTHLY" && cancelling.expense.paidOn ? " A conta do mês seguinte, se ainda estiver em aberto, também será cancelada." : ""}</p>
        <label htmlFor="expense-cancel-reason">Motivo</label>
        <p id="expense-cancel-help" className="field-help">Visível só para a loja. De 3 a 300 caracteres.</p>
        <input ref={reasonRef} id="expense-cancel-reason" disabled={busy} value={reason} maxLength={600} autoComplete="off" onChange={event => { setReason(event.target.value); setReasonError(""); }}
          aria-invalid={reasonError ? true : undefined} aria-describedby={reasonError ? "expense-cancel-help expense-cancel-error" : "expense-cancel-help"} />
        {reasonError ? <p id="expense-cancel-error" className="field-error" role="alert">{reasonError}</p> : null}
        <div className="form-actions">
          <button type="submit" className="button-danger" disabled={busy}>{busy ? "Cancelando…" : cancelling.correct ? "Cancelar e corrigir" : "Cancelar conta"}</button>
          <button type="button" className="button-secondary" disabled={busy} onClick={() => { setCancelling(null); setReason(""); actionTrigger.current?.focus(); }}>Manter conta</button>
        </div>
      </form> : null}
      </FormSheet>
    </> : null}
    <FormSheet open={state === "ready" && creating} onOpenChange={setCreating} titleId="expense-form-title" title="Registrar conta" busy={busy} feedbackRef={feedbackRef}>{feedbackNode}
    <form onSubmit={event => void create(event)} noValidate aria-labelledby="expense-form-title">
      {field("description", "Descrição", <input id="expense-description" disabled={busy} value={draft.description} maxLength={400} autoComplete="off" onChange={event => set("description", event.target.value)} aria-invalid={errors.description ? true : undefined} aria-describedby={described("description")} />)}
      {field("supplier", "Fornecedor (opcional)", <input id="expense-supplier" disabled={busy} value={draft.supplier} maxLength={240} autoComplete="off" onChange={event => set("supplier", event.target.value)} aria-invalid={errors.supplier ? true : undefined} aria-describedby={described("supplier")} />)}
      {field("documentNumber", "Número do documento (opcional)", <input id="expense-documentNumber" disabled={busy} value={draft.documentNumber} maxLength={80} autoComplete="off" onChange={event => set("documentNumber", event.target.value)} aria-invalid={errors.documentNumber ? true : undefined} aria-describedby={described("documentNumber", true)} />, "Duplicata, boleto ou nota, para conferir depois.")}
      {field("category", "Categoria", <select id="expense-category" disabled={busy} value={draft.category} onChange={event => set("category", event.target.value as ExpenseCategory)} aria-invalid={errors.category ? true : undefined} aria-describedby={described("category")}>
        <option value="">Escolha</option>{CATEGORIES.map(value => <option key={value} value={value}>{CATEGORY_LABELS[value]}</option>)}</select>)}
      {field("kind", "Tipo", <select id="expense-kind" disabled={busy} value={draft.kind} onChange={event => set("kind", event.target.value as ExpenseKind)} aria-invalid={errors.kind ? true : undefined} aria-describedby={described("kind", true)}>
        <option value="">Escolha</option><option value="FIXED">Fixa</option><option value="VARIABLE">Variável</option></select>, "Variáveis (frete, embalagem, comissões) entram na margem de contribuição; fixas (aluguel, pessoal) não.")}
      {field("amount", split ? "Valor total (R$)" : "Valor (R$)", <input id="expense-amount" disabled={busy} value={draft.amount} inputMode="decimal" autoComplete="off" maxLength={24} onChange={event => set("amount", event.target.value)} aria-invalid={errors.amount ? true : undefined} aria-describedby={described("amount")} />)}
      {field("installments", "Parcelas", <input id="expense-installments" disabled={busy} value={draft.installments} inputMode="numeric" autoComplete="off" maxLength={2} onChange={event => set("installments", event.target.value.replace(/\D/g, ""))} aria-invalid={errors.installments ? true : undefined} aria-describedby={described("installments", true)} />,
        preview ? `${count} parcelas mensais: ${preview[0] === preview[count - 1] ? `${count} × ${formatBRL(preview[0])}` : `${count - 1} × ${formatBRL(preview[0])} e a última de ${formatBRL(preview[count - 1])}`}. Cada parcela conta no mês em que vence.` : `De 1 a ${MAX_INSTALLMENTS}. Com mais de uma, o valor é dividido em parcelas mensais.`)}
      {field("dueDate", split ? "Vencimento da primeira parcela" : "Vencimento", <input id="expense-dueDate" disabled={busy} type="date" min="2000-01-01" value={draft.dueDate} onChange={event => set("dueDate", event.target.value)} aria-invalid={errors.dueDate ? true : undefined} aria-describedby={described("dueDate", true)} />, "Desde 2000 até um ano à frente.")}
      {!split ? <>
        {field("competenceDate", "Data da despesa (opcional)", <input id="expense-competenceDate" disabled={busy} type="date" min="2000-01-01" value={draft.competenceDate} onChange={event => set("competenceDate", event.target.value)} aria-invalid={errors.competenceDate ? true : undefined} aria-describedby={described("competenceDate", true)} />, "Define o mês em que a despesa conta no resultado. Em branco, vale o vencimento.")}
        <label className="check-field"><input type="checkbox" disabled={busy} checked={draft.monthly} onChange={event => set("monthly", event.target.checked)} aria-describedby="expense-monthly-help" /> Repetir todo mês</label>
        <p id="expense-monthly-help" className="field-help">Ao pagar, a conta do mês seguinte é criada com o mesmo valor. Para encerrar, cancele a conta em aberto.</p>
        <label className="check-field"><input type="checkbox" disabled={busy} checked={draft.alreadyPaid} onChange={event => { const checked = event.target.checked; setErrors(current => ({ ...current, paidOn: undefined, paidAccountId: undefined }));
          setDraft(current => ({ ...current, alreadyPaid: checked, paidOn: checked && !current.paidOn ? today() : current.paidOn, paidAccountId: checked && !current.paidAccountId ? payingAccounts[0]?.id ?? "" : current.paidAccountId })); }} /> Já está paga</label>
        {draft.alreadyPaid ? <>
          {field("paidOn", "Data do pagamento", <input id="expense-paidOn" disabled={busy} type="date" min="2000-01-01" max={today()} value={draft.paidOn} onChange={event => set("paidOn", event.target.value)} aria-invalid={errors.paidOn ? true : undefined} aria-describedby={described("paidOn")} />)}
          {field("paidAccountId", "Conta de saída", <select id="expense-paidAccountId" disabled={busy} value={draft.paidAccountId} onChange={event => set("paidAccountId", event.target.value)} aria-invalid={errors.paidAccountId ? true : undefined} aria-describedby={described("paidAccountId")}>
            <option value="">Escolha</option>{payingAccounts.map(account => <option key={account.id} value={account.id}>{account.name}</option>)}</select>)}
        </> : null}
      </> : null}
      <div className="form-actions"><button type="submit" disabled={busy}>{busy ? "Registrando…" : split ? `Registrar ${count} parcelas` : "Registrar conta"}</button></div>
    </form>
    </FormSheet>
  </section>;
}
