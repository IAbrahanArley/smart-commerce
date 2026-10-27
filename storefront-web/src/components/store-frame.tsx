import Link from "next/link";
import { cookies } from "next/headers";
import type { ReactNode } from "react";
import { type PublicStore, loadMenu, whatsappText } from "@/lib/public-store";
import { type MenuItem, categoryPath } from "@/lib/menu";
import { cartCookieName, parseCart } from "@/lib/cart-cookie";

// Sprint 8 (SC-044, DESIGN.md "Vitrine padrão V1"): the frame of every store page. Header with the
// store name, search and cart; the category menu (native <details>, so it opens with the keyboard
// and without JavaScript); the store footer; and the WhatsApp link when the store shows one.
export async function StoreFrame({ store, currentCategory, menu: loaded, children }: {
  store: PublicStore; currentCategory?: string; menu?: MenuItem[]; children: ReactNode;
}) {
  // Catalog pages already have the menu from the same response; other pages fetch it.
  const [jar, menu] = await Promise.all([cookies(), loaded ?? loadMenu(store.slug)]);
  // The count only reads this store's path-scoped cart cookie; prices and stock come from the quote.
  const units = parseCart(jar.get(cartCookieName(store.slug))?.value).reduce((total, line) => total + line.quantity, 0);
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  const { contactEmail, whatsapp, instagram } = store.profile;
  return <>
    <header className="store-header">
      <div className="page-width header-main">
        <Link href={storeUrl} className="store-name" translate="no" prefetch={false}>
          {/* The name stays as text next to the logo, so the image is decorative. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          {store.profile.logo ? <img className="store-logo" src={`/midia/lojas/${encodeURIComponent(store.slug)}/logo`} alt="" /> : null}
          <span>{store.name}</span>
        </Link>
        <form className="header-search" role="search" action={`${storeUrl}/busca`} method="get">
          <label htmlFor="store-search" className="visually-hidden">Buscar produtos</label>
          <input id="store-search" name="q" type="search" placeholder="O que você procura?" maxLength={100} autoComplete="off" enterKeyHint="search" />
          <button type="submit" aria-label="Buscar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true" focusable="false"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
          </button>
        </form>
        <nav aria-label="Atalhos da loja" className="header-shortcuts">
          <Link href={`${storeUrl}/carrinho`} className="cart-link" prefetch={false}>
            <svg className="icon" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M5 8h14l-1.2 11H6.2z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></svg>
            Carrinho
            {units > 0 ? <>{" "}<span className="cart-count">{units}<span className="visually-hidden"> {units === 1 ? "unidade" : "unidades"}</span></span></> : null}
          </Link>
        </nav>
      </div>
      {menu.length ? <nav className="category-menu" aria-label="Categorias">
        <div className="page-width">
          <details className="menu-compact">
            <summary>Categorias</summary>
            <MenuList items={menu} store={store.slug} current={currentCategory} />
          </details>
          <ul className="menu-bar">
            {menu.map(item => <li key={item.slug}>
              {item.children.length ? <details className="menu-group">
                <summary aria-current={item.slug === currentCategory ? "page" : undefined}>{item.name}</summary>
                <div className="menu-panel">
                  <Link href={categoryPath(store.slug, item.slug)} prefetch={false} className="menu-all">Ver tudo em {item.name}</Link>
                  <MenuList items={item.children} store={store.slug} current={currentCategory} />
                </div>
              </details> : <Link href={categoryPath(store.slug, item.slug)} prefetch={false} aria-current={item.slug === currentCategory ? "page" : undefined}>{item.name}</Link>}
            </li>)}
          </ul>
        </div>
      </nav> : null}
    </header>
    {children}
    <footer className="store-footer" aria-labelledby="store-footer-title">
      <div className="page-width store-footer-grid">
        <div>
          <h2 id="store-footer-title" className="store-footer-name" translate="no">{store.name}</h2>
        </div>
        {menu.length ? <nav aria-label="Categorias do rodapé">
          <h2>Categorias</h2>
          <ul>{menu.map(item => <li key={item.slug}><Link href={categoryPath(store.slug, item.slug)} prefetch={false}>{item.name}</Link></li>)}</ul>
        </nav> : null}
        {contactEmail || whatsapp || instagram ? <div>
          <h2>Atendimento</h2>
          <ul>
            {whatsapp ? <li><a href={`https://wa.me/${whatsapp}`} rel="noopener noreferrer" target="_blank">WhatsApp {whatsappText(whatsapp)}<span className="visually-hidden"> (abre em nova aba)</span></a></li> : null}
            {contactEmail ? <li><a href={`mailto:${contactEmail}`}>{contactEmail}</a></li> : null}
            {instagram ? <li><a href={`https://www.instagram.com/${instagram}/`} rel="noopener noreferrer" target="_blank">Instagram @{instagram}<span className="visually-hidden"> (abre em nova aba)</span></a></li> : null}
          </ul>
        </div> : null}
      </div>
    </footer>
    {whatsapp ? <a className="whatsapp-float" href={`https://wa.me/${whatsapp}`} rel="noopener noreferrer" target="_blank" aria-label={`Conversar no WhatsApp com ${store.name} (abre em nova aba)`}>
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M4 20l1.3-3.9A8 8 0 1 1 8 19z" /><path d="M9 9.5c0 3 2.5 5.5 5.5 5.5l1-1.5-2-1-1 .8a4 4 0 0 1-2-2l.8-1-1-2z" /></svg>
    </a> : null}
  </>;
}

function MenuList({ items, store, current }: { items: MenuItem[]; store: string; current?: string }) {
  return <ul className="menu-list">
    {items.map(item => <li key={item.slug}>
      <Link href={categoryPath(store, item.slug)} prefetch={false} aria-current={item.slug === current ? "page" : undefined}>{item.name}</Link>
      {item.children.length ? <MenuList items={item.children} store={store} current={current} /> : null}
    </li>)}
  </ul>;
}
