"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { formatBRL, type Product } from "@/lib/catalog";
import type { TenantStore } from "@/lib/tenant-api";
import ProductForm from "@/components/product-form";
import ProductImages from "@/components/product-images";
import ProductVariants from "@/components/product-variants";

export default function Catalog({ tenantId, productId }: { tenantId: string; productId?: string }) {
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [store, setStore] = useState<TenantStore | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [product, setProduct] = useState<Product | undefined>();
  const [attempt, setAttempt] = useState(0);
  const [notice, setNotice] = useState("");
  const [draftDirty, setDraftDirty] = useState(false);
  const noticeRef = useRef<HTMLParagraphElement>(null);
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  const apiPath = `/api/tenants/${encodeURIComponent(tenantId)}`;

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([apiRequest(apiPath, { signal: controller.signal }), apiRequest(`${apiPath}/products${productId ? `/${encodeURIComponent(productId)}` : ""}`, { signal: controller.signal })])
      .then(async ([storeResponse, productResponse]) => {
        if (storeResponse.status === 401 || productResponse.status === 401) { setState("signed-out"); return; }
        if ([storeResponse.status, productResponse.status].some(status => status === 403 || status === 404)) { setState("missing"); return; }
        if (!storeResponse.ok || !productResponse.ok) throw new Error();
        const [storeData, productData] = await Promise.all([storeResponse.json(), productResponse.json()]);
        if (controller.signal.aborted) return;
        setStore(storeData);
        if (productId) setProduct(productData); else setProducts(productData);
        setState("ready");
      }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [apiPath, productId, attempt]);

  useEffect(() => { if (notice) noticeRef.current?.focus(); }, [notice, product]);

  function saved(savedProduct: Product, customNotice?: string) {
    if (productId) setProduct(savedProduct);
    else setProducts(current => [savedProduct, ...current]);
    setNotice(customNotice ?? (productId ? "Alterações salvas." : "Produto cadastrado no catálogo interno. Ele começa oculto na vitrine."));
  }
  function reload() { setState("loading"); setNotice(""); setDraftDirty(false); setAttempt(current => current + 1); }

  return <section className="stores-panel" aria-labelledby="catalog-title">
    <Link className="back-link" href={productId ? `${storePath}/produtos` : storePath}>{productId ? "Voltar para produtos" : "Voltar para minha loja"}</Link>
    <h1 id="catalog-title">{productId ? "Editar produto" : "Produtos"}</h1>
    {state === "loading" ? <p role="status">Consultando catálogo…</p> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar os produtos.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Produto ou loja indisponível. Volte para suas lojas e escolha uma loja à qual você tem acesso.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar o catálogo. Tente novamente.</p><button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar catálogo novamente</button></> : null}
    {state === "ready" && store ? <>
      <p className="catalog-store">Loja: <strong>{store.store.name}</strong></p>
      <p className="notice">{store.store.status === "PUBLISHED" ? "Sua vitrine está publicada. Somente os produtos selecionados individualmente ficam visíveis para consulta pública." : "Sua vitrine está oculta. Você pode selecionar produtos agora; eles só ficarão visíveis quando publicar a loja."} Cadastrar um produto não altera o estoque.</p>
      {notice ? <p ref={noticeRef} tabIndex={-1} role="status" className="feedback feedback-success">{notice}</p> : null}
      {productId && product ? <div className="product-workspace"><Link className="back-link" href={`${storePath}/produtos/${encodeURIComponent(product.id)}/estoque`}>Consultar e movimentar estoque</Link><ProductForm key={`${product.id}-${attempt}`} tenantId={tenantId} product={product} storePublished={store.store.status === "PUBLISHED"} onSaved={saved} onDirtyChange={setDraftDirty} onConflict={reload} />
        <ProductImages tenantId={tenantId} productId={product.id} version={product.version} locked={draftDirty} onVersion={version => setProduct(current => current ? { ...current, version } : current)} onReload={reload} />
        {product.kind === "VARIABLE" ? <ProductVariants tenantId={tenantId} product={product} locked={draftDirty} onSaved={saved} onReload={reload} /> : null}
      </div> : <>
        <h2>Produtos cadastrados</h2>
        {products.length ? <ul className="product-list">{products.map(item => <li className="product-card" key={item.id}>
          <h3>{item.name}</h3><p className="product-sku">{item.kind === "VARIABLE" ? `${item.variants.length} combinações` : <>SKU: <span translate="no">{item.sku}</span></>}</p>
          <p className="store-status">{item.published ? store.store.status === "PUBLISHED" ? "Visível na vitrine" : "Selecionado para vitrine · Loja oculta" : "Oculto na vitrine"}</p>
          <dl className="product-values"><dt>{item.kind === "VARIABLE" ? "A partir de" : "Preço"}</dt><dd>{formatBRL(item.price)}</dd>{item.kind === "SIMPLE" ? <><dt>Custo</dt><dd>{item.cost === null ? "Não informado" : formatBRL(item.cost)}</dd></> : null}</dl>
          <Link href={`${storePath}/produtos/${encodeURIComponent(item.id)}`} aria-label={`Editar produto ${item.name}`}>Editar produto</Link>
          <Link className="product-stock-link" href={`${storePath}/produtos/${encodeURIComponent(item.id)}/estoque`} aria-label={`Estoque de ${item.name}`}>Estoque</Link>
        </li>)}</ul> : <p className="feedback-empty">Nenhum produto cadastrado. Preencha o formulário abaixo para adicionar o primeiro.</p>}
        <section className="store-create product-editor" aria-labelledby="product-create-title"><h2 id="product-create-title">Cadastrar produto</h2>
          <ProductForm tenantId={tenantId} onSaved={saved} />
        </section>
      </>}
    </> : null}
  </section>;
}
