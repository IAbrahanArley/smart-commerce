"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { coverUrl, formatBRL, photoCountText, type Product } from "@/lib/catalog";
import type { TenantStore } from "@/lib/tenant-api";
import ProductForm from "@/components/product-form";
import ProductImages from "@/components/product-images";
import ProductVariants from "@/components/product-variants";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import { Loading } from "@/components/ui/loading";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { type Category, parseCategories } from "@/lib/categories";
import { EMPTY_FILTERS, filterProducts, filtersActive, filtersQuery, NO_CATEGORY, type ProductFilters, type ProductSort, resultText, type Visibility } from "@/lib/product-filters";

export default function Catalog({ tenantId, productId, view, created = false, initialFilters = EMPTY_FILTERS }: { tenantId: string; productId?: string; view: "list" | "new" | "edit"; created?: boolean; initialFilters?: ProductFilters }) {
  const router = useRouter();
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [store, setStore] = useState<TenantStore | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [product, setProduct] = useState<Product | undefined>();
  const [attempt, setAttempt] = useState(0);
  const [notice, setNotice] = useState(created ? "Produto cadastrado no catálogo interno. Ele começa oculto na vitrine. Inclua fotos e registre o estoque quando quiser." : "");
  const [draftDirty, setDraftDirty] = useState(false);
  const [filters, setFilterState] = useState<ProductFilters>(initialFilters);
  // The URL reflects the filters, so a reload or a shared link keeps them (Next keeps its router in sync).
  function setFilters(update: ProductFilters | ((current: ProductFilters) => ProductFilters)) {
    const next = typeof update === "function" ? update(filters) : update;
    setFilterState(next);
    window.history.replaceState(null, "", `${window.location.pathname}${filtersQuery(next)}`);
  }
  const [categories, setCategories] = useState<Category[]>([]);
  const noticeRef = useRef<HTMLParagraphElement>(null);
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  const apiPath = `/api/tenants/${encodeURIComponent(tenantId)}`;
  const title = view === "edit" ? product?.name ?? "Editar produto" : view === "new" ? "Novo produto" : "Produtos";

  useEffect(() => {
    const controller = new AbortController();
    const productPath = view === "edit" && productId ? `${apiPath}/products/${encodeURIComponent(productId)}` : view === "list" ? `${apiPath}/products` : null;
    Promise.all([apiRequest(apiPath, { signal: controller.signal }), productPath ? apiRequest(productPath, { signal: controller.signal }) : Promise.resolve(null)])
      .then(async ([storeResponse, productResponse]) => {
        const responses = productResponse ? [storeResponse, productResponse] : [storeResponse];
        if (responses.some(response => response.status === 401)) { setState("signed-out"); return; }
        if (responses.some(response => response.status === 403 || response.status === 404)) { setState("missing"); return; }
        if (responses.some(response => !response.ok)) throw new Error();
        const [storeData, productData] = await Promise.all([storeResponse.json(), productResponse?.json()]);
        if (controller.signal.aborted) return;
        setStore(storeData);
        if (view === "edit") setProduct(productData); else if (view === "list") setProducts(productData);
        setState("ready");
      }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [apiPath, productId, view, attempt]);

  // Categories only feed the list filter; without them the filter is simply not offered.
  useEffect(() => {
    if (view !== "list") return;
    const controller = new AbortController();
    apiRequest(`${apiPath}/categories`, { signal: controller.signal })
      .then(async response => { const parsed = response.ok ? parseCategories(await response.json()) : null; if (parsed && !controller.signal.aborted) setCategories(parsed); })
      .catch(() => undefined);
    return () => controller.abort();
  }, [apiPath, view]);

  useEffect(() => { if (notice && state === "ready") noticeRef.current?.focus(); }, [notice, product, state]);

  function saved(savedProduct: Product, customNotice?: string) {
    if (view === "new") { router.push(`${storePath}/produtos/${encodeURIComponent(savedProduct.id)}?criado=1`); return; }
    setProduct(savedProduct);
    setNotice(customNotice ?? "Alterações salvas.");
  }
  function reload() { setState("loading"); setNotice(""); setDraftDirty(false); setAttempt(current => current + 1); }
  function photos(images: { id: string; alt: string }[]) {
    setProduct(current => current ? { ...current, coverImageId: images[0]?.id ?? null, coverImageAlt: images[0]?.alt ?? null, imageCount: images.length } : current);
  }
  function cover(item: Product, size: number) {
    const src = coverUrl(tenantId, item);
    // Decorative next to the product name; the photo's own alt text lives in the photo manager.
    return src ? <Image src={src} alt="" width={size} height={size} unoptimized loading="lazy" className="product-thumb" />
      : <span className="product-thumb product-thumb-empty" style={{ width: size, height: size }}>Sem foto</span>;
  }
  function visibility(item: Product) {
    if (!item.published) return <StatusBadge tone="neutral">Oculto na vitrine</StatusBadge>;
    return store?.store.status === "PUBLISHED" ? <StatusBadge tone="positive">Visível na vitrine</StatusBadge> : <StatusBadge tone="neutral">Selecionado · Loja oculta</StatusBadge>;
  }

  const shown = filterProducts(products, filters);
  return <section className="page" aria-labelledby="catalog-title">
    <PageHeader id="catalog-title" title={title}
      breadcrumbs={view === "list" ? undefined : [{ href: `${storePath}/produtos`, label: "Produtos" }]}
      description={state === "ready" && store ? <>Loja: <strong translate="no">{store.store.name}</strong></> : undefined}
      action={view === "list" && state === "ready" ? <span className="form-actions"><Link className="button-link button-secondary" href={`${storePath}/categorias`}>Categorias</Link><Link className="button-link" href={`${storePath}/produtos/novo`}>Novo produto</Link></span> : undefined} />
    {state === "loading" ? <Loading context="catálogo" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar os produtos.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Produto ou loja indisponível. Volte para suas lojas e escolha uma loja à qual você tem acesso.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar o catálogo. Tente novamente.</p><button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar catálogo novamente</button></> : null}
    {state === "ready" && store ? <>
      {notice ? <p ref={noticeRef} tabIndex={-1} role="status" className="feedback feedback-success">{notice}</p> : null}
      {view === "edit" && product ? <div className="product-workspace">
        <ProductForm key={`${product.id}-${attempt}`} tenantId={tenantId} product={product} storePublished={store.store.status === "PUBLISHED"} onSaved={saved} onDirtyChange={setDraftDirty} onConflict={reload}
          aside={<><section className="side-card product-photos-card" aria-labelledby="product-photos-card-title"><h2 id="product-photos-card-title">Fotos</h2>
            <div className="product-photos-summary">{cover(product, 72)}<p>{product.imageCount > 0 ? <>{photoCountText(product.imageCount)}. A primeira é a capa na vitrine.</> : "Sem foto. Produtos com foto ficam mais fáceis de reconhecer na vitrine."}</p></div>
            <a className="back-link" href="#fotos">{product.imageCount > 0 ? "Gerenciar fotos" : "Adicionar fotos"}</a></section>
            <section className="side-card" aria-labelledby="product-stock-title"><h2 id="product-stock-title">Estoque</h2>
            <p className="field-help">{product.kind === "VARIABLE" ? "O saldo é separado por combinação." : "Consulte o saldo e registre entradas ou ajustes."}</p>
            <Link className="back-link" href={`${storePath}/produtos/${encodeURIComponent(product.id)}/estoque`}>Consultar e movimentar estoque</Link></section></>} />
        <ProductImages tenantId={tenantId} productId={product.id} version={product.version} locked={draftDirty} onVersion={version => setProduct(current => current ? { ...current, version } : current)} onPhotos={photos} onReload={reload} />
        {product.kind === "VARIABLE" ? <ProductVariants tenantId={tenantId} product={product} locked={draftDirty} onSaved={saved} onReload={reload} /> : null}
      </div> : null}
      {view === "new" ? <div className="product-new">
        <p className="notice">Cadastrar um produto não altera o estoque. Ele começa oculto na vitrine{store.store.status === "PUBLISHED" ? "" : ", e a loja também está oculta"}.</p>
        <ProductForm tenantId={tenantId} onSaved={saved} />
      </div> : null}
      {view === "list" ? <>
        <p className="notice">{store.store.status === "PUBLISHED" ? "Sua vitrine está publicada. Somente os produtos selecionados individualmente ficam visíveis para consulta pública." : "Sua vitrine está oculta. Você pode selecionar produtos agora; eles só ficarão visíveis quando publicar a loja."}</p>
        {products.length ? <div className="table-toolbar" role="search" aria-label="Filtrar produtos">
          <SearchInput className="min-w-[min(100%,16rem)] flex-[2_1_16rem]" value={filters.query} onChange={event => setFilters(current => ({ ...current, query: event.target.value }))} placeholder="Buscar por nome ou SKU…" aria-label="Buscar por nome ou SKU" />
          {categories.length ? <Select className="flex-[1_1_11rem]" aria-label="Categoria" value={filters.category} onChange={event => setFilters(current => ({ ...current, category: event.target.value }))}>
            <option value="">Todas as categorias</option>
            {categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
            <option value={NO_CATEGORY}>Sem categoria</option>
          </Select> : null}
          <Select className="flex-[1_1_11rem]" aria-label="Vitrine" value={filters.visibility} onChange={event => setFilters(current => ({ ...current, visibility: event.target.value as Visibility }))}>
            <option value="all">Todos na vitrine</option>
            <option value="visible">Selecionados para a vitrine</option>
            <option value="hidden">Ocultos na vitrine</option>
          </Select>
          <Select className="flex-[1_1_11rem]" aria-label="Ordenar" value={filters.sort} onChange={event => setFilters(current => ({ ...current, sort: event.target.value as ProductSort }))}>
            <option value="default">Ordem de cadastro</option>
            <option value="name">Nome (A–Z)</option>
            <option value="price-asc">Menor preço</option>
            <option value="price-desc">Maior preço</option>
          </Select>
          {filtersActive(filters) ? <Button variant="ghost" size="sm" onClick={() => setFilters(EMPTY_FILTERS)}>Limpar filtros</Button> : null}
        </div> : null}
        {products.length && !shown.length ? <div className="empty-panel" role="status"><h2>Nenhum produto encontrado</h2><p>Nenhum produto corresponde aos filtros escolhidos.</p><Button variant="outline" onClick={() => setFilters(EMPTY_FILTERS)}>Limpar filtros</Button></div> : null}
        {shown.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
          <caption><span aria-live="polite">Produtos cadastrados ({resultText(shown.length, products.length)})</span></caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Produto</th><th role="columnheader" scope="col">SKU</th><th role="columnheader" scope="col" className="numeric">Preço</th><th role="columnheader" scope="col" className="numeric">Custo</th><th role="columnheader" scope="col">Vitrine</th><th role="columnheader" scope="col">Estoque</th></tr></thead>
          <tbody role="rowgroup">{shown.map(item => <tr role="row" key={item.id}>
            <th role="rowheader" scope="row"><span className="product-cell">{cover(item, 48)}<span className="product-cell-text"><Link href={`${storePath}/produtos/${encodeURIComponent(item.id)}`}>{item.name}</Link>{item.imageCount > 0 ? <span className="cell-hint">{photoCountText(item.imageCount)}</span> : null}</span></span></th>
            <td role="cell" data-label="SKU">{item.kind === "VARIABLE" ? `${item.variants.length} combinações` : <span translate="no">{item.sku}</span>}</td>
            <td role="cell" data-label="Preço" className="numeric">{item.kind === "VARIABLE" ? <span className="cell-hint">a partir de </span> : null}{formatBRL(item.price)}</td>
            <td role="cell" data-label="Custo" className="numeric">{item.kind === "VARIABLE" ? <span className="cell-hint">por combinação</span> : item.cost === null ? "Não informado" : formatBRL(item.cost)}</td>
            <td role="cell" data-label="Vitrine">{visibility(item)}</td>
            <td role="cell" data-label="Estoque"><Link href={`${storePath}/produtos/${encodeURIComponent(item.id)}/estoque`} aria-label={`Estoque de ${item.name}`}>Estoque</Link></td>
          </tr>)}</tbody>
        </table></div> : null}
        {!products.length ? <div className="empty-panel"><h2>Nenhum produto cadastrado</h2><p>Cadastre o primeiro produto para montar o catálogo e escolher o que aparece na vitrine.</p><Link className="button-link" href={`${storePath}/produtos/novo`}>Cadastrar primeiro produto</Link></div> : null}
      </> : null}
    </> : null}
  </section>;
}
