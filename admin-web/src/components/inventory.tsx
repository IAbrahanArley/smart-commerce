"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { Product } from "@/lib/catalog";
import { reservedStockMessage, wholeQuantity, type InventorySnapshot, type MovementRequest } from "@/lib/inventory";
import { variantLabel } from "@/components/variant-builder";
import PageHeader from "@/components/page-header";

const integer = new Intl.NumberFormat("pt-BR");
const date = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "medium", timeZone: "America/Sao_Paulo" });

export default function Inventory({ tenantId, productId, variantId }: { tenantId: string; productId: string; variantId?: string }) {
  const productPath = `/lojas/${encodeURIComponent(tenantId)}/produtos/${encodeURIComponent(productId)}`;
  const productApi = `/api/tenants/${encodeURIComponent(tenantId)}/products/${encodeURIComponent(productId)}`;
  const inventoryApi = `${productApi}${variantId ? `/variants/${encodeURIComponent(variantId)}` : ""}/inventory`;
  const [state, setState] = useState<"loading" | "ready" | "error" | "missing" | "signed-out">("loading");
  const [attempt, setAttempt] = useState(0);
  const [product, setProduct] = useState<Product | null>(null);
  const [snapshot, setSnapshot] = useState<InventorySnapshot | null>(null);
  const [kind, setKind] = useState("ENTRY");
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<{ quantity?: string; reason?: string }>({});
  const [busy, setBusy] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [paging, setPaging] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const [pageError, setPageError] = useState("");
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const [pending, setPending] = useState<MovementRequest | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const quantityRef = useRef<HTMLInputElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const pendingFocus = useRef<"quantity" | "reason" | null>(null);
  const dirty = !!pending || !!quantity || !!reason;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(productApi, { signal: controller.signal })
      .then(async productResponse => {
        if (productResponse.status === 401) { setState("signed-out"); return; }
        if (productResponse.status === 403 || productResponse.status === 404) { setState("missing"); return; }
        if (!productResponse.ok) throw new Error();
        const productData: Product = await productResponse.json();
        if (controller.signal.aborted) return;
        setProduct(productData);
        if (productData.kind === "VARIABLE" && !variantId) { setState("ready"); return; }
        if (variantId && (productData.kind !== "VARIABLE" || !productData.variants.some(variant => variant.id === variantId))) { setState("missing"); return; }
        const inventoryResponse = await apiRequest(inventoryApi, { signal: controller.signal });
        if (inventoryResponse.status === 401) { setState("signed-out"); return; }
        if (inventoryResponse.status === 403 || inventoryResponse.status === 404) { setState("missing"); return; }
        if (!inventoryResponse.ok) throw new Error();
        const inventoryData: InventorySnapshot = await inventoryResponse.json();
        if (controller.signal.aborted) return;
        setProduct(productData); setSnapshot(inventoryData); setState("ready");
      }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [productApi, inventoryApi, variantId, attempt]);

  useEffect(() => {
    if (!dirty) return;
    const warning = pending ? "Há uma movimentação sem confirmação. Sair perde a chave de repetição segura. Permaneça nesta tela e confirme o resultado antes de sair." : "Há uma movimentação não salva. Deseja sair sem registrar?";
    const leave = (event: BeforeUnloadEvent) => event.preventDefault();
    const navigate = (event: MouseEvent) => {
      if (event.target instanceof Element && event.target.closest("a[href]") && event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !window.confirm(warning)) event.preventDefault();
    };
    window.addEventListener("beforeunload", leave); document.addEventListener("click", navigate, true);
    return () => { window.removeEventListener("beforeunload", leave); document.removeEventListener("click", navigate, true); };
  }, [dirty, pending]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => {
    if (busy || !pendingFocus.current) return;
    const ref = pendingFocus.current === "quantity" ? quantityRef : reasonRef;
    pendingFocus.current = null; ref.current?.focus();
  }, [busy, errors]);

  async function refresh() {
    setRefreshing(true);
    try {
      const response = await apiRequest(inventoryApi);
      if (!response.ok) throw new Error();
      setSnapshot(await response.json()); setRefreshFailed(false); setPageError("");
    } catch { setRefreshFailed(true); }
    finally { setRefreshing(false); }
  }

  async function send(request: MovementRequest) {
    if (submitting.current) return;
    submitting.current = true; setBusy(true); setFeedback(null); setPending(request);
    let confirmed = false;
    let stale = false;
    try {
      const response = await apiRequest(inventoryApi, { method: "POST", body: request });
      if (response.ok) {
        await response.json();
        confirmed = true; setPending(null); setQuantity(""); setReason("");
        setFeedback({ text: "Movimentação registrada. O saldo será consultado novamente.", error: false });
      } else if ([400, 401, 403, 404, 409, 429].includes(response.status)) {
        const result: { code?: string; reserved?: unknown } = await response.json().catch(() => ({}));
        setPending(null);
        stale = result.code === "RESERVED_STOCK" || result.code === "INSUFFICIENT_STOCK";
        const messages: Record<string, string> = {
          INSUFFICIENT_STOCK: "Saldo insuficiente para retirar essa quantidade. Confira o estoque e ajuste a movimentação.",
          BALANCE_LIMIT: "Esta movimentação ultrapassa o limite do saldo. Reduza a quantidade.",
          REQUEST_CONFLICT: "A chave desta solicitação está associada a outra movimentação. Consulte o histórico antes de tentar novamente.",
        };
        setFeedback({ text: response.status === 401 ? "Sua sessão expirou. Entre novamente para movimentar o estoque." : response.status === 404 ? "Produto ou loja indisponível para esta solicitação." : result.code === "RESERVED_STOCK" ? reservedStockMessage(result.reserved) : messages[result.code ?? ""] ?? "Não foi possível registrar. Confira os dados e tente novamente.", error: true });
      } else setFeedback({ text: "Não foi possível confirmar o resultado. Repita a mesma solicitação abaixo; a chave será preservada para evitar duplicação.", error: true });
    } catch { setFeedback({ text: "A conexão foi interrompida e o resultado não foi confirmado. Repita a mesma solicitação abaixo para consultar ou concluir sem duplicar.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
    if (confirmed || stale) await refresh();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || pending || refreshing || refreshFailed) return;
    const amount = wholeQuantity(quantity);
    const trimmedReason = reason.trim();
    const invalidReason = !trimmedReason || [...trimmedReason].length > 240 || /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/u.test(reason);
    const nextErrors = { ...(!amount ? { quantity: "Informe uma quantidade inteira de 1 a 2.147.483.647, sem pontos ou vírgulas." } : {}), ...(invalidReason ? { reason: "Informe um motivo com até 240 caracteres, sem quebras de linha ou caracteres de controle." } : {}) };
    setErrors(nextErrors); setFeedback(null);
    if (Object.keys(nextErrors).length) { pendingFocus.current = nextErrors.quantity ? "quantity" : "reason"; return; }
    void send({ requestId: crypto.randomUUID(), type: kind === "ENTRY" ? "ENTRY" : "ADJUSTMENT", delta: kind === "REMOVE" ? -amount! : amount!, reason: trimmedReason });
  }

  async function more() {
    if (!snapshot?.nextCursor || paging) return;
    setPaging(true); setPageError("");
    try {
      const response = await apiRequest(`${inventoryApi}?before=${encodeURIComponent(snapshot.nextCursor)}`);
      if (!response.ok) throw new Error();
      const older: InventorySnapshot = await response.json();
      setSnapshot(current => current ? { ...current, movements: [...current.movements, ...older.movements.filter(item => !current.movements.some(existing => existing.id === item.id))], nextCursor: older.nextCursor } : current);
    } catch { setPageError("Não foi possível consultar movimentações anteriores. Tente novamente."); }
    finally { setPaging(false); }
  }

  return <section className="page page-panel" aria-labelledby="inventory-title">
    <PageHeader id="inventory-title" title="Estoque do produto" current="Estoque"
      breadcrumbs={[{ href: `/lojas/${encodeURIComponent(tenantId)}/produtos`, label: "Produtos" }, { href: productPath, label: product?.name ?? "Produto" }]} />
    {state === "loading" ? <p role="status">Consultando estoque…</p> : null}
    {state === "signed-out" ? <><p role="status">Entre novamente para consultar o estoque.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Produto ou loja indisponível. Escolha uma loja à qual você tem acesso.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar o estoque. Tente novamente.</p><button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar estoque novamente</button></> : null}
    {state === "ready" && product?.kind === "VARIABLE" ? <section aria-labelledby="inventory-variant-title"><h2 id="inventory-variant-title">Selecione a combinação</h2><ul className="variant-links">{product.variants.map(variant => <li key={variant.id}><Link href={`${productPath}/estoque?variantId=${encodeURIComponent(variant.id)}`} aria-current={variant.id === variantId ? "page" : undefined}>{variantLabel(variant.options)} — {variant.sku}</Link></li>)}</ul>{!variantId ? <p>O estoque é separado por combinação. Escolha um SKU para consultar o saldo e registrar movimentações.</p> : null}</section> : null}
    {state === "ready" && product && snapshot ? <>
      <h2>{product.name}</h2><p className="product-sku">SKU: <span translate="no">{variantId ? product.variants.find(variant => variant.id === variantId)?.sku : product.sku}</span></p>
      <dl className="inventory-levels">
        <div><dt>Saldo físico</dt><dd>{integer.format(snapshot.quantity)} unidades</dd></div>
        <div><dt>Reservado</dt><dd>{integer.format(snapshot.reserved)} unidades</dd></div>
        <div><dt>Disponível para venda</dt><dd><strong>{integer.format(snapshot.available)} unidades</strong></dd></div>
      </dl>
      <p className="notice">Entradas adicionam unidades e ajustes corrigem o saldo físico. Pedidos pendentes reservam unidades por tempo limitado sem alterar o saldo físico; o disponível é o físico menos o reservado. Ajustes de retirada não podem deixar o físico abaixo do reservado.</p>
      {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
      {refreshFailed ? <p role="alert" className="feedback feedback-error">O saldo exibido pode estar desatualizado. Se uma movimentação foi confirmada, ela já está registrada. Atualize o saldo antes de registrar outra.</p> : null}
      <button type="button" disabled={busy || refreshing || paging} onClick={() => void refresh()}>{refreshing ? "Consultando saldo…" : "Atualizar saldo e histórico"}</button>
      <section className="store-create product-editor" aria-labelledby="movement-title"><h2 id="movement-title">Registrar movimentação</h2>
        <form onSubmit={submit} noValidate aria-busy={busy}>
          <div className="form-field"><label htmlFor="movement-kind">Tipo de movimentação</label><select id="movement-kind" name="type" value={kind} onChange={event => setKind(event.target.value)} disabled={busy || !!pending}>
            <option value="ENTRY">Entrada</option><option value="ADD">Ajuste para adicionar</option><option value="REMOVE">Ajuste para retirar</option>
          </select></div>
          <div className="form-field"><label htmlFor="movement-quantity">Quantidade de unidades</label><input ref={quantityRef} id="movement-quantity" name="quantity" type="text" inputMode="numeric" autoComplete="off" spellCheck={false} maxLength={10} value={quantity} onChange={event => { setQuantity(event.target.value); setErrors(current => ({ ...current, quantity: undefined })); }} disabled={busy || !!pending} required aria-invalid={!!errors.quantity} aria-describedby={`movement-quantity-help${errors.quantity ? " movement-quantity-error" : ""}`} />
            <p id="movement-quantity-help" className="field-help">Somente unidades inteiras, maiores que zero. A retirada é definida pelo tipo de movimentação.</p>{errors.quantity ? <p id="movement-quantity-error" className="field-error">{errors.quantity}</p> : null}</div>
          <div className="form-field"><label htmlFor="movement-reason">Motivo</label><textarea ref={reasonRef} id="movement-reason" name="reason" autoComplete="off" rows={3} maxLength={480} value={reason} onChange={event => { setReason(event.target.value); setErrors(current => ({ ...current, reason: undefined })); }} disabled={busy || !!pending} required aria-invalid={!!errors.reason} aria-describedby={`movement-reason-help${errors.reason ? " movement-reason-error" : ""}`} />
            <p id="movement-reason-help" className="field-help">Até 240 caracteres, sem quebras de linha. Exemplo: recebimento de fornecedor ou conferência física.</p>{errors.reason ? <p id="movement-reason-error" className="field-error">{errors.reason}</p> : null}</div>
          {pending ? <><p className="notice">Solicitação pendente: {pending.type === "ENTRY" ? "entrada" : pending.delta > 0 ? "ajuste para adicionar" : "ajuste para retirar"} de {integer.format(Math.abs(pending.delta))} unidades. O conteúdo permanece bloqueado até a confirmação.</p><button type="button" disabled={busy || refreshing} onClick={() => void send(pending)}>{busy ? "Confirmando movimentação…" : "Repetir mesma solicitação com segurança"}</button></> : <button type="submit" disabled={busy || refreshing || refreshFailed}>{busy ? "Registrando movimentação…" : "Registrar movimentação"}</button>}
        </form>
      </section>
      <section className="store-create" aria-labelledby="history-title"><h2 id="history-title">Histórico de movimentações</h2><p className="field-help">Mais recentes primeiro. Datas no fuso de São Paulo (America/Sao_Paulo).</p>
        {snapshot.movements.length ? <ol className="movement-list">{snapshot.movements.map(movement => <li className="product-card" key={movement.id}>
          <h3>{movement.type === "ENTRY" ? "Entrada" : "Ajuste"}: {movement.delta > 0 ? "+" : "−"}{integer.format(Math.abs(movement.delta))} unidades</h3>
          <p><time dateTime={movement.createdAt}>{date.format(new Date(movement.createdAt))}</time> — São Paulo</p><p>Saldo após: <strong>{integer.format(movement.balanceAfter)} unidades</strong></p><p className="movement-reason">{movement.reason}</p>
        </li>)}</ol> : <p>Nenhuma movimentação registrada. O saldo inicial é zero.</p>}
        {pageError ? <p role="alert" className="feedback feedback-error">{pageError}</p> : null}
        {snapshot.nextCursor ? <button type="button" disabled={paging || refreshing || busy} onClick={() => void more()}>{paging ? "Consultando movimentações…" : "Carregar movimentações anteriores"}</button> : null}
      </section>
    </> : null}
  </section>;
}
