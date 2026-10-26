"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useRef, useState } from "react";

export default function PresentationNav() {
  const pathname = usePathname();
  return <Navigation key={pathname} />;
}

function Navigation() {
  const [open, setOpen] = useState(false);
  const toggle = useRef<HTMLButtonElement>(null);
  return <header className="presentation-header" onKeyDown={event => {
    if (event.key === "Escape" && open) { setOpen(false); toggle.current?.focus(); }
  }}>
    <div className="landing-wrap presentation-header-inner">
      <Link className="brand" href="/" translate="no">Smart Commerce</Link>
      <button ref={toggle} className="presentation-menu" aria-expanded={open} aria-controls="presentation-navigation" onClick={() => setOpen(!open)}>Menu {open ? "−" : "+"}</button>
      <nav id="presentation-navigation" aria-label="Apresentação da plataforma" data-open={open} onClick={event => {
        if ((event.target as HTMLElement).closest("a")) setOpen(false);
      }}>
        <Link href="/#recursos">Recursos</Link><Link href="/#como-funciona">Como funciona</Link><Link href="/#plataforma">Conheça a plataforma</Link><Link href="/#planos">Planos</Link>
        <Link href="/entrar">Entrar</Link><Link className="landing-button" href="/cadastro">Criar conta <span aria-hidden="true">↗</span></Link>
      </nav>
    </div>
  </header>;
}
