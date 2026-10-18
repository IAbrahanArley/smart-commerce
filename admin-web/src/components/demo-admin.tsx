"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import PageHeader from "@/components/page-header";
import DemoProducts from "@/components/demo-products";

export default function DemoAdmin() {
  const [availableOnly, setAvailableOnly] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const toggle = useRef<HTMLButtonElement>(null);
  return <div className="demo-page landing-wrap">
    <div className="demo-banner"><strong>Demonstração do admin</strong><span>Dados fictícios · alterações não são salvas</span><Link href="/">Voltar à apresentação</Link></div>
    <div className="demo-admin-shell" data-ui>
      <aside className="demo-sidebar" onKeyDown={event => { if (event.key === "Escape") { setMenuOpen(false); toggle.current?.focus(); } }}>
        <div className="demo-sidebar-heading"><strong>Loja Exemplo</strong><button ref={toggle} className="presentation-menu" aria-expanded={menuOpen} aria-controls="demo-store-navigation" onClick={() => setMenuOpen(!menuOpen)}>Menu da loja</button></div>
        <nav id="demo-store-navigation" aria-label="Navegação da loja demonstrada" data-open={menuOpen}>
          <ul><li>Visão geral <small>Em preparação</small></li><li><Link href="/admin/produtos" aria-current="page" onClick={() => { setMenuOpen(false); toggle.current?.focus(); }}>Produtos</Link></li><li>Entrega <small>Em preparação</small></li></ul>
          <ul className="demo-secondary"><li>Minhas lojas <small>Em preparação</small></li><li>Minha conta <small>Em preparação</small></li></ul>
        </nav>
      </aside>
      <div className="demo-admin-content"><PageHeader id="demo-products-title" title="Produtos" description="Consulte o catálogo da sua loja de exemplo." />
        <label className="check-field demo-filter"><input type="checkbox" checked={availableOnly} onChange={event => setAvailableOnly(event.target.checked)} />Mostrar apenas produtos com estoque</label>
        <p className="landing-note" aria-live="polite">{availableOnly ? "3 produtos com estoque" : "4 produtos de demonstração"}. Os links abrem o mesmo produto de exemplo da loja.</p>
        <DemoProducts availableOnly={availableOnly} />
        <p>Cadastro e edição não estão disponíveis nesta demonstração.</p>
      </div>
    </div>
  </div>;
}
