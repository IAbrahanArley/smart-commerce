import Link from "next/link";
import { cookies } from "next/headers";
import type { PublicStore } from "@/lib/public-store";
import { cartCookieName, parseCart } from "@/lib/cart-cookie";

// The count only reads this store's path-scoped cart cookie; prices and stock always come from the quote.
export async function StoreHeader({ store }: { store: PublicStore }) {
  const units = parseCart((await cookies()).get(cartCookieName(store.slug))?.value).reduce((total, line) => total + line.quantity, 0);
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  return (
    <header className="store-header">
      <div className="page-width header-content">
        <Link href={storeUrl} className="store-name" translate="no" prefetch={false}>
          {/* The name stays as text next to the logo, so the image is decorative. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          {store.profile.logo ? <img className="store-logo" src={`/midia/lojas/${encodeURIComponent(store.slug)}/logo`} alt="" /> : null}
          <span>{store.name}</span>
        </Link>
        <nav aria-label="Navegação da loja">
          <Link href={`${storeUrl}#produtos`} prefetch={false}>Ver produtos</Link>
          <a href="#sobre-a-vitrine">Sobre a vitrine</a>
          <Link href={`${storeUrl}/carrinho`} className="cart-link" prefetch={false}>
            <svg className="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
              <path d="M3 4h2l2 11h11l2-8H6.5" /><circle cx="9" cy="19" r="1.5" /><circle cx="17" cy="19" r="1.5" />
            </svg>
            Carrinho
            {units > 0 ? <>{" "}<span className="cart-count">{units}<span className="visually-hidden"> {units === 1 ? "unidade" : "unidades"}</span></span></> : null}
          </Link>
        </nav>
      </div>
    </header>
  );
}
