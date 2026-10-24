# SC-013 — Pagar o pedido pelo Checkout Pro: validação

Data: 2026-10-20. Base: [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md) (P1, P2, P8, P11) e [SPRINT_04.md](../product/SPRINT_04.md).

**Estado:** implementada e validada com um **servidor falso da Orders API** em loopback. Nenhuma order foi criada no Mercado Pago real. Isso depende da SC-012 validada com o usuário de teste vendedor.

> **Atualização (2026-10-24):** o Checkout Pro passou da Orders API para a API de preferências ([ADR-0013, mudança da P1](../architecture/adr/0013-mercado-pago-checkout-pro.md#mudança-da-p1-checkout-pro-pela-api-de-preferências-aprovada-em-2026-10-24)). Os testes citados aqui foram adaptados aos endpoints novos; este registro descreve a validação original.

## Critérios de aceite

| Critério | Evidência |
| --- | --- |
| A order é criada com o token do lojista, `X-Idempotency-Key` derivado do pedido, `external_reference`, itens e total do snapshot, `expiration_time` igual ao fim da reserva e meios restritos | `CheckoutPaymentIT.createsTheProviderOrder…` |
| Tabela `payment` com ids do provedor, status e valores, sem CPF ou cartão | `V18__payments.sql`; o mesmo teste confere a linha e a ausência de `payer` |
| A vitrine redireciona para o `checkout_url`, e as páginas de retorno não confiam na URL | smoke da vitrine (`smoke-checkout.mjs`) |
| Idempotência: nova tentativa não cria segunda order | `retriesAndConcurrentAttemptsShareOneProviderOrder` (4 tentativas simultâneas e uma depois) |
| Falha do provedor deixa o pedido pendente e permite tentar de novo | `providerFailureKeepsTheOrderPendingAndTheBuyerCanRetry` |
| Loja sem conexão bloqueia o checkout | `storeWithoutConnectedAccountTakesNoOrdersAndCannotBePaid` |
| A/B: a order de A usa sempre o token de A | `eachOrderIsPaidWithItsOwnStoresTokenAndForeignAttemptsAreOne404` |

## O que foi entregue

**Backend, módulo `payments`:**
- **Migração `V18__payments.sql`** (aditiva): tabela `payment`, 1:1 com o pedido, nos estados `CREATING` e `CREATED`. Ver [DATA_MODEL](../architecture/DATA_MODEL.md).
- **`MercadoPagoOrdersClient`:** faz `POST /v1/orders` e confere a resposta:
  - id e status bem formados;
  - `checkout_url` em HTTPS num domínio `mercadopago.com(.xx)`, sem porta, credenciais ou fragmento;
  - mesmo total, moeda `BRL` e `external_reference` igual ao pedido.

  Qualquer divergência vira `PROVIDER_ERROR`, e o comprador nunca é redirecionado. Não segue redirecionamentos e usa timeout de 10 s.
- **`PaymentCheckoutService.open`:**
  1. reserva a linha `payment` numa transação própria;
  2. se a order já existe, devolve a URL guardada sem chamar o provedor;
  3. senão, obtém o token da loja (renovando se preciso), chama o provedor fora de transação e grava o resultado só se a linha ainda estiver `CREATING`.
- **Chave de idempotência:** um UUID derivado de `sc-checkout-pro:<id do pedido>`, igual em toda tentativa.
- **`PaymentConnectionService.sellerCredentials`:** devolve o token junto com o id da conta, que fica guardado em `payment.provider_user_id` para a SC-014 conferir notificações.

**Backend, módulo `orders`:**
- `POST /api/public/stores/{slug}/orders/{id}/payment`, com o comprador provado pelo `X-Order-Token`. Pedido ausente, de outra loja ou com token errado recebe o mesmo 404. Os outros erros:
  - `NOT_PAYABLE` (409): pedido expirado ou cancelado;
  - `PAYMENT_UNAVAILABLE` (409): integração desligada ou conta desconectada ou revogada;
  - `PAYMENT_FAILED` (502): falha do provedor.
- O checkout recusa pedidos novos com `PAYMENT_UNAVAILABLE` quando a integração está configurada e a loja não está conectada.
- `OrderView.payable` diz à vitrine quando oferecer o pagamento.

**Vitrine:**
- **Depois de "Confirmar pedido":** o servidor inicia o pagamento com o token recém-criado e redireciona para o Mercado Pago. Se falhar, leva à página do pedido.
- **Página do pedido:** botão "Ir para o pagamento", por server action, que funciona sem JavaScript.
- **Página de retorno** `/lojas/{slug}/pedidos/{id}/pagamento`, para todos os resultados:
  - mostra "Pagamento em confirmação" e o estado do pedido segundo o backend;
  - não lê a query string do provedor;
  - exige o cookie do pedido.
- **Antes de redirecionar,** a vitrine confere de novo que o destino é uma página HTTPS do Mercado Pago.

## Configuração

| Variável | Uso |
| --- | --- |
| `STOREFRONT_PUBLIC_ORIGIN` | Origem pública da vitrine para as URLs de retorno. Só é enviada se for HTTPS; vazia ou HTTP (localhost) não envia URLs de retorno |

As demais variáveis são as da SC-012 ([SC-012-VALIDATION.md](SC-012-VALIDATION.md)). `payments.mercado-pago.api-url` existe só para o servidor falso dos testes; o padrão é `https://api.mercadopago.com`.

## Testes

| Teste | Cobertura |
| --- | --- |
| `CheckoutPaymentIT` (7), com PostgreSQL via Testcontainers e Orders API falsa | <ul><li>corpo enviado: tipo, modo, total, referência, `PT30M`, itens com opções e frete, `not_allowed_types`, URLs de retorno e ausência de `payer` e de dados do comprador</li><li>`Authorization` com o token da loja e chave de idempotência derivada</li><li>linha `payment` em `CREATED`; resposta e logs sem token</li><li>4 tentativas simultâneas e uma posterior: uma order no provedor, uma linha, a mesma URL</li><li>falha 500 e nova tentativa com a mesma chave</li><li>respostas inválidas (outro domínio, HTTP, domínio parecido, valor ou moeda divergente, id malformado, JSON inválido) sem redirecionar</li><li>loja desconectada: checkout 409 sem criar pedido, `payable=false`, pagamento 409</li><li>pedido expirado ou cancelado: 409 `NOT_PAYABLE`</li><li>A/B: token de cada loja, pedido de A pela loja B, pedido de B com token de A, token errado, sem token e id inexistente: 404</li></ul> |
| `OrderCheckoutIT`, `PaymentConnectionIT` e demais | sem a integração, o checkout segue como antes; com ela, a conexão continua igual |
| `ArchitectureTest` | `services` sem HTTP e repositórios isolados, com as classes novas |
| Smoke da vitrine | <ul><li>destino aceito só em HTTPS `mercadopago.com(.xx)` (rejeita HTTP, domínio parecido, credenciais, porta, fragmento, `javascript:`, caminho relativo e URL longa)</li><li>checkout redireciona ao Checkout Pro com o token do pedido, sem corpo</li><li>"Ir para o pagamento" sem JavaScript; falhas 502, 409 e URL estranha não redirecionam</li><li>origem externa não inicia pagamento</li><li>retorno com `?status=approved` não declara aprovação, e sem o cookie dá 404</li></ul> |

**Comandos executados:**
- backend: `mvnw verify` completo, com 78 testes unitários e 109 de integração, todos passando;
- vitrine: `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

**Não executado:** verificação manual no navegador e chamada ao Mercado Pago real (sandbox).

## Revisão de segurança (multi-tenant)

- **Tenant:** vem do pedido resolvido pelo slug publicado e pelo token do comprador, nunca de parâmetro do cliente. O token do provedor é sempre o da loja do pedido.
- **Redirecionamento aberto:** o destino vem do provedor, é validado no backend (domínio, HTTPS e conferência de total e referência) e de novo na vitrine.
- **Confiança no navegador:** a página de retorno não lê parâmetros. O pedido continua `PENDING_PAYMENT` até a SC-014.
- **Dados pessoais (P11):** nenhum dado do comprador vai ao Mercado Pago; ele mesmo informa no Checkout Pro.
- **Segredos:** `SellerCredentials.toString` é redigido. O log de falha registra só o id do pedido. O teste confere que os logs não têm token.
- **Abuso:** o endpoint exige o token de 256 bits do pedido. Depois da primeira criação, as chamadas não vão mais ao provedor.

## Hipóteses e limites

- **H1, sem integração configurada:** em DEV ou D6, o checkout continua criando pedidos sem pagamento, como antes. O bloqueio por falta de conexão só vale com a integração ligada.
- **H2, sem `payer`:** o campo é opcional na Orders API, então não enviamos e-mail do comprador.
- **H3, URLs de retorno só em HTTPS:** a documentação diz que o provedor rejeita endereços locais. Em DEV o comprador volta pelo link do pedido.
- **H4, tipos excluídos `ticket`, `atm` e `digital_currency`:** a documentação lista os valores por `GET /v1/payment_methods`. Confirmar no sandbox que cartão, Pix e saldo continuam e que esses valores não geram erro.
- **Domínio do `checkout_url`:** aceita `*.mercadopago.com` e `*.mercadopago.com.xx`. Confirmar no sandbox o domínio real, inclusive o da conta de teste.
- **Vencimento:** a API exige no mínimo 30 min. Se a primeira criação falhar e der certo minutos depois, a order pode vencer depois da reserva; esse pagamento tardio é o P4, tratado na SC-014.
- **Chave de idempotência:** se o provedor guardar um erro 4xx para a mesma chave, as novas tentativas repetem o erro até a reserva expirar. Verificar no sandbox.
- **Fora do escopo (SC-014 a SC-016):** confirmação, baixa de estoque, webhook, conciliação e exibição do pagamento no admin.

## Próximo passo

Com a SC-012 validada pelo usuário de teste vendedor:
1. fazer um pedido de teste na vitrine;
2. conferir no Checkout Pro o total, os itens e os meios oferecidos;
3. pagar com o usuário comprador de teste;
4. registrar aqui o resultado e as hipóteses H3 e H4.
