# Bloco 1d do MVP — Páginas legais: validação

Data: 2026-10-26. Base: bloco 1d do [RELEASE_PLAN.md](../product/RELEASE_PLAN.md) e os requisitos de privacidade e LGPD do [PRD.md](../product/PRD.md).

**Estado: estrutura pronta, texto em rascunho.** O texto ainda precisa da validação do responsável e, de preferência, de revisão jurídica (bloco 4). Enquanto a razão social e o CNPJ não forem preenchidos, as páginas mostram "Rascunho em validação".

## Decisões do responsável (2026-10-26)

- **Operadora:** a plataforma é operada por uma empresa com CNPJ. A razão social e o CNPJ ainda serão informados.
- **Contato:** o e-mail de contato de privacidade e dos termos é `abrahanarley@gmail.com`, publicado nas páginas.
- **Papéis na LGPD:**
  - cada lojista é controlador dos dados dos seus compradores, e a plataforma é operadora;
  - a plataforma é controladora dos dados dos lojistas.

## O que foi criado

**Admin:**
- **`/termos`:** termos de uso para lojistas. Cobrem:
  - serviço e conta;
  - responsabilidades da loja: produtos, CDC e direito de arrependimento, obrigações fiscais e dados dos compradores;
  - pagamentos no Mercado Pago, na conta do lojista, sem a plataforma tocar no dinheiro;
  - relatórios e assistente, que não são consultoria;
  - conteúdo, disponibilidade, encerramento, limitação de responsabilidade e lei brasileira.
- **`/privacidade`:** política de privacidade. Cobre:
  - papéis;
  - dados tratados, finalidades e bases legais;
  - compartilhamento: Mercado Pago, Brevo, Render, Vercel e Anthropic (esta só com o assistente ligado e só com agregados);
  - transferência internacional e retenção;
  - direitos do titular, segurança e cookies.
- **Cadastro:** aviso "Ao criar a conta, você concorda com os Termos de uso e declara ter lido a Política de privacidade", ligado ao botão por `aria-describedby`.
- **Rodapés:** links para as duas páginas no rodapé das páginas públicas e da apresentação.

**Vitrine:**
- **`/privacidade`:** aviso de privacidade para compradores. Explica que a loja é a controladora e que os dados de pagamento vão direto ao Mercado Pago. Também trata da retenção, dos direitos e do arrependimento, que é tratado com a loja.
- **Checkout:**
  - o aviso aprovado na D2 do ADR-0012 foi mantido;
  - a frase desatualizada "Pagamento ainda não está disponível nesta etapa" virou "O pagamento é feito no Mercado Pago, que recebe os dados de pagamento diretamente", com link para a política.
- **Rodapé:** link "Privacidade" em todas as páginas.

**Identificação da operadora:** fica em `src/lib/legal.ts` de cada aplicação. As duas cópias devem mudar juntas, porque não há pacote compartilhado (AGENTS.md).

## Fonte de cada afirmação

| Afirmação | Fonte |
| --- | --- |
| dados do comprador sem CPF; anonimização de pedidos não pagos em 90 dias | ADR-0012, D2 |
| senhas com hash; cadastros pendentes expiram em 7 dias | ADR-0005, D4 |
| credenciais do Mercado Pago cifradas; dinheiro na conta do lojista, sem taxa da plataforma | ADR-0013, P3; não há `marketplace_fee` no código |
| assistente envia só agregados e catálogo; metadados por 90 dias, sem o texto | ADR-0016, A4 e A9 |
| só cookies necessários, sem rastreamento | nenhum script de analytics nas duas aplicações |
| isolamento entre empresas | ADR de tenant e testes de isolamento |

## Pendências antes da produção

1. **Identificação:** razão social e CNPJ em `admin-web/src/lib/legal.ts` e `storefront-web/src/lib/legal.ts`. Com os dois preenchidos, o aviso de rascunho some.
2. **Validação do texto pelo responsável.** A revisão jurídica é recomendada e está no bloco 4. Pontos em aberto:
   - foro;
   - prazo de guarda dos registros de acesso (Marco Civil, art. 15);
   - prazos fiscais de pedidos pagos;
   - termos do Mercado Pago para plataformas.
3. **Registro do aceite (hipótese, não implementada):** guardar a versão e a data do aceite na ativação da conta. Hoje o aceite é um aviso no cadastro. Implementar exige migração e aprovação.
4. **Rodapé da vitrine:** o texto "Pedidos feitos aqui são de teste" é do ambiente de teste e entra na revisão do bloco 2.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre os helpers de identificação e rascunho, as rotas `/termos` e `/privacidade` como públicas e o link dos termos no cadastro |
| Vitrine: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre o link no rodapé, a página `/privacidade` e o novo aviso do checkout com link |
| Navegador | <ul><li>aviso no cadastro ligado ao botão</li><li>links do rodapé com alvo de 44px</li><li>política com aviso de rascunho</li><li>vitrine em 375px sem rolagem horizontal</li></ul> |
| Backend | sem mudança |
