import type { Metadata } from "next";
import Link from "next/link";
import DemoProducts from "@/components/demo-products";
import PlatformPreview from "@/components/platform-preview";
import PricingPlans from "@/components/pricing-plans";

export const metadata: Metadata = { title: "Smart Commerce | Sua loja e sua operação", description: "Smart Commerce: catálogo, estoque único para vitrine, balcão, Mercado Livre e Shopee, pagamentos pelo Mercado Pago, caixa, clientes, financeiro e alertas para pequenos varejistas." };

// Operation after the sale, as delivered in the MVP.
const operation = [
  { title: "Financeiro do período.", text: "Receita, custo dos produtos vendidos, despesas e margem, separando o que foi vendido, o que falta receber e o que já está em caixa." },
  { title: "Contas a pagar e a receber.", text: "Gaveta, banco e Mercado Pago no mesmo lugar, com contas a pagar parceladas, crediário e recebíveis de cada canal." },
  { title: "Livro de vendas.", text: "Vendas online, da loja física e dos marketplaces no mesmo relatório diário, por canal e forma de pagamento." },
  { title: "Fluxo de caixa projetado.", text: "Saldo previsto para 30, 60 e 90 dias, com alerta de contas perto do vencimento." },
  { title: "Painel da loja.", text: "Vendas, ticket médio, estoque baixo e itens parados, sempre com o período à vista." },
  { title: "Alertas objetivos.", text: "Estoque, pedidos, pagamentos e margem, calculados a partir dos dados da própria loja." },
  { title: "Clientes da loja.", text: "Ficha com histórico de compras online e presenciais, segmentos e notas de atendimento." },
  { title: "Assistente com IA.", text: "Pergunte em português sobre os números da loja e veja a fonte de cada valor. Em liberação gradual." },
  { title: "Equipe com o acesso certo.", text: "Convide funcionários por e-mail para o caixa, as vendas e o atendimento, sem abrir os números gerenciais." },
];

export default function LandingPage() {
  return <>
    <section className="landing-hero" aria-labelledby="landing-title">
      <div className="landing-wrap hero-copy">
      <p className="landing-intro">Smart Commerce · Online e na loja física</p>
      <h1 id="landing-title">Sua loja.<br /><span>Tudo conectado.</span></h1>
      <div className="hero-bottom"><p>Venda pela vitrine, no balcão e nos marketplaces com um só estoque, receba pelo Mercado Pago, controle o caixa e acompanhe o financeiro da loja em um só lugar.</p>
        <div className="landing-actions"><Link className="landing-button" href="/cadastro">Criar minha loja <span aria-hidden="true">↗</span></Link><Link className="landing-text-link" href="/entrar">Já tenho conta <span aria-hidden="true">→</span></Link></div>
      </div>
      <p className="landing-note">Os pagamentos vão direto para a sua conta do Mercado Pago.</p>
      <a className="hero-scroll" href="#visao-geral">Conheça a experiência <span aria-hidden="true">↓</span></a>
      </div>
    </section>
    <section id="visao-geral" tabIndex={-1} className="landing-overview" aria-label="Prévia do catálogo">
      <div className="landing-wrap overview-stage" data-reveal>
        <div className="overview-labels" aria-hidden="true"><span>Catálogo organizado</span><span>Um só estoque</span><span>Todos os canais</span></div>
        <div className="hero-product" data-ui><div className="hero-product-heading"><div><p>Loja Exemplo <span> / Admin</span></p><h2>Produtos</h2></div><Link href="/cadastro">Começar agora <span aria-hidden="true">↗</span></Link></div><DemoProducts /></div>
        <p className="landing-note overview-note">Sua operação, em uma visão mais clara. Prévia com dados fictícios.</p>
      </div>
    </section>
    <section id="recursos" tabIndex={-1} className="landing-field" aria-labelledby="resources-title"><div className="landing-wrap">
      <div className="section-heading" data-reveal><p>Menos dispersão. Mais organização.</p><h2 id="resources-title">Cada coisa<br />em seu lugar.</h2></div>
      <div className="feature-row" data-reveal><h3>Catálogo organizado.</h3><div><p>Categorias e subcategorias, marcas, fotos, variações e preço “de/por” para apresentar o que você vende com clareza.</p><div className="feature-sample" data-ui><strong>Camiseta essencial</strong><span>Cor: natural · Tamanhos: P, M e G</span></div></div></div>
      <div className="feature-row" data-reveal><h3>Um só estoque.</h3><div><p>Entradas, ajustes e histórico de movimentações. Vendeu na vitrine, no balcão ou num marketplace, o saldo cai em todos os canais.</p><div className="stock-sample" data-ui><span>Vitrine, balcão e marketplaces</span><strong>24 unidades</strong></div></div></div>
      <div className="feature-row" data-reveal><h3>Uma vitrine completa.</h3><div><p>Página inicial com banners e seções, menu de categorias, busca com filtros, página de produto com parcelamento e páginas institucionais, montada pelo painel, sem código.</p><p className="landing-note">O comprador tem a própria conta, com pedidos e favoritos, e acompanha cada entrega.</p></div></div>
      <div className="feature-row" data-reveal><h3>Pedidos e pagamento.</h3><div><p>Carrinho, retirada ou frete fixo e pagamento pelo Mercado Pago, com pedidos e reembolsos no painel.</p><div className="feature-sample" data-ui><span>Exemplo de entrega</span><strong>Retirada na loja ou frete fixo</strong></div></div></div>
      <div className="feature-row" data-reveal><h3>Mercado Livre e Shopee.</h3><div><p>Conecte suas contas e veja os pedidos dos marketplaces no mesmo painel, com quanto sobrou de cada venda depois da tarifa.</p><div className="feature-sample" data-ui><span>Exemplo de pedido</span><strong>Mercado Livre · 1 item · estoque atualizado em todos os canais</strong></div></div></div>
      <div className="feature-row" data-reveal><h3>Balcão e caixa.</h3><div><p>Abertura e fechamento do caixa com contagem cega, sangria e suprimento. Registre vendas da loja física à mão, importe uma planilha CSV ou venda no crediário em parcelas.</p><div className="feature-sample" data-ui><span>Exemplo de venda no balcão</span><strong>2 itens · Pix · baixa no estoque na hora</strong></div></div></div>
      <p className="landing-note">Exemplos ilustrativos com dados fictícios.</p>
    </div></section>
    <section id="como-funciona" tabIndex={-1} className="landing-wrap landing-section" aria-labelledby="steps-title"><div className="section-heading" data-reveal><p>Uma rotina que faz sentido.</p><h2 id="steps-title">Comece pela sua loja.</h2></div>
      <ol className="landing-steps"><li data-reveal><h3>Configure sua loja.</h3><p>Defina nome, logo, contatos e entrega, conecte sua conta do Mercado Pago e convide sua equipe.</p></li><li data-reveal><h3>Organize o catálogo.</h3><p>Reúna categorias, fotos, preços, variações e disponibilidade.</p></li><li data-reveal><h3>Venda em todo lugar.</h3><p>Publique a vitrine, conecte os marketplaces, registre as vendas do balcão e acompanhe tudo no mesmo painel.</p></li></ol>
    </section>
    <section id="plataforma" tabIndex={-1} className="landing-field" aria-labelledby="platform-title"><div className="landing-wrap"><div className="section-heading" data-reveal><p>Conheça a plataforma.</p><h2 id="platform-title">Duas interfaces.<br />A mesma loja.</h2></div><PlatformPreview /></div></section>
    <section id="operacao" tabIndex={-1} className="landing-wrap landing-section" aria-labelledby="operation-title">
      <div className="section-heading" data-reveal><p>Para a rotina da loja.</p><h2 id="operation-title">Do pedido<br />ao financeiro.</h2></div>
      <ul className="operation-grid">{operation.map(item => <li key={item.title} data-reveal><h3>{item.title}</h3><p>{item.text}</p></li>)}</ul>
    </section>
    <section id="planos" tabIndex={-1} className="landing-wrap landing-section pricing-section" aria-labelledby="pricing-title">
      <div className="section-heading" data-reveal><p>Planos para cada fase da loja.</p><h2 id="pricing-title">Comece grátis.<br />Cresça no seu ritmo.</h2></div>
      <PricingPlans />
    </section>
    <section className="landing-closing" aria-labelledby="closing-title"><div className="landing-wrap" data-reveal><h2 id="closing-title">Comece<br />sua loja.</h2><Link className="landing-button" href="/cadastro">Criar conta <span aria-hidden="true">↗</span></Link><p>14 dias grátis. Você só precisa de um e-mail para começar.</p></div></section>
  </>;
}
