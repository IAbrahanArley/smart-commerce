import type { Metadata } from "next";
import Link from "next/link";
import LegalPage from "@/components/legal-page";
import { LEGAL, operatorText } from "@/lib/legal";

export const metadata: Metadata = { title: "Termos de uso | Smart Commerce" };

// MVP block 1d. Draft for the owner's validation, written for merchants (the platform's users).
// Not legal advice; the governing-law clause is pending the owner's decision.
export default function TermsPage() {
  return <LegalPage title="Termos de uso">
    <p>Estes termos regem o uso da Smart Commerce, plataforma para criar e administrar lojas virtuais, operada por {operatorText()}. Ao criar uma conta, você concorda com eles e com a <Link href="/privacidade">Política de privacidade</Link>.</p>

    <h2>1. O serviço</h2>
    <p>A Smart Commerce oferece, por assinatura, painel de administração, vitrine pública, catálogo, estoque, pedidos, conexão com o Mercado Pago, relatórios financeiros e, quando ligado, um assistente que explica os números da loja. O serviço evolui e funções podem ser alteradas, com aviso quando a mudança for relevante.</p>

    <h2>2. Conta</h2>
    <ul>
      <li>Você precisa de um e-mail válido e confirmado e deve ser maior de 18 anos ou representar uma empresa.</li>
      <li>Você é responsável pela senha e por tudo o que for feito com a sua conta. Avise-nos se suspeitar de acesso indevido.</li>
      <li>As informações da empresa e da loja devem ser verdadeiras.</li>
    </ul>

    <h2>3. Responsabilidades do lojista</h2>
    <ul>
      <li>Produtos, descrições, fotos, preços, estoque, entrega, trocas e atendimento são de responsabilidade da loja, que é quem vende ao comprador.</li>
      <li>A loja cumpre o Código de Defesa do Consumidor, inclusive o direito de arrependimento em compras pela internet, e as obrigações fiscais da sua atividade, como a emissão de nota fiscal.</li>
      <li>A loja é controladora dos dados dos seus compradores e deve usá-los só para o pedido e conforme a LGPD.</li>
      <li>É proibido vender produtos ilegais, falsificados ou que violem direitos de terceiros, e usar a plataforma para fraude, spam ou ataques.</li>
    </ul>

    <h2>4. Pagamentos</h2>
    <p>Os pagamentos são processados pelo Mercado Pago, na conta do próprio lojista, conectada pelo painel. O lojista aceita os termos e as tarifas do Mercado Pago diretamente com ele. A Smart Commerce não recebe, não guarda e não repassa o dinheiro das vendas, e não responde por recusas, bloqueios, estornos ou contestações decididos pelo Mercado Pago.</p>

    <h2>5. Assinatura, teste grátis e cobrança</h2>
    <ul>
      <li>A conta começa com 14 dias de teste grátis, sem cartão, com os recursos do plano Profissional. Ao final, é preciso assinar um plano para continuar fazendo alterações.</li>
      <li>Os planos (Essencial, Profissional e Escala), seus limites e preços estão na página de planos e em Plano e cobrança, no painel. Os ciclos são mensal, semestral ou anual, e o valor do ciclo é cobrado no início de cada período.</li>
      <li>A cobrança é feita no cartão de crédito pela Stripe, que processa o pagamento em página própria. A Smart Commerce não vê nem guarda os dados do cartão. A assinatura renova automaticamente até ser cancelada.</li>
      <li>Você troca de plano ou de ciclo e cancela pelo painel, em “Plano e cobrança”, e atualiza o cartão em “Cartão e recibos”. A mudança para um plano maior ou um ciclo mais longo vale na hora, com o valor proporcional cobrado no ato, que você confere antes de confirmar; a mudança para um plano menor ou um ciclo mais curto e o cancelamento valem no fim do período já pago.</li>
      <li>Você pode desistir em até 7 dias da contratação ou da renovação semestral ou anual, com reembolso integral, pelo e-mail <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a> (Código de Defesa do Consumidor, art. 49). Depois desse prazo, o cancelamento vale no fim do período pago, sem reembolso proporcional.</li>
      <li>Se uma cobrança falhar, a Stripe tenta de novo e a loja continua funcionando por 7 dias. Depois disso, ou com o teste encerrado sem assinatura, ou com a assinatura cancelada, o painel fica só para consulta e a vitrine sai do ar. Nenhum dado é apagado por isso, e tudo volta assim que o pagamento for regularizado.</li>
      <li>Se o uso passar do limite depois de mudar para um plano menor, nada é apagado: só a criação de novas lojas ou produtos fica bloqueada até voltar ao limite.</li>
    </ul>

    <h2>6. Relatórios e assistente</h2>
    <p>Relatórios financeiros e respostas do assistente são calculados com os dados registrados na plataforma e servem para apoiar decisões. Não são consultoria contábil, fiscal, jurídica ou financeira. Confira os valores com o Mercado Pago e com o seu contador antes de decisões importantes.</p>

    <h2>7. Conteúdo e propriedade</h2>
    <p>O conteúdo que você publica continua seu. Você nos autoriza a armazená-lo e exibi-lo na sua vitrine apenas para prestar o serviço. A marca, o código e o design da plataforma pertencem à operadora.</p>

    <h2>8. Disponibilidade</h2>
    <p>Trabalhamos para manter o serviço disponível e seguro, mas ele pode ter interrupções para manutenção ou por falhas de terceiros. Não garantimos funcionamento ininterrupto.</p>

    <h2>9. Suspensão e encerramento</h2>
    <p>Você pode encerrar sua conta a qualquer momento pelo e-mail <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>. Podemos suspender ou encerrar contas que violem estes termos ou a lei, com aviso quando possível. Após o encerramento, os dados seguem os prazos da Política de privacidade.</p>

    <h2>10. Limitação de responsabilidade</h2>
    <p>Na extensão permitida pela lei, a operadora não responde por lucros cessantes, perdas indiretas ou por atos da loja, do comprador ou de terceiros, como o Mercado Pago e os provedores de hospedagem.</p>

    <h2>11. Alterações e lei aplicável</h2>
    <p>Podemos atualizar estes termos. A data no topo indica a versão atual e mudanças relevantes serão avisadas com antecedência. Estes termos seguem a legislação brasileira.</p>
  </LegalPage>;
}
