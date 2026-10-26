import type { Metadata } from "next";
import Link from "next/link";
import LegalPage from "@/components/legal-page";
import { LEGAL, operatorText } from "@/lib/legal";

export const metadata: Metadata = { title: "Política de privacidade | Smart Commerce" };

// MVP block 1d. Draft for the owner's validation: it describes what the platform does today
// (docs/quality/MVP-1D-LEGAL.md lists the source of each statement). Not legal advice.
export default function PrivacyPage() {
  return <LegalPage title="Política de privacidade">
    <p>Esta política explica como a Smart Commerce trata dados pessoais, de acordo com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018, LGPD).</p>

    <h2>1. Quem somos</h2>
    <p>A Smart Commerce é operada por {operatorText()}. Para assuntos de privacidade, inclusive pedidos de titulares, escreva para <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>.</p>

    <h2>2. Papéis</h2>
    <ul>
      <li><strong>Lojistas:</strong> a Smart Commerce é controladora dos dados de quem cria conta e administra lojas na plataforma.</li>
      <li><strong>Compradores:</strong> cada loja é controladora dos dados dos seus compradores. A Smart Commerce atua como operadora, tratando esses dados em nome da loja e apenas para o pedido. Pedidos sobre esses dados devem ser feitos primeiro à loja; se precisar, também atendemos pelo e-mail acima.</li>
    </ul>

    <h2>3. Dados que tratamos</h2>
    <p><strong>De lojistas:</strong></p>
    <ul>
      <li>conta: e-mail e senha, guardada só como resumo criptográfico (hash), nunca em texto;</li>
      <li>empresa e loja: nome da empresa, nome e endereço da loja e, se você quiser, contatos públicos (e-mail, WhatsApp e Instagram) e logo;</li>
      <li>operação: produtos, estoque, pedidos, despesas e relatórios financeiros da loja;</li>
      <li>Mercado Pago: credenciais de acesso da sua conta, guardadas cifradas;</li>
      <li>segurança: registros de acesso e de sessão para proteger a conta;</li>
      <li>assistente, se estiver ligado: horário, uso e resultado de cada pergunta, sem guardar o texto da pergunta nem da resposta.</li>
    </ul>
    <p><strong>De compradores, em nome da loja:</strong> nome, e-mail, telefone e, quando há entrega, o endereço. Não pedimos CPF. Os dados de pagamento (cartão, Pix etc.) são informados diretamente ao Mercado Pago e não passam pela Smart Commerce.</p>

    <h2>4. Para que usamos e com qual base legal</h2>
    <ul>
      <li>criar e manter sua conta e sua loja, processar pedidos e pagamentos: execução do contrato (art. 7º, V);</li>
      <li>enviar e-mails de confirmação, recuperação de senha e avisos da conta: execução do contrato;</li>
      <li>proteger contas, evitar abuso e fraude e manter registros de acesso: legítimo interesse e obrigação legal (art. 7º, II e IX, e Marco Civil da Internet);</li>
      <li>cumprir obrigações legais, fiscais e regulatórias: obrigação legal (art. 7º, II).</li>
    </ul>
    <p>Não vendemos dados, não usamos dados para publicidade e não criamos perfis de compradores entre lojas.</p>

    <h2>5. Com quem compartilhamos</h2>
    <ul>
      <li><strong>Mercado Pago:</strong> processa os pagamentos e reembolsos na conta do próprio lojista;</li>
      <li><strong>Brevo:</strong> envia os e-mails da plataforma;</li>
      <li><strong>Render e Vercel:</strong> hospedam a aplicação e o banco de dados;</li>
      <li><strong>Anthropic:</strong> somente quando o assistente estiver ligado para a loja. Recebe números agregados da loja e dados de catálogo (nomes e SKUs de produtos, categorias de despesa), nunca dados de compradores;</li>
      <li>autoridades, quando exigido por lei ou ordem judicial.</li>
    </ul>
    <p>Alguns desses fornecedores podem tratar dados fora do Brasil. Nesses casos, a transferência segue o art. 33 da LGPD, com as garantias contratuais oferecidas por cada fornecedor.</p>

    <h2>6. Por quanto tempo guardamos</h2>
    <ul>
      <li>cadastros não confirmados expiram em 7 dias;</li>
      <li>pedidos não pagos (expirados ou cancelados) têm os dados pessoais do comprador anonimizados 90 dias após a criação;</li>
      <li>registros de uso do assistente: 90 dias;</li>
      <li>demais dados: enquanto a conta estiver ativa e, depois, pelo prazo exigido por lei, como os prazos fiscais aplicáveis a pedidos pagos.</li>
    </ul>

    <h2>7. Seus direitos</h2>
    <p>Você pode pedir confirmação de tratamento, acesso, correção, anonimização, bloqueio ou eliminação de dados desnecessários, portabilidade, informação sobre compartilhamento e revisão do consentimento, quando ele for a base legal (art. 18 da LGPD). Escreva para <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>. Você também pode reclamar à Autoridade Nacional de Proteção de Dados (ANPD).</p>

    <h2>8. Segurança</h2>
    <p>Usamos conexão cifrada (HTTPS), senhas com hash, sessão em cookie protegido, credenciais do Mercado Pago cifradas e isolamento entre empresas: uma empresa não acessa dados de outra. Nenhuma medida elimina todo risco; se houver incidente relevante, comunicaremos os titulares e a ANPD conforme a lei.</p>

    <h2>9. Cookies</h2>
    <p>Usamos apenas cookies necessários: sessão e proteção contra falsificação de requisições no painel, e carrinho e acompanhamento do pedido na vitrine. Não usamos cookies de publicidade nem de rastreamento.</p>

    <h2>10. Alterações</h2>
    <p>Podemos atualizar esta política. A data no topo indica a versão atual; mudanças relevantes serão avisadas aos lojistas por e-mail ou no painel. Veja também os <Link href="/termos">Termos de uso</Link>.</p>
  </LegalPage>;
}
