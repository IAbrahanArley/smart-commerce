import type { ReactNode } from "react";
import Link from "next/link";
import AccountMenu from "@/components/account-menu";

// Public pages outside the panel (terms, privacy): same brand and account menu as the admin header.
export default function SiteLayout({ children }: { children: ReactNode }) {
  return <><header className="sticky top-0 z-30 border-b border-border bg-card">
    <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-5">
      <Link href="/" className="flex items-center gap-3 rounded-md text-foreground no-underline hover:no-underline focus-visible:ring-2 focus-visible:ring-ring">
        <span className="inline-flex size-9 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground" aria-hidden="true">SC</span>
        <span className="font-bold" translate="no">Smart Commerce</span>
      </Link>
      <AccountMenu />
    </div>
  </header>
  <main id="conteudo" className="site-main" tabIndex={-1}>{children}</main>
  <footer><span translate="no">Smart Commerce</span>
    <nav className="site-footer-links" aria-label="Documentos legais"><Link href="/termos">Termos de uso</Link><Link href="/privacidade">Política de privacidade</Link></nav>
  </footer></>;
}
