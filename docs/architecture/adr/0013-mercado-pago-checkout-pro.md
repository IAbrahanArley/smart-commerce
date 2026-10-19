# ADR-0013 — Pagamentos com Mercado Pago Checkout Pro (Sprint 4)

Status: **proposto** em 2026-10-19. Nenhuma decisão abaixo está aprovada, e nenhum código, credencial ou conta deve ser criado com base neste documento antes da aprovação registrada aqui. Plano incremental: [SPRINT_04.md](../../product/SPRINT_04.md).

## Contexto

- **[ADR-0003](0003-payment-ownership.md):**
  - cada lojista conecta a própria conta Mercado Pago;
  - a plataforma não centraliza fundos nem divide pagamentos (split fora do MVP, [PRD](../../product/PRD.md)).
- **[BUSINESS_RULES](../../product/BUSINESS_RULES.md):**
  - o status de pagamento nunca é inferido só pelo retorno do navegador;
  - o webhook é autenticado, idempotente e confirmado por consulta ao provedor;
  - pagamento confirmado baixa o estoque;
  - reembolso exige permissão, confirmação e auditoria.
- **[ADR-0012](0012-stock-reservation-cart-orders.md):**
  - o pedido nasce `PENDING_PAYMENT` com reserva de 30 minutos (D1);
  - o checkout fica desligado fora de DEV até existir pagamento (D6);
  - a §9 lista as pendências desta sprint;
  - o ledger exige `actor_id` de `identity_user` e só tem os tipos `ENTRY` e `ADJUSTMENT`.

**Documentação oficial do Mercado Pago, consultada em 2026-10-19 (confirmar de novo antes de cada tarefa, porque há inconsistências entre páginas):**

- **Checkout Pro:** existe pela **Orders API** (`POST /v1/orders` com `X-Idempotency-Key`, que devolve um `checkout_url`) e pela API de preferências. A documentação recomenda Orders para integrações novas e diz que recursos novos só sairão nela; preferências segue suportada como legado.
- **Vencimento:** `expiration_time` da order vai de 30 minutos a 30 dias (ISO 8601, por exemplo `PT30M`). Para Pix e boleto via preferência, a página recomenda pelo menos 3 dias. Pagamento feito depois do vencimento é devolvido ao pagador.
- **Webhooks:**
  - tópico `order`; cabeçalho `x-signature` (`ts=…,v1=…`) com HMAC-SHA256, usando `data.id`, `x-request-id` e `ts` e o segredo da aplicação;
  - URL **HTTPS pública** obrigatória (localhost e 127.0.0.1 não são aceitos);
  - resposta 200/201 em até 22 s; reenvios de 15 min a 96 h;
  - compras feitas com credenciais de teste **não geram notificação** (teste pelo "Simular" do painel);
  - depois de responder, confirmar com `GET /v1/orders/{id}`.
- **OAuth:** authorization code com PKCE recomendado (S256); o código vale 10 min e é de uso único; o access token dura 180 dias; o refresh token dura 6 meses, exige o escopo `offline_access` e é rotacionado a cada renovação.
- **Reembolso:** `X-Idempotency-Key` obrigatório; total ou parcial; erros para pagamento já reembolsado ou antigo demais.

Fontes:
- [Checkout Pro, visão geral (Orders × Preferências)](https://www.mercadopago.com.br/developers/en/reference/online-payments/checkout-pro-orders/overview)
- [Criar order](https://www.mercadopago.com.br/developers/en/docs/checkout-pro-orders/create-order?scope=prod)
- [Notificações de order](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-orders/notifications?scope=prod)
- [Webhooks](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/additional-content/notifications/webhooks)
- [Vencimento (preferências)](https://www.mercadopago.com.br/developers/en/docs/checkout-pro-preferences/additional-settings/expiration-date)
- [Pix (Orders)](https://www.mercadopago.com.br/developers/en/docs/checkout-api-orders/payment-integration/websites/pix)
- [OAuth](https://www.mercadopago.com.br/developers/en/docs/security/oauth/creation)
- [Renovação de token](https://www.mercadopago.com.br/developers/en/docs/subscriptions/additional-content/security/oauth/renewal)
- [Reembolso](https://www.mercadopago.com.br/developers/en/reference/online-payments/checkout-api-payments/create-refund/post)

## Decisões propostas (requerem aprovação)

| ID | Decisão | Recomendação | Alternativas | Bloqueia |
| --- | --- | --- | --- | --- |
| P1 | Caminho de integração | **Checkout Pro via Orders API** | Preferências (legado; já tem mais exemplos) | todas |
| P2 | Meios de pagamento × reserva de 30 min (D1) | **Cartão, Pix e saldo Mercado Pago; boleto excluído.** A order expira em `PT30M`, criada no mesmo instante da reserva | (a) só cartão e saldo, sem Pix; (b) Pix e boleto com reserva estendida até o vencimento (dias), o que muda o D1 | SC-013 |
| P3 | Credenciais do lojista | **OAuth com PKCE (S256) e `offline_access`**, `state` assinado e de uso único, tokens **cifrados em repouso (AES-256-GCM)** com chave em variável de ambiente, renovação antes do vencimento, "Desconectar" no admin | Lojista colar o access token (descartado: expõe segredo e não renova) | SC-012 |
| P4 | Pagamento aprovado depois da expiração ou do cancelamento | **Tentar reservar de novo e baixar**; sem estoque, o pedido fica `PAID` com alerta "sem estoque" para o lojista decidir o reembolso. Nada de reembolso automático | Reembolso automático (contraria a regra de aprovação humana) | SC-014 |
| P5 | Confirmação do pagamento | **Webhook assinado → registro idempotente do evento → `GET /v1/orders/{id}` com o token do lojista → transição condicional.** Uma rotina de conciliação consulta pedidos com order criada e sem confirmação | Só webhook (perde eventos); só consulta periódica (latência) | SC-014, SC-015 |
| P6 | Webhook em desenvolvimento (exige HTTPS público) | **Sem túnel por padrão:** em DEV a confirmação vem da rotina de conciliação e do "Simular" do painel; túnel só com aprovação, por ser serviço externo | Túnel (ngrok/cloudflared): serviço externo, precisa de aprovação | SC-014 |
| P7 | Baixa de estoque e ator do ledger | **Novo tipo `SALE`** (e `SALE_REVERSAL` para devolução ao estoque) com **ator de sistema**: `actor_kind USER/SYSTEM`, `actor_id` obrigatório só para `USER`; a reserva passa a `CONSUMED` | Usuário técnico em `identity_user` (mistura identidade humana e sistema) | SC-014 |
| P8 | Estados do pedido | `PENDING_PAYMENT → PAID → REFUNDED`, com `PAID` vindo também de `EXPIRED`/`CANCELLED` (P4); tabela `payment` (1:1 com a order do provedor) e `payment_event` (eventos brutos sem dados de cartão) | Estados intermediários do provedor no próprio pedido | SC-013, SC-014 |
| P9 | Reembolso | **Só reembolso total no MVP**, iniciado pelo lojista no admin com confirmação e motivo, chave de idempotência por pedido e escolha explícita de "devolver ao estoque" | Parcial (fica para depois); automático (descartado) | SC-016 |
| P10 | Ambiente | **Somente sandbox na Sprint 4**: aplicação e usuários de teste criados **pelo responsável** no painel do Mercado Pago, segredos só em variáveis de ambiente, nunca no chat ou no Git. Produção exige aprovação separada (o D6 continua) | — | SC-012 |
| P11 | Dados do pagador | **Não armazenar CPF nem dados de cartão**: o Checkout Pro coleta. Guardar só ids do provedor, status, valores, taxa e líquido (para a Sprint 5) | — | SC-013 |

## Consequências e riscos

- **Cadeia de tempo do D1.** A reserva e a order expiram juntas. Um pagamento aprovado segundos depois do vencimento cai no P4. Pix pode creditar com atraso, e quem paga depois do vencimento é reembolsado pelo provedor; os dois casos precisam de teste.
- **Superfície nova.**
  - Endpoint público de webhook, que fica fora da cadeia de sessão, não tem CSRF, valida assinatura e tamanho e tem limite de taxa.
  - Segredos: `client_secret`, chave de cifra e segredo do webhook.
  - Tokens de terceiros por tenant, que nunca podem aparecer em log.
- **Isolamento multi-tenant.** A notificação traz `user_id` e o id da order. O tenant é resolvido pela order guardada (nunca pelo payload), e a consulta usa o token **daquele** lojista. Um token de A nunca consulta nem reembolsa a order de B.
- **Teste.** Sem notificação de credencial de teste, o E2E de webhook depende do "Simular" ou de túnel (P6). A conciliação cobre o fluxo ponta a ponta.
- **Antes de produção.** Revisão jurídica e dos termos do provedor ([PRD](../../product/PRD.md), "Requisitos legais"), política de privacidade, proxy confiável (R1 da SC-011) e decisões R2/R6 pendentes.
