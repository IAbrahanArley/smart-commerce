"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { type Account, parseAccounts } from "@/lib/accounts";
import { formatDateTime } from "@/lib/cash";
import { type CartLine, type CounterOverview, type CounterProduct, type CounterSale, type CounterSettings, METHOD_LABELS, METHODS, type PaymentMethod, itemLabel, minus, parseDays, parsePercent, subtotal, withinLimit } from "@/lib/counter-sales";
import { formatDay, lineText, parseOptionalAmount } from "@/lib/expenses";
import CounterImport from "@/components/counter-import";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";

type SettingsDraft = { limit: string; pix: string; debitFee: string; debitDays: string; creditFee: string; creditDays: string };

// SC-053 (ADR-0019 C6, C8): sales at the physical store, from the same stock as the storefront.
// Employees and the OWNER register; only the OWNER cancels and sets the employee discount limit,
// the Pix account and the card fees and release days. No cost or margin is shown here.
export default function StoreCounter({ tenantId }: { tenantId: string }) {
  const [data, setData] = useState<CounterOverview | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [search, setSearch] = useState("");
  const [found, setFound] = useState<{ query: string; items: CounterProduct[] } | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [discount, setDiscount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [session, setSession] = useState("");
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [errors, setErrors] = useState<{ cart?: string; discount?: string; method?: string }>({});
  const [cancelling, setCancelling] = useState<CounterSale | null>(null);
  const [reason, setReason] = useState("");
  const [restock, setRestock] = useState(true);
  const [reasonError, setReasonError] = useState("");
  const [settings, setSettings] = useState<SettingsDraft | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [settingsErrors, setSettingsErrors] = useState<Partial<Record<keyof SettingsDraft, string>>>({});
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const reasonRef = useRef<HTMLInputElement>(null);
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/counter-sales`;
  const owner = data?.role === "OWNER";
  useUnsavedChanges(cart.length > 0 || Boolean(reason) || busy);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(api, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: CounterOverview = await response.json();
      if (current.role === "OWNER") {
        const [stored, list] = await Promise.all([apiRequest(`${api}/settings`, { signal: controller.signal }), apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/finance/accounts`, { signal: controller.signal })]);
        if (!stored.ok || !list.ok) throw new Error();
        const values: CounterSettings = await stored.json();
        const parsed = parseAccounts(await list.json());
        if (!parsed) throw new Error();
        if (controller.signal.aborted) return;
        // Pix only into a bank account: a drawer would count money that is not in it at closing.
        setAccounts(parsed.filter(account => account.type === "BANK"));
        setSettings({ limit: values.staffDiscountLimit.replace(".", ","), pix: values.pixAccountId ?? "", debitFee: values.debitFeePercent?.replace(".", ",") ?? "", debitDays: values.debitReleaseDays?.toString() ?? "", creditFee: values.creditFeePercent?.replace(".", ",") ?? "", creditDays: values.creditReleaseDays?.toString() ?? "" });
      }
      if (controller.signal.aborted) return;
      setData(current); setState("ready");
      setSession(currentSession => current.sessions.some(item => item.id === currentSession) ? currentSession : current.sessions[0]?.id ?? "");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, tenantId, attempt]);

  // The stock search runs a moment after typing stops; two characters at least, like the backend.
  useEffect(() => {
    const text = search.trim();
    if ([...text].length < 2) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      apiRequest(`${api}/products?q=${encodeURIComponent(text)}`, { signal: controller.signal }).then(async response => {
        if (!response.ok) throw new Error();
        const items: CounterProduct[] = await response.json();
        if (!controller.signal.aborted) setFound({ query: text, items });
      }).catch(() => { if (!controller.signal.aborted) setFound({ query: text, items: [] }); });
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [api, search]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => { if (cancelling) reasonRef.current?.focus(); }, [cancelling]);

  function add(product: CounterProduct) {
    setErrors(current => ({ ...current, cart: undefined }));
    setCart(current => current.some(line => line.product.variantId === product.variantId)
      ? current.map(line => line.product.variantId === product.variantId ? { ...line, quantity: Math.min(line.quantity + 1, product.available) } : line)
      : [...current, { product, quantity: 1 }]);
    setSearch(""); searchRef.current?.focus();
  }

  // Results only for what is typed now: an older answer never shows under a newer query.
  const query = search.trim();
  const results = found && found.query === query && [...query].length >= 2 ? found.items : null;

  const total = subtotal(cart);
  const off = parseOptionalAmount(discount);
  const final = "amount" in off ? minus(total, off.amount) : total;

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !data) return;
    const next: typeof errors = {};
    if (!cart.length) next.cart = "Adicione ao menos um produto do estoque.";
    else if (cart.some(line => !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > line.product.available)) next.cart = "Confira as quantidades: cada uma vai de 1 até o disponível.";
    if ("error" in off) next.discount = off.error;
    else if (cart.length && (final.startsWith("-") || final === "0.00")) next.discount = "O desconto precisa ser menor que o total.";
    else if (!owner && cart.length && !withinLimit(off.amount, total, data.staffDiscountLimit)) next.discount = `O desconto passa do limite de ${data.staffDiscountLimit.replace(".", ",")}% definido pelo dono da loja.`;
    if (method === "CASH" && !session) next.method = "Abra o caixa antes de vender em dinheiro.";
    if (method === "PIX" && !data.pixAccountName) next.method = "O dono da loja ainda não escolheu a conta que recebe o Pix.";
    setErrors(next);
    const first = next.cart ? "counter-search" : next.discount ? "counter-discount" : next.method ? "counter-method-CASH" : null;
    if (first) { document.getElementById(first)?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(api, { method: "POST", body: {
        requestId, items: cart.map(line => ({ variantId: line.product.variantId, quantity: line.quantity })),
        discount: "amount" in off ? off.amount : null, paymentMethod: method, cashSessionId: method === "CASH" ? session : null } });
      if (response.ok) {
        const sale: CounterSale = await response.json();
        setCart([]); setDiscount(""); setRequestId(crypto.randomUUID()); setAttempt(current => current + 1);
        setFeedback({ text: `Venda registrada: ${formatBRL(sale.total)} em ${METHOD_LABELS[sale.paymentMethod].toLowerCase()}.${sale.expectedReleaseOn ? ` Previsão de recebimento: ${formatDay(sale.expectedReleaseOn)}.` : ""}`, error: false });
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de registrar a venda.", error: true }); return; }
      if (response.status === 409) {
        const answer = await response.json().catch(() => null) as { message?: string; code?: string } | null;
        if (answer?.code === "REQUEST_CONFLICT") setRequestId(crypto.randomUUID());
        setAttempt(current => current + 1);
        setFeedback({ text: answer?.message ?? "A venda não pôde ser registrada. Confira e tente de novo.", error: true }); return;
      }
      if (response.status === 404) { setAttempt(current => current + 1); setFeedback({ text: "Algum produto ou o caixa não existe mais. A página foi atualizada.", error: true }); return; }
      if (response.status === 400) { setFeedback({ text: "Confira os dados: quantidades inteiras e desconto com até duas casas decimais.", error: true }); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar a venda. Tente de novo: a mesma tentativa não registra duas vezes.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  async function cancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !cancelling) return;
    const checked = lineText(reason, 300, "o motivo");
    if ("error" in checked) { setReasonError(checked.error); reasonRef.current?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`${api}/${encodeURIComponent(cancelling.id)}/cancel`, { method: "POST", body: { reason: checked.text, restock } });
      if (response.ok) {
        setCancelling(null); setReason(""); setAttempt(current => current + 1);
        setFeedback({ text: `Venda cancelada.${restock ? " Os produtos voltaram ao estoque." : ""}${cancelling.paymentMethod === "CASH" || cancelling.paymentMethod === "PIX" ? ` O valor saiu de “${cancelling.accountName}”.` : ""}`, error: false });
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de cancelar.", error: true }); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar o cancelamento. Consulte a lista antes de tentar de novo.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  async function saveSettings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !settings) return;
    const limit = parsePercent(settings.limit, 100, true);
    const debitFee = parsePercent(settings.debitFee, 100, false);
    const creditFee = parsePercent(settings.creditFee, 100, false);
    const debitDays = parseDays(settings.debitDays);
    const creditDays = parseDays(settings.creditDays);
    const next: Partial<Record<keyof SettingsDraft, string>> = {};
    if ("error" in limit) next.limit = limit.error;
    if ("error" in debitFee) next.debitFee = debitFee.error;
    if ("error" in debitDays) next.debitDays = debitDays.error;
    if ("error" in creditFee) next.creditFee = creditFee.error;
    if ("error" in creditDays) next.creditDays = creditDays.error;
    setSettingsErrors(next);
    const first = (["limit", "debitFee", "debitDays", "creditFee", "creditDays"] as const).find(field => next[field]);
    if (first) { document.getElementById(`settings-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`${api}/settings`, { method: "PUT", body: {
        staffDiscountLimit: "percent" in limit ? limit.percent ?? "0.00" : "0.00", pixAccountId: settings.pix || null,
        debitFeePercent: "percent" in debitFee ? debitFee.percent : null, debitReleaseDays: "days" in debitDays ? debitDays.days : null,
        creditFeePercent: "percent" in creditFee ? creditFee.percent : null, creditReleaseDays: "days" in creditDays ? creditDays.days : null } });
      if (response.ok) { setAttempt(current => current + 1); setFeedback({ text: "Configurações das vendas na loja salvas.", error: false }); return; }
      const answer = await response.json().catch(() => null) as { message?: string } | null;
      setFeedback({ text: answer?.message ?? requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível salvar as configurações. Tente de novo.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  const settingsField = (name: keyof SettingsDraft, label: string, help: string) => settings ? <div className="form-field">
    <label htmlFor={`settings-${name}`}>{label}</label>
    <p id={`settings-${name}-help`} className="field-help">{help}</p>
    <input id={`settings-${name}`} disabled={busy} value={settings[name]} inputMode="decimal" autoComplete="off" maxLength={8}
      onChange={event => { setSettings({ ...settings, [name]: event.target.value }); setSettingsErrors(current => ({ ...current, [name]: undefined })); }}
      aria-invalid={settingsErrors[name] ? true : undefined} aria-describedby={[`settings-${name}-help`, settingsErrors[name] ? `settings-${name}-error` : null].filter(Boolean).join(" ")} />
    {settingsErrors[name] ? <p id={`settings-${name}-error`} className="field-error">{settingsErrors[name]}</p> : null}
  </div> : null;

  return <section className="page" aria-labelledby="counter-title">
    <PageHeader id="counter-title" title="Vendas na loja" description="Registre as vendas da loja física. Cada produto sai do mesmo estoque da vitrine." />
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback ${feedback.error ? "feedback-error" : "feedback-success"}`}>{feedback.text}</p> : null}
    {state === "loading" ? <Loading context="vendas na loja" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para registrar vendas.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="receipt" title="Não foi possível consultar as vendas na loja" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {state === "ready" && data ? <>
      <form className="side-card" onSubmit={event => void register(event)} noValidate aria-labelledby="counter-form-title">
        <h2 id="counter-form-title">Registrar venda</h2>
        <div className="form-field">
          <label htmlFor="counter-search">Adicionar produto do estoque</label>
          <p id="counter-search-help" className="field-help">Busque pelo nome ou pelo SKU. Só aparecem produtos do catálogo, com o disponível de agora.</p>
          <input ref={searchRef} id="counter-search" type="search" disabled={busy} value={search} maxLength={120} autoComplete="off" onChange={event => setSearch(event.target.value)}
            aria-describedby={["counter-search-help", errors.cart ? "counter-cart-error" : null].filter(Boolean).join(" ")} aria-invalid={errors.cart ? true : undefined} />
          {errors.cart ? <p id="counter-cart-error" className="field-error">{errors.cart}</p> : null}
          {results ? <ul className="m-0 grid list-none gap-2 p-0" aria-label="Produtos encontrados" aria-live="polite">
            {results.length ? results.map(product => <li key={product.variantId}>
              <button type="button" className="button-secondary" disabled={busy || product.available < 1} onClick={() => add(product)}>
                Adicionar {itemLabel(product)} · {product.sku} · {formatBRL(product.price)} · {product.available ? `${product.available} disponíve${product.available === 1 ? "l" : "is"}` : "sem estoque"}
              </button>
            </li>) : <li className="field-help">Nenhum produto encontrado.</li>}
          </ul> : null}
        </div>
        {cart.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
          <caption>Itens da venda</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Produto</th><th role="columnheader" scope="col">Quantidade</th><th role="columnheader" scope="col" className="numeric">Total</th><th role="columnheader" scope="col">Ações</th></tr></thead>
          <tbody role="rowgroup">{cart.map(line => <tr role="row" key={line.product.variantId}>
            <th role="rowheader" scope="row">{itemLabel(line.product)}<span className="cell-hint">{line.product.sku} · {formatBRL(line.product.price)} cada · {line.product.available} disponíve{line.product.available === 1 ? "l" : "is"}</span></th>
            <td role="cell" data-label="Quantidade"><input type="number" min={1} max={line.product.available} step={1} disabled={busy} value={line.quantity} aria-label={`Quantidade de ${itemLabel(line.product)}`}
              onChange={event => { const quantity = Number(event.target.value); setCart(current => current.map(item => item.product.variantId === line.product.variantId ? { ...item, quantity } : item)); setErrors(current => ({ ...current, cart: undefined })); }} /></td>
            <td role="cell" data-label="Total" className="numeric">{Number.isInteger(line.quantity) && line.quantity > 0 ? formatBRL(subtotal([line])) : "—"}</td>
            <td role="cell" data-label="Ações"><button type="button" className="button-secondary" disabled={busy} aria-label={`Remover ${itemLabel(line.product)}`} onClick={() => setCart(current => current.filter(item => item.product.variantId !== line.product.variantId))}>Remover</button></td>
          </tr>)}</tbody>
        </table></div> : null}
        <div className="form-field">
          <label htmlFor="counter-discount">Desconto (R$, opcional)</label>
          <p id="counter-discount-help" className="field-help">{owner ? "Valor tirado do total da venda." : `Até ${data.staffDiscountLimit.replace(".", ",")}% do total, conforme o dono da loja.`}</p>
          <input id="counter-discount" disabled={busy} value={discount} inputMode="decimal" autoComplete="off" maxLength={24} onChange={event => { setDiscount(event.target.value); setErrors(current => ({ ...current, discount: undefined })); }}
            aria-invalid={errors.discount ? true : undefined} aria-describedby={["counter-discount-help", errors.discount ? "counter-discount-error" : null].filter(Boolean).join(" ")} />
          {errors.discount ? <p id="counter-discount-error" className="field-error">{errors.discount}</p> : null}
        </div>
        <fieldset className="form-field" aria-describedby={errors.method ? "counter-method-error" : undefined}>
          <legend>Forma de pagamento</legend>
          {METHODS.map(value => <label key={value} className="check-field"><input id={`counter-method-${value}`} type="radio" name="counter-method" value={value} disabled={busy} checked={method === value} onChange={() => { setMethod(value); setErrors(current => ({ ...current, method: undefined })); }} /> {METHOD_LABELS[value]}</label>)}
          {errors.method ? <p id="counter-method-error" className="field-error">{errors.method}</p> : null}
        </fieldset>
        {method === "CASH" ? data.sessions.length ? <div className="form-field">
          <label htmlFor="counter-session">Caixa</label>
          <select id="counter-session" disabled={busy} value={session} onChange={event => setSession(event.target.value)}>{data.sessions.map(item => <option key={item.id} value={item.id}>{item.accountName}</option>)}</select>
        </div> : <p className="field-help">Nenhum caixa aberto. <Link href={`/lojas/${encodeURIComponent(tenantId)}/caixa`}>Abrir o caixa</Link> antes de vender em dinheiro.</p> : null}
        {method === "PIX" ? <p className="field-help">{data.pixAccountName ? `O Pix entra em “${data.pixAccountName}”.` : "O dono da loja ainda não escolheu a conta que recebe o Pix."}</p> : null}
        {method === "DEBIT" || method === "CREDIT" ? <p className="field-help">Venda no cartão fica a receber até a maquininha liberar o valor.</p> : null}
        <p aria-live="polite">Total da venda: <strong>{cart.length && !final.startsWith("-") ? formatBRL(final) : "—"}</strong></p>
        <div className="form-actions"><button type="submit" disabled={busy}>{busy ? "Registrando…" : "Registrar venda"}</button></div>
      </form>
      {cancelling ? <form className="side-card" onSubmit={event => void cancel(event)} noValidate aria-labelledby="counter-cancel-title">
        <h2 id="counter-cancel-title">Cancelar venda de {formatBRL(cancelling.total)}</h2>
        <label htmlFor="counter-cancel-reason">Motivo</label>
        <p id="counter-cancel-help" className="field-help">Fica registrado. De 3 a 300 caracteres.</p>
        <input ref={reasonRef} id="counter-cancel-reason" disabled={busy} value={reason} maxLength={600} autoComplete="off" onChange={event => { setReason(event.target.value); setReasonError(""); }}
          aria-invalid={reasonError ? true : undefined} aria-describedby={reasonError ? "counter-cancel-help counter-cancel-error" : "counter-cancel-help"} />
        {reasonError ? <p id="counter-cancel-error" className="field-error" role="alert">{reasonError}</p> : null}
        <label className="check-field"><input type="checkbox" disabled={busy} checked={restock} onChange={event => setRestock(event.target.checked)} /> Devolver os produtos ao estoque</label>
        <div className="form-actions">
          <button type="submit" className="button-danger" disabled={busy}>{busy ? "Cancelando…" : "Cancelar venda"}</button>
          <button type="button" className="button-secondary" disabled={busy} onClick={() => { setCancelling(null); setReason(""); }}>Manter venda</button>
        </div>
      </form> : null}
      {owner ? <CounterImport tenantId={tenantId} onImported={() => setAttempt(current => current + 1)} /> : null}
      <section aria-labelledby="counter-sales-title">
        <h2 id="counter-sales-title">Últimas vendas na loja</h2>
        {data.sales.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
          <caption>As 50 vendas mais recentes registradas na loja</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Venda</th><th role="columnheader" scope="col">Pagamento</th><th role="columnheader" scope="col" className="numeric">Total</th><th role="columnheader" scope="col">Situação</th>{owner ? <th role="columnheader" scope="col">Ações</th> : null}</tr></thead>
          <tbody role="rowgroup">{data.sales.map(sale => <tr role="row" key={sale.id}>
            <th role="rowheader" scope="row">{sale.items.map(item => `${item.quantity}× ${itemLabel(item)}`).join(", ")}<span className="cell-hint">{sale.soldOn ? `Venda de ${formatDay(sale.soldOn)}, importada em ${formatDateTime(sale.createdAt)}` : formatDateTime(sale.createdAt)} · por {sale.createdBy}</span></th>
            <td role="cell" data-label="Pagamento">{METHOD_LABELS[sale.paymentMethod]}{sale.accountName ? <span className="cell-hint">{sale.accountName}</span> : null}{sale.paymentMethod === "DEBIT" || sale.paymentMethod === "CREDIT" ? <span className="cell-hint">{sale.expectedReleaseOn ? `Recebe em ${formatDay(sale.expectedReleaseOn)}` : "Data de recebimento desconhecida"}{sale.fee ? ` · taxa ${formatBRL(sale.fee)}` : " · taxa não informada"}</span> : null}</td>
            <td role="cell" data-label="Total" className="numeric">{sale.status === "CANCELLED" ? <s>{formatBRL(sale.total)}</s> : formatBRL(sale.total)}{sale.discount !== "0.00" ? <span className="cell-hint">Desconto {formatBRL(sale.discount)}</span> : null}</td>
            <td role="cell" data-label="Situação"><StatusBadge tone={sale.status === "ACTIVE" ? "positive" : "neutral"}>{sale.status === "ACTIVE" ? "Registrada" : "Cancelada"}</StatusBadge>{sale.cancelReason ? <span className="cell-hint">{sale.cancelReason}</span> : null}</td>
            {owner ? <td role="cell" data-label="Ações">{sale.status === "ACTIVE" ? <button type="button" className="button-secondary" disabled={busy} aria-label={`Cancelar venda de ${formatBRL(sale.total)} de ${formatDateTime(sale.createdAt)}`} onClick={() => { setFeedback(null); setReason(""); setReasonError(""); setRestock(true); setCancelling(sale); }}>Cancelar</button> : null}</td> : null}
          </tr>)}</tbody>
        </table></div> : <p className="field-help">Nenhuma venda registrada na loja ainda.</p>}
      </section>
      {owner && settings ? <form className="side-card" onSubmit={event => void saveSettings(event)} noValidate aria-labelledby="counter-settings-title">
        <h2 id="counter-settings-title">Configurações das vendas na loja</h2>
        {settingsField("limit", "Desconto máximo do funcionário (%)", "Percentual do total que um funcionário pode descontar. Zero: nenhum desconto. Você não tem limite.")}
        <div className="form-field">
          <label htmlFor="settings-pix">Conta que recebe o Pix</label>
          <p id="settings-pix-help" className="field-help">Só contas bancárias. Sem conta escolhida, a venda no Pix fica indisponível.</p>
          <select id="settings-pix" disabled={busy} value={settings.pix} onChange={event => setSettings({ ...settings, pix: event.target.value })} aria-describedby="settings-pix-help">
            <option value="">Nenhuma</option>{accounts.map(account => <option key={account.id} value={account.id}>{account.name}</option>)}</select>
        </div>
        {settingsField("debitFee", "Taxa do cartão de débito (%)", "Como cobrada pela sua maquininha. Em branco: taxa não informada, e a margem fica parcial.")}
        {settingsField("debitDays", "Prazo de recebimento do débito (dias)", "Em branco: data de recebimento desconhecida.")}
        {settingsField("creditFee", "Taxa do cartão de crédito (%)", "Como cobrada pela sua maquininha. Em branco: taxa não informada, e a margem fica parcial.")}
        {settingsField("creditDays", "Prazo de recebimento do crédito (dias)", "Em branco: data de recebimento desconhecida.")}
        <div className="form-actions"><button type="submit" disabled={busy}>{busy ? "Salvando…" : "Salvar configurações"}</button></div>
      </form> : null}
    </> : null}
  </section>;
}
