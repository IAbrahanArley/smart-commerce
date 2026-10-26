import type { ReactNode } from "react";
import Link from "next/link";
import AccountMenu from "@/components/account-menu";

export default function SiteLayout({ children }: { children: ReactNode }) {
  return <><header className="site-header">
    <p className="site-identity"><Link className="brand" href="/" translate="no">Smart Commerce</Link> <span>Administração</span></p>
    <AccountMenu />
  </header>
  <main id="conteudo" className="site-main" tabIndex={-1}>{children}</main>
  <footer>Smart Commerce · Ambiente de desenvolvimento
    <nav className="site-footer-links" aria-label="Documentos legais"><Link href="/termos">Termos de uso</Link><Link href="/privacidade">Política de privacidade</Link></nav>
  </footer></>;
}
