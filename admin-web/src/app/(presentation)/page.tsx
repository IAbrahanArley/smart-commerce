import type { Metadata } from "next";
import Link from "next/link";
import DemoProducts from "@/components/demo-products";
import PlatformPreview from "@/components/platform-preview";

export const metadata: Metadata = { title: "Smart Commerce | Sua loja e sua operação", description: "Conheça o Smart Commerce: produtos, estoque e vitrine para pequenos varejistas. Explore as demonstrações da plataforma em desenvolvimento." };

export default function LandingPage() {
  return <>
    <section className="landing-hero" aria-labelledby="landing-title">
      <div className="landing-wrap hero-copy">
      <p className="landing-intro">Smart Commerce · Para o seu próximo passo</p>
      <h1 id="landing-title">Sua loja.<br /><span>Tudo conectado.</span></h1>
      <div className="hero-bottom"><p>Organize produtos, acompanhe o estoque e prepare sua vitrine para vender online com mais clareza no dia a dia.</p>
        <div className="landing-actions"><Link className="landing-button" href="/admin/produtos">Explorar o admin <span aria-hidden="true">↗</span></Link><Link className="landing-text-link" href="/loja/produto">Conhecer a loja <span aria-hidden="true">→</span></Link></div>
      </div>
      <p className="landing-note">Produto em desenvolvimento. Explore as interfaces de demonstração.</p>
      <a className="hero-scroll" href="#visao-geral">Conheça a experiência <span aria-hidden="true">↓</span></a>
      </div>
    </section>
    <section id="visao-geral" tabIndex={-1} className="landing-overview" aria-label="Prévia do catálogo">
      <div className="landing-wrap overview-stage" data-reveal>
        <div className="overview-labels" aria-hidden="true"><span>Produtos organizados</span><span>Estoque acompanhado</span><span>Vitrine conectada</span></div>
        <div className="hero-product" data-ui><div className="hero-product-heading"><div><p>Loja Exemplo <span> / Admin</span></p><h2>Produtos</h2></div><Link href="/admin/produtos">Conhecer esta interface <span aria-hidden="true">↗</span></Link></div><DemoProducts /></div>
        <p className="landing-note overview-note">Sua operação, em uma visão mais clara. Prévia com dados fictícios.</p>
      </div>
    </section>
    <section id="recursos" tabIndex={-1} className="landing-field" aria-labelledby="resources-title"><div className="landing-wrap">
      <div className="section-heading" data-reveal><p>Menos dispersão. Mais organização.</p><h2 id="resources-title">Cada coisa<br />em seu lugar.</h2></div>
      <div className="feature-row" data-reveal><h3>Produtos organizados.</h3><div><p>Cadastro, fotos e variações para apresentar o que você vende com clareza.</p><div className="feature-sample" data-ui><strong>Camiseta essencial</strong><span>Cor: natural · Tamanhos: P, M e G</span></div></div></div>
      <div className="feature-row" data-reveal><h3>Estoque acompanhado.</h3><div><p>Entradas, ajustes e disponibilidade com histórico de movimentações.</p><div className="stock-sample" data-ui><span>Disponibilidade do exemplo</span><strong>24 unidades</strong></div></div></div>
      <div className="feature-row" data-reveal><h3>A sua vitrine online.</h3><div><p>Produtos e suas opções em uma experiência feita para o comprador explorar.</p><Link href="/loja/produto">Ver o produto na loja <span aria-hidden="true">→</span></Link></div></div>
      <div className="feature-row" data-reveal><h3>Entrega e carrinho.</h3><div><p>Retirada ou frete fixo e organização dos itens no carrinho. O pagamento ainda não está disponível.</p><div className="feature-sample" data-ui><span>Exemplo de entrega</span><strong>Retirada na loja ou frete fixo</strong></div></div></div>
      <p className="landing-note">Exemplos ilustrativos com dados fictícios. As demonstrações não salvam alterações.</p>
    </div></section>
    <section id="como-funciona" tabIndex={-1} className="landing-wrap landing-section" aria-labelledby="steps-title"><div className="section-heading" data-reveal><p>Uma rotina que faz sentido.</p><h2 id="steps-title">Comece pela sua loja.</h2></div>
      <ol className="landing-steps"><li data-reveal><h3>Configure sua loja.</h3><p>Defina o nome e as opções de entrega da sua operação.</p></li><li data-reveal><h3>Organize o catálogo.</h3><p>Reúna fotos, preços, variações e disponibilidade.</p></li><li data-reveal><h3>Publique sua vitrine.</h3><p>Apresente seus produtos online. Checkout e pagamentos seguem em desenvolvimento.</p></li></ol>
    </section>
    <section id="plataforma" tabIndex={-1} className="landing-field" aria-labelledby="platform-title"><div className="landing-wrap"><div className="section-heading" data-reveal><p>Conheça a plataforma.</p><h2 id="platform-title">Duas interfaces.<br />A mesma loja.</h2></div><PlatformPreview /></div></section>
    <section className="landing-wrap landing-section roadmap" aria-labelledby="roadmap-title"><p>Em desenvolvimento</p><h2 id="roadmap-title">Próximas<br />etapas.</h2><p>O Smart Commerce está evoluindo. Estes recursos são planejados e ainda não estão disponíveis:</p><ul><li>Checkout e pedidos</li><li>Pagamentos</li><li>Gestão financeira</li><li>Assistência por IA</li></ul></section>
    <section className="landing-closing" aria-labelledby="closing-title"><div className="landing-wrap" data-reveal><h2 id="closing-title">Explore<br />por dentro.</h2><Link className="landing-button" href="/admin/produtos">Explorar demonstração <span aria-hidden="true">↗</span></Link><p>Sem cadastro. Com espaço para explorar.</p></div></section>
  </>;
}
