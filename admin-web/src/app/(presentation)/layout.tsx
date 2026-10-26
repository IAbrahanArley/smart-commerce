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
      <nav aria-label="Rodapé"><Link href="/">Início</Link><Link href="/entrar">Entrar</Link><Link href="/cadastro">Criar conta</Link><Link href="/termos">Termos de uso</Link><Link href="/privacidade">Política de privacidade</Link></nav>
      <p>Os exemplos desta página usam dados fictícios.</p>
    </div></footer>
  </PresentationShell>;
}
