import type { Metadata } from "next";
import Link from "next/link";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";

export const metadata: Metadata = { title: "Componentes | Smart Commerce" };
export default function ComponentsPage() {
  return <div className="landing-wrap landing-section component-gallery">
    <PageHeader id="components-title" title="Fundamentos e componentes" description="Uma amostra dos componentes locais usados na plataforma. Os exemplos não enviam dados." />
    <section aria-labelledby="identity-title"><h2 id="identity-title">Identidade da plataforma</h2><p>Arial, Helvetica, sans-serif. Controles com raio de 4px e superfícies com raio de 8px.</p><div className="gallery-colors"><div>Texto principal<br /><strong>#172B3A</strong></div><div>Superfície de apoio<br /><strong>#E9F1FA</strong></div><div>Ação da apresentação<br /><strong>#00ABE4</strong></div></div></section>
    <section aria-labelledby="links-title"><h2 id="links-title">Ações e navegação</h2><div className="landing-actions"><Link className="landing-button" href="/admin/produtos">Explorar demonstração</Link><Link className="button-link" href="/entrar">Entrar na plataforma</Link><Link href="/">Voltar ao início</Link></div></section>
    <section aria-labelledby="status-title"><h2 id="status-title">Estados explícitos</h2><div className="landing-actions"><StatusBadge tone="positive">Disponível</StatusBadge><StatusBadge tone="neutral">Oculto</StatusBadge></div><p className="notice">Exemplo de aviso: o produto está em desenvolvimento.</p></section>
    <section aria-labelledby="field-title"><h2 id="field-title">Campo com instrução</h2><div className="form-field"><label htmlFor="sample-name">Nome do produto de exemplo</label><input id="sample-name" name="sample-name" autoComplete="off" defaultValue="Camiseta essencial" aria-describedby="sample-help" /><p id="sample-help" className="field-help">Campo demonstrativo. O conteúdo não é salvo nem enviado.</p></div></section>
  </div>;
}
