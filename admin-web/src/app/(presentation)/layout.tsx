import type { ReactNode } from "react";
import Link from "next/link";
import PresentationNav from "@/components/presentation-nav";
import PresentationShell from "@/components/presentation-shell";
import "./presentation.css";
import "./landing.css";

export default function PresentationLayout({ children }: { children: ReactNode }) {
  return <PresentationShell>
    <PresentationNav />
    <main id="conteudo" tabIndex={-1}>{children}</main>
    <footer className="presentation-footer"><div className="landing-wrap">
      <Link className="brand" href="/" translate="no">Smart Commerce</Link>
      <nav aria-label="Rodapé"><Link href="/">Início</Link><Link href="/admin/produtos">Demonstração do admin</Link><Link href="/loja/produto">Demonstração da loja</Link><Link href="/componentes">Componentes</Link></nav>
      <p>Produto em desenvolvimento. Demonstrações com dados fictícios.</p>
    </div></footer>
  </PresentationShell>;
}
