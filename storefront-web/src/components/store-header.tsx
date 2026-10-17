import Link from "next/link";
import type { PublicStore } from "@/lib/public-store";

export function StoreHeader({ store }: { store: PublicStore }) {
  return (
    <header className="store-header">
      <div className="page-width header-content">
        <Link href={`/lojas/${encodeURIComponent(store.slug)}`} className="store-name" translate="no" prefetch={false}>
          {store.name}
        </Link>
        <nav aria-label="Navegação da loja">
          <Link href={`/lojas/${encodeURIComponent(store.slug)}#produtos`} prefetch={false}>Ver produtos</Link>
          <a href="#sobre-a-vitrine">Sobre a vitrine</a>
        </nav>
      </div>
    </header>
  );
}
