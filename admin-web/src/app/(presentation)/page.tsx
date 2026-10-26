import type { Metadata } from "next";
import Link from "next/link";
import DemoProducts from "@/components/demo-products";
import PlatformPreview from "@/components/platform-preview";

export const metadata: Metadata = { title: "Smart Commerce | Sua loja e sua operação", description: "Smart Commerce: produtos, estoque, vitrine, pagamentos pelo Mercado Pago e financeiro para pequenos varejistas." };

export default function LandingPage() {
  return <>
    <section className="landing-hero" aria-labelledby="landing-title">
      <div className="landing-wrap hero-copy">
      <p className="landing-intro">Smart Commerce · Para o seu próximo passo</p>
      <h1 id="landing-title">Sua loja.<br /><span>Tudo conectado.</span></h1>
      <div className="hero-bottom"><p>Organize produtos, acompanhe o estoque, venda pela sua vitrine e receba pelo Mercado Pago, com o financeiro da loja em um só lugar.</p>
        <div className="landing-actions"><Link className="landing-button" href="/cadastro">Criar minha loja <span aria-hidden="true">↗</span></Link><Link className="landing-text-link" href="/entrar">Já tenho conta <span aria-hidden="true">→</span></Link></div>
      </div>
      <p className="landing-note">Os pagamentos vão direto para a sua conta do Mercado Pago.</p>
      <a className="hero-scroll" href="#visao-geral">Conheça a experiência <span aria-hidden="true">↓</span></a>
      </div>
    </section>
    <section id="visao-geral" tabIndex={-1} className="landing-overview" aria-label="Prévia do catálogo">
      <div className="landing-wrap overview-stage" data-reveal>
        <div className="overview-labels" aria-hidden="true"><span>Produtos organizados</span><span>Estoque acompanhado</span><span>Vitrine conectada</span></div>
        <div className="hero-product" data-ui><div className="hero-product-heading"><div><p>Loja Exemplo <span> / Admin</span></p><h2>Produtos</h2></div><Link href="/cadastro">Começar agora <span aria-hidden="true">↗</span></Link></div><DemoProducts /></div>
        <p className="landing-note overview-note">Sua operação, em uma visão mais clara. Prévia com dados fictícios.</p>
      </div>
    </section>
    <section id="recursos" tabIndex={-1} className="landing-field" aria-labelledby="resources-title"><div className="landing-wrap">
      <div className="section-heading" data-reveal><p>Menos dispersão. Mais organização.</p><h2 id="resources-title">Cada coisa<br />em seu lugar.</h2></div>
      <div className="feature-row" data-reveal><h3>Produtos organizados.</h3><div><p>Cadastro, fotos e variações para apresentar o que você vende com clareza.</p><div className="feature-sample" data-ui><strong>Camiseta essencial</strong><span>Cor: natural · Tamanhos: P, M e G</span></div></div></div>
      <div className="feature-row" data-reveal><h3>Estoque acompanhado.</h3><div><p>Entradas, ajustes e disponibilidade com histórico de movimentações.</p><div className="stock-sample" data-ui><span>Disponibilidade do exemplo</span><strong>24 unidades</strong></div></div></div>
      <div className="feature-row" data-reveal><h3>A sua vitrine online.</h3><div><p>Produtos e suas opções em uma experiência feita para o comprador explorar.</p><p className="landing-note">Com categorias, logo e contatos da sua loja.</p></div></div>
      <div className="feature-row" data-reveal><h3>Pedidos e pagamento.</h3><div><p>Carrinho, retirada ou frete fixo e pagamento pelo Mercado Pago, com pedidos e reembolsos no painel.</p><div className="feature-sample" data-ui><span>Exemplo de entrega</span><strong>Retirada na loja ou frete fixo</strong></div></div></div>
      <p className="landing-note">Exemplos ilustrativos com dados fictícios.</p>
    </div></section>
    <section id="como-funciona" tabIndex={-1} className="landing-wrap landing-section" aria-labelledby="steps-title"><div className="section-heading" data-reveal><p>Uma rotina que faz sentido.</p><h2 id="steps-title">Comece pela sua loja.</h2></div>
      <ol className="landing-steps"><li data-reveal><h3>Configure sua loja.</h3><p>Defina nome, logo, contatos e entrega, e conecte sua conta do Mercado Pago.</p></li><li data-reveal><h3>Organize o catálogo.</h3><p>Reúna fotos, preços, variações e disponibilidade.</p></li><li data-reveal><h3>Publique sua vitrine.</h3><p>Compartilhe o endereço da vitrine e receba pedidos pagos pelo Mercado Pago.</p></li></ol>
    </section>
    <section id="plataforma" tabIndex={-1} className="landing-field" aria-labelledby="platform-title"><div className="landing-wrap"><div className="section-heading" data-reveal><p>Conheça a plataforma.</p><h2 id="platform-title">Duas interfaces.<br />A mesma loja.</h2></div><PlatformPreview /></div></section>
    <section className="landing-wrap landing-section roadmap" aria-labelledby="roadmap-title"><p>Para a rotina da loja</p><h2 id="roadmap-title">Do pedido<br />ao financeiro.</h2><p>Depois da venda, o painel acompanha a operação:</p><ul><li>Pedidos e reembolsos</li><li>Receita, custos e margem</li><li>Despesas da loja</li><li>Alertas de estoque e pedidos</li></ul></section>
    <section className="landing-closing" aria-labelledby="closing-title"><div className="landing-wrap" data-reveal><h2 id="closing-title">Comece<br />sua loja.</h2><Link className="landing-button" href="/cadastro">Criar conta <span aria-hidden="true">↗</span></Link><p>Você só precisa de um e-mail para começar.</p></div></section>
  </>;
}
