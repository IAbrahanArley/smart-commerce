# Sprint 4 — Mercado Pago em sandbox (planejamento)

Data: 2026-10-19. **P1–P11 do [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md) aprovadas em 2026-10-19.** Cada tarefa ainda exige autorização própria. A Sprint 3 foi concluída em 2026-10-20 ([SPRINT_03.md](SPRINT_03.md)), então a dependência está atendida.

**Objetivo:** o lojista conecta sua conta Mercado Pago de teste. O comprador paga um pedido `PENDING_PAYMENT` pelo Checkout Pro em sandbox. O pagamento é confirmado pelo backend (webhook assinado ou conciliação, nunca pelo retorno do navegador), o pedido passa a `PAID` e o estoque é baixado uma única vez. O lojista vê o pagamento e pode reembolsar com confirmação. Sem produção, split, assinatura SaaS nem financeiro (Sprint 5).

## Gates de entrada

Nenhuma tarefa começa sem autorização explícita do responsável.

| Tarefa | Requer |
| --- | --- |
| SC-012 | P1, P3, P10 aprovadas; o **responsável** cria a aplicação e os usuários de teste no painel do Mercado Pago e configura as variáveis de ambiente localmente (nunca no chat) |
| SC-013 | SC-012; P2, P8, P11 |
| SC-014 | SC-013; P4, P5, P6, P7 |
| SC-015 | SC-014 |
| SC-016 | SC-014; P9 |
| SC-017 | SC-012 a SC-016 |

## SC-012 — Conexão da conta Mercado Pago (módulo `payments`)

**Estado (2026-10-20):** autorizada e implementada, validada com um servidor OAuth falso ([validação](../quality/SC-012-VALIDATION.md)). A conexão com uma conta de teste real fica pendente até o responsável criar a aplicação e os usuários de teste.

- **Credenciais da plataforma** em variáveis de ambiente: `client_id`, `client_secret`, segredo do webhook e chave de cifra. Nenhum valor versionado.
- **OAuth** com PKCE S256 e `offline_access`, com `state` assinado, de uso único e ligado ao lojista e ao tenant.
- **Tokens** cifrados com AES-256-GCM. Renovação antes do vencimento, gravando o novo refresh token na mesma transação.
- **Admin:** seção Pagamentos com "Conectar Mercado Pago", estado (conectado, expirado ou revogado, com a conta exibida só pelo identificador) e "Desconectar".

**Aceite:**
- o lojista conecta e desconecta a conta de teste;
- o token nunca aparece em resposta, log ou HTML;
- renovar não perde a conexão.

**Testes:**
- `state` adulterado, reutilizado ou de outro tenant;
- callback sem PKCE;
- cifra com nonce único;
- rotação do refresh token;
- logs sem segredo.

**A/B:** OWNER A não conecta, lê nem desconecta a conta de B.

## SC-013 — Pagar o pedido pelo Checkout Pro

**Estado (2026-10-20):** autorizada e implementada, validada com um servidor falso da Orders API ([validação](../quality/SC-013-VALIDATION.md)). A criação de uma order real em sandbox fica pendente até a SC-012 ser validada com o usuário de teste vendedor.

- **Criação da order:** no checkout, o backend cria a order no Mercado Pago com o token do lojista, `X-Idempotency-Key` derivado do pedido, `external_reference` (id do pedido), itens e total do snapshot, `expiration_time` igual ao fim da reserva (P2) e meios de pagamento restritos.
- **Tabela `payment`:** id da order do provedor, status, valores, sem CPF ou cartão.
- **Vitrine:** depois de "Confirmar pedido", redireciona para o `checkout_url`. As páginas de retorno mostram "pagamento em confirmação" e **não confiam** em parâmetros da URL.
- **Pré-condição:** o checkout continua exigindo loja conectada; o D6 segue em DEV.

**Aceite:** um pedido de teste chega ao Checkout Pro com o total correto e com o pedido e a order expirando juntos.

**Testes:**
- idempotência da criação (retry não cria segunda order);
- falha do provedor deixa o pedido pendente e permite tentar de novo;
- loja sem conexão bloqueia o checkout.

**A/B:** a order de A usa sempre o token de A.

## SC-014 — Webhook, confirmação e baixa de estoque

**Estado (2026-10-21):** autorizada e implementada, validada com webhook assinado e Orders API falsos ([validação](../quality/SC-014-VALIDATION.md)). O formato exato da assinatura e os estados do provedor ainda precisam ser confirmados com o "Simular" do painel (ver o lembrete abaixo).

- **Endpoint público** de webhook com limite de tamanho e taxa e validação de `x-signature`, rejeitando com 401 quando inválida.
- **`payment_event`** idempotente pela chave do evento; resposta 200 rápida e processamento assíncrono.
- **Confirmação:** `GET /v1/orders/{id}` com o token do lojista dono do pedido e transição condicional `PENDING_PAYMENT → PAID`.
- **Baixa:** consumo da reserva com movimento `SALE` de ator de sistema (P7).
- **Pagamento tardio** (P4): reserva de novo ou alerta "pago sem estoque" para o lojista.

**Aceite:** pagamento aprovado leva o pedido a `PAID` e baixa o estoque uma vez; webhook repetido, fora de ordem ou forjado não tem efeito.

**Testes:**
- assinatura válida, inválida e com `ts` antigo;
- evento duplicado e fora de ordem;
- corrida entre webhook, conciliação e expiração;
- pagamento após expiração com e sem estoque;
- ledger exato;
- logs sem token.

**A/B:** notificação de uma order de B nunca altera pedido de A; tenant resolvido pela order guardada.

## SC-015 — Conciliação mínima

- **Rotina** com `SKIP LOCKED` que consulta orders sem confirmação, de pedidos pendentes e recém-expirados, e aplica a mesma transição do SC-014. É o caminho principal em DEV (P6).
- **Admin:** no detalhe do pedido, situação do pagamento, id da order no provedor, valores, taxa e líquido.

**Aceite:** sem webhook, um pagamento aprovado é confirmado pela rotina; divergências ficam visíveis.

**Testes:** rotina concorrente, provedor indisponível e conciliação idempotente.

**A/B:** a rotina usa o token do tenant de cada pedido.

## SC-016 — Reembolso controlado

- **Admin:** "Reembolsar" só em pedido `PAID`, com motivo, confirmação em duas etapas e opção explícita "devolver ao estoque" (movimento `SALE_REVERSAL`).
- **Backend:** `X-Idempotency-Key` por pedido; estado `REFUNDED` confirmado pelo provedor (webhook ou consulta).

**Aceite:** o reembolso total em sandbox leva o pedido a `REFUNDED`, uma única vez, e a devolução ao estoque só acontece se escolhida.

**Testes:**
- clique duplo e retry;
- erro "já reembolsado";
- corrida com webhook;
- auditoria de quem reembolsou.

**A/B:** OWNER A não reembolsa pedido de B.

## SC-017 — Revisão integrada e validação ponta a ponta em sandbox

- **Revisão:** checklists de [SECURITY.md](../agents/SECURITY.md) e [QA.md](../agents/QA.md) e modelo de ameaças de webhook e OAuth (forja, replay, SSRF, vazamento de token, confusão de tenant).
- **Validação ponta a ponta** com contas de teste: conectar, pagar com cartão de teste e com Pix, confirmar, baixar e reembolsar, conforme P6.
- **Registro** em `docs/quality/` e atualização de ARCHITECTURE, DATA_MODEL e status do ADR-0013.

**Aceite:** fluxo de teste ponta a ponta concluído e nenhum achado crítico aberto.

## Lembrete: validação real pendente (SC-012 e SC-013)

**Aberto desde 2026-10-21.** Os dois itens foram validados só com servidores falsos, porque o responsável tinha uma única conta Mercado Pago, e a conta dona da aplicação não pode autorizar a si mesma. Fazer antes ou junto com a SC-017:

- [ ] No painel de desenvolvedores, em **Suas integrações → aplicação → Contas de teste**, criar um usuário de teste **vendedor** e um **comprador**.
- [ ] Em janela anônima, logado como o vendedor de teste, conectar a loja em **Pagamentos** no admin (SC-012); desconectar e conectar de novo.
- [ ] Fazer um pedido na vitrine e conferir no Checkout Pro o total, os itens e os meios oferecidos (SC-013, hipótese H4: sem boleto).
- [ ] Pagar com o comprador de teste e conferir o retorno à vitrine com `STOREFRONT_PUBLIC_ORIGIN` em HTTPS (hipótese H3).
- [ ] Configurar o webhook da aplicação (evento "Order (Mercado Pago)") e `MERCADO_PAGO_WEBHOOK_SECRET`; usar o "Simular" do painel e conferir que a assinatura é aceita e o pedido passa a `PAID` (SC-014).
- [ ] Registrar o resultado em [SC-012-VALIDATION.md](../quality/SC-012-VALIDATION.md) [SC-013-VALIDATION.md](../quality/SC-013-VALIDATION.md) e [SC-014-VALIDATION.md](../quality/SC-014-VALIDATION.md), inclusive o `live_mode` e o domínio do `checkout_url`.

## Definition of Done

- Testes executados: unidade, integração PostgreSQL, ArchUnit, smoke e casos de webhook e conciliação.
- Migrações aditivas.
- Segredos fora do Git e dos logs.
- Documentação atualizada.
- Revisão multi-tenant.
- Aprovação humana.
- Sem produção, credenciais reais de lojista, cobrança, split, túnel ou serviço novo sem autorização.

## Fora da sprint

Produção e homologação no provedor, assinatura SaaS, split, reembolso parcial, e-mail ao comprador, financeiro (CMV, margem, fluxo de caixa — Sprint 5), chargebacks além do registro do evento, múltiplos adquirentes.
