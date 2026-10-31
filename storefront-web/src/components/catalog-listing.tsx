import Link from "next/link";
import { type CatalogPage, SORTS, type SearchParams, apiQuery, centsBelow, pageHref, priceHref, toggleHref } from "@/lib/catalog-page";
import { categoryPath } from "@/lib/menu";
import { formatBRL } from "@/lib/price";
import { ProductCard } from "@/components/product-card";

// SC-045: category and search pages. Filters are plain links and the order is a GET form, so the
// page works without JavaScript and every state is a shareable URL. On narrow screens the filters
// fold into a native <details> panel above the products.
export function CatalogListing({ store, base, params, catalog, title, breadcrumb }: {
  store: string; base: string; params: SearchParams; catalog: CatalogPage; title: string; breadcrumb: { name: string; href: string }[];
}) {
  const applied = apiQuery(params);
  const brands = applied.getAll("marca").flatMap(value => value.split(","));
  const options = applied.getAll("opcao");
  const pages = Math.max(1, Math.ceil(catalog.total / catalog.pageSize));
  const hasFilters = brands.length > 0 || options.length > 0 || applied.has("preco_min") || applied.has("preco_max");
  const clear = new URLSearchParams();
  if (applied.get("q")) clear.set("q", applied.get("q")!);
  const facets = catalog.facets;
  const filters = (prefix: string) => <>
    {facets.categories.length ? <section className="facet" aria-labelledby={`${prefix}-facet-categories`}>
      <h2 id={`${prefix}-facet-categories`}>Categorias</h2>
      <ul>{facets.categories.map(item => <li key={item.slug}><Link href={categoryPath(store, item.slug)} prefetch={false}>{item.name} <span className="facet-count">({item.count})</span></Link></li>)}</ul>
    </section> : null}
    {facets.brands.length ? <section className="facet" aria-labelledby={`${prefix}-facet-brands`}>
      <h2 id={`${prefix}-facet-brands`}>Marca</h2>
      <ul>{facets.brands.map(item => <li key={item.slug}><FilterLink href={toggleHref(base, params, "marca", item.slug)} active={brands.includes(item.slug)} label={item.name} count={item.count} /></li>)}</ul>
    </section> : null}
    {facets.prices.length ? <section className="facet" aria-labelledby={`${prefix}-facet-prices`}>
      <h2 id={`${prefix}-facet-prices`}>Preço</h2>
      <ul>{facets.prices.map(range => {
        const label = range.max ? `${formatBRL(range.min)} a ${formatBRL(centsBelow(range.max))}` : `A partir de ${formatBRL(range.min)}`;
        const active = applied.get("preco_min") === range.min && (applied.get("preco_max") ?? null) === (range.max ? centsBelow(range.max) : null);
        return <li key={range.min}><FilterLink href={priceHref(base, params, range)} active={active} label={label} count={range.count} /></li>;
      })}</ul>
    </section> : null}
    {facets.options.map((option, index) => <section className="facet" key={option.name} aria-labelledby={`${prefix}-facet-option-${index}`}>
      <h2 id={`${prefix}-facet-option-${index}`}>{option.name}</h2>
      <ul className="facet-chips">{option.values.map(choice => {
        const value = `${option.name}:${choice.value}`;
        return <li key={choice.value}><FilterLink href={toggleHref(base, params, "opcao", value)} active={options.includes(value)} label={choice.value} count={choice.count} chip /></li>;
      })}</ul>
    </section>)}
  </>;
  const hasFacets = facets.categories.length + facets.brands.length + facets.prices.length + facets.options.length > 0;
  return <main id="conteudo" className="page-width catalog-listing" tabIndex={-1}>
    <nav className="breadcrumbs" aria-label="Você está em">
      <ol>{breadcrumb.map((item, index) => <li key={item.href}>{index === breadcrumb.length - 1 ? <span aria-current="page">{item.name}</span> : <Link href={item.href} prefetch={false}>{item.name}</Link>}</li>)}</ol>
    </nav>
    <div className="listing-heading">
      <div>
        <h1>{title}</h1>
        <p className="listing-count" role="status">{catalog.total === 1 ? "1 produto" : `${new Intl.NumberFormat("pt-BR").format(catalog.total)} produtos`}</p>
      </div>
      <form className="listing-sort" method="get" action={base}>
        {[...applied.entries()].filter(([key]) => key !== "ordem" && key !== "pagina").map(([key, value], index) => <input key={`${key}-${index}`} type="hidden" name={key} value={value} />)}
        <label htmlFor="ordem">Ordenar por</label>
        <select id="ordem" name="ordem" defaultValue={catalog.sort}>
          {SORTS.filter(sort => sort.value !== "relevancia" || catalog.search).map(sort => <option key={sort.value} value={sort.value}>{sort.label}</option>)}
        </select>
        <button type="submit" className="button-secondary">Aplicar</button>
      </form>
    </div>
    {hasFilters ? <p className="listing-clear"><Link href={clear.size ? `${base}?${clear}` : base} prefetch={false}>Limpar filtros</Link></p> : null}
    <div className="listing-layout">
      {hasFacets ? <>
        <aside className="listing-filters" aria-label="Filtros">{filters("side")}</aside>
        <details className="listing-filters-compact">
          <summary>Filtrar produtos</summary>
          <div className="listing-filters-panel">{filters("compact")}</div>
        </details>
      </> : null}
      <section className="listing-results" aria-labelledby="listing-results-title">
        {/* SC-048: the product names (h3) sit under an h2, also when there are no filters above. */}
        <h2 id="listing-results-title" className="visually-hidden">Produtos</h2>
        {catalog.products.length === 0 ? <div className="empty-state">
          <h3>Nenhum produto encontrado</h3>
          <p>{hasFilters ? "Tente remover algum filtro." : catalog.search ? "Confira a grafia ou procure por outro termo." : "Os produtos aparecerão aqui quando forem publicados."}</p>
          {catalog.menu.length ? <ul className="empty-suggestions">{catalog.menu.map(item => <li key={item.slug}><Link href={categoryPath(store, item.slug)} prefetch={false}>{item.name}</Link></li>)}</ul> : null}
        </div> : <ul className="catalog-grid">
          {catalog.products.map(card => <li key={card.id}><ProductCard store={store} card={card} maxInstallments={catalog.maxInstallments} /></li>)}
        </ul>}
        {pages > 1 ? <nav className="listing-pages" aria-label="Páginas">
          {catalog.page > 1 ? <Link href={pageHref(base, params, catalog.page - 1)} prefetch={false}>Anterior</Link> : null}
          {pageNumbers(catalog.page, pages).map(number => number === catalog.page
            ? <span key={number} aria-current="page" className="page-current">{number}</span>
            : <Link key={number} href={pageHref(base, params, number)} prefetch={false} aria-label={`Página ${number}`}>{number}</Link>)}
          {catalog.page < pages ? <Link href={pageHref(base, params, catalog.page + 1)} prefetch={false}>Próxima</Link> : null}
        </nav> : null}
      </section>
    </div>
  </main>;
}

function FilterLink({ href, active, label, count, chip }: { href: string; active: boolean; label: string; count: number; chip?: boolean }) {
  return <Link href={href} prefetch={false} className={chip ? "facet-chip" : "facet-link"} aria-current={active ? "true" : undefined}>
    {active ? <span className="visually-hidden">Remover filtro: </span> : null}{label} <span className="facet-count">({count})</span>
  </Link>;
}

/** Up to 5 page numbers around the current one. */
function pageNumbers(current: number, total: number): number[] {
  const start = Math.max(1, Math.min(current - 2, total - 4));
  return Array.from({ length: Math.min(5, total) }, (_, index) => start + index);
}
