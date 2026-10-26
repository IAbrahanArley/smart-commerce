import type { Metadata } from "next";
import { LEGAL, legalDraft, operatorText, updatedText } from "@/lib/legal";

export const metadata: Metadata = { title: "Privacidade" };

// MVP block 1d: privacy notice for shoppers. Draft for the owner's validation. Each store controls
// its shoppers' data; the platform processes it on the store's behalf.
export default function PrivacyPage() {
  return (
    <main id="conteudo" className="notice-page legal-page" tabIndex={-1}>
      <h1>Privacidade nas vitrines</h1>
      <p className="legal-updated">{updatedText()}</p>
      {legalDraft() ? <p className="legal-draft" role="note"><strong>Rascunho em validação.</strong> Este texto ainda será revisado e pode mudar antes do lançamento.</p> : null}
      <p>As vitrines deste endereço são de lojas independentes que usam a plataforma Smart Commerce, operada por {operatorText()}.</p>

      <h2>Quem cuida dos seus dados</h2>
      <p>A loja em que você compra é a controladora dos seus dados, nos termos da LGPD (Lei nº 13.709/2018). A Smart Commerce trata esses dados em nome da loja, só para o seu pedido. Os contatos da loja aparecem na página dela, em &ldquo;Fale com a loja&rdquo;.</p>

      <h2>O que coletamos</h2>
      <ul>
        <li>no pedido: nome, e-mail, telefone e, se você escolher entrega, o endereço. Não pedimos CPF;</li>
        <li>no pagamento: nada. Cartão, Pix e outros dados de pagamento são informados diretamente ao Mercado Pago, que processa o pagamento para a loja;</li>
        <li>cookies necessários: o carrinho e o acompanhamento do pedido. Não usamos cookies de publicidade nem de rastreamento.</li>
      </ul>

      <h2>Para que usamos</h2>
      <p>Para registrar, cobrar, entregar o pedido e falar com você sobre ele (execução do contrato de compra), e para cumprir obrigações legais da loja. Seus dados não são compartilhados com outras lojas nem usados para publicidade.</p>

      <h2>Por quanto tempo</h2>
      <p>Se o pedido não for pago, seus dados pessoais são anonimizados em até 90 dias. Pedidos pagos ficam guardados pelo prazo exigido por lei, como os prazos fiscais.</p>

      <h2>Seus direitos</h2>
      <p>Você pode pedir acesso, correção ou eliminação dos seus dados, entre outros direitos do art. 18 da LGPD. Fale primeiro com a loja. Se precisar, escreva também para a plataforma: <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>. Você pode ainda reclamar à Autoridade Nacional de Proteção de Dados (ANPD).</p>

      <h2>Compras pela internet</h2>
      <p>Trocas, devoluções, entrega e o direito de arrependimento (art. 49 do Código de Defesa do Consumidor) são tratados com a loja que vendeu o produto.</p>
    </main>
  );
}
