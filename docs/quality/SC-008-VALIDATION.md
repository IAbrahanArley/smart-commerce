# Validação local — SC-008 Checkout e pedido `PENDING_PAYMENT`

Data: 2026-10-18. Branch `feat/sc-008-checkout-orders`, a partir da `main` em `096125e`. Escopo: [SPRINT_03.md](../product/SPRINT_03.md) e [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md), com D1–D7 e as decisões complementares de 2026-10-18 (IP assinado, valores do D4, aviso de privacidade). Somente backend; a tela de checkout é a SC-009.

## Entrega

- **V13:** `sales_order`, `sales_order_item`, `sales_order_counter` e `order_rate_limit`; FK composta `inventory_reservation(order_id, tenant_id) → sales_order` como NOT VALID (aplica-se a toda reserva nova sem rejeitar reservas anteriores aos pedidos, como fixtures locais). Migrações anteriores intactas.
- **Módulo `orders`** em camadas (ADR-0008): parser estrito do checkout, domínio validado (comprador mínimo do D2, endereço só para frete fixo, linhas 1–20 com 1–10 unidades), repositórios com todo o SQL, serviços de checkout, consulta e admissão, controller e tratamento de erros. ArchUnit aprovado.
- **`POST /api/public/stores/{slug}/orders`:** resolve a loja publicada pelo slug; aplica o limite por IP; serializa por `requestId` (replay idêntico devolve o mesmo pedido com `replayed=true` e sem token; payload diferente → `REQUEST_CONFLICT`); limita pedidos pendentes por e-mail e loja; revalida variantes da própria loja; confere a entrega ativa; calcula totais no backend e compara com `expectedTotal` (`PRICE_CHANGED` com `currentTotal`); grava pedido, itens e reservas numa transação. Qualquer falha desfaz tudo e não consome a chave.
- **`GET /api/public/stores/{slug}/orders/{id}`:** exige `X-Order-Token`; o banco guarda só o SHA-256; comparação em tempo constante; pedido de outra loja, token errado ou inexistente → 404 uniforme. Pedido pendente com prazo vencido aparece como `EXPIRED` sem rotina.
- **Cotação:** passa a trazer `delivery` com as opções ativas da loja (SC-006).
- **Configuração:** `orders.checkout.enabled` falso por padrão e verdadeiro só no perfil `dev` (D6); `orders.client-ip.secret` (variável `STOREFRONT_FORWARDING_SECRET`, mínimo de 32 caracteres, sem valor versionado).
- **Testes existentes ajustados:** reservas criadas em `InventoryReservationIT`, `InventoryIsolationIT` e `CartQuoteIT` agora apontam para pedidos reais (`TestOrders`), para que os CHECKs testados falhem pelo motivo certo e não pela FK nova; `CartQuoteIT` passou a esperar o campo `delivery`.

## Correção durante a implementação

A chave de e-mail da identidade só normaliza o domínio (a parte local é sensível a maiúsculas, contrato do login). Usada no limite de pedidos pendentes, ela permitiria contornar o teto trocando a caixa. O teste unitário pegou a divergência; a chave do comprador passou a ser o e-mail inteiro em minúsculas, sem alterar a identidade.

## Verificações executadas

- **Backend:** `mvnw.cmd -B -ntp verify -Dspring-boot.repackage.skip=true` com Corretto 21.0.9 e PostgreSQL 18.6/Testcontainers; BUILD SUCCESS em 2026-10-18 13:30:34 -03:00. **143 testes** (61 unitários/arquiteturais e 82 de integração), zero falhas, erros ou ignorados; Spotless limpo. Log local: `backend/target/sc008-full-verify.log`. O repackage foi pulado pelo mesmo motivo da integração anterior; a CI empacota.
- **Novos:** `OrderDomainTest` (parser e normalização, impressão digital estável, token, assinatura de IP com janela de 60 s, checkout desligado → 404), `OrderCheckoutIT` (9 cenários) e `SalesOrderMigrationIT`.
- **Frontends:** não alterados nesta tarefa. O novo campo da cotação é aditivo; checks dos frontends não foram executados.

## Correções da revisão do PR #8 (2026-10-18)

Revisão independente, somente leitura, com os achados conferidos no código antes da correção. Decisões registradas no [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md#ajustes-da-revisão-do-pr-8-2026-10-18).

- **Major, pool de conexões:** `admit()` (`REQUIRES_NEW`) rodava dentro da transação do checkout e pedia uma segunda conexão. Com o pool padrão de 10 e espera de 3 s, cerca de 10 checkouts simultâneos se bloqueariam e falhariam com 500. Agora `OrderCheckoutService` não é transacional: resolve a loja, responde retentativas, cobra a admissão e chama `OrderPlacementService.place`, a nova transação do pedido. Cada etapa segura no máximo uma conexão. Regressão: 16 checkouts simultâneos, todos 201.
- **Retentativas e limite por IP:** um `requestId` já gravado é respondido (replay ou `REQUEST_CONFLICT`) antes de cobrar a admissão. A transação do pedido repete a checagem sob o lock. Regressão: 25 replays do mesmo IP seguem em 200, e uma nova tentativa ainda é admitida.
- **Segredo ausente fora de DEV:** `CheckoutSafetyCheck` impede a inicialização com checkout habilitado, segredo vazio e perfil diferente de `dev`. Teste unitário dedicado.
- **Índice:** `V14__sales_order_item_variant_index.sql`, aditiva, cria o índice da FK `sales_order_item(variant_id, tenant_id)`. As FKs do lado do pedido já usam os UNIQUE começados por `order_id`. A V13 não foi alterada, para não mudar seu checksum em bancos locais.
- **Registrados no ADR, sem código:** teto multiplicado por instâncias (20 × N), validação futura da FK `NOT VALID` e consistência loja do item × loja do pedido garantida pelo serviço.

Verificação após as correções: `mvnw.cmd -B -ntp verify -Dspring-boot.repackage.skip=true`, BUILD SUCCESS em 2026-10-18 16:19:49 -03:00. **146 testes** (62 unitários/arquiteturais e 84 de integração), zero falhas, erros ou ignorados; Spotless limpo. As mensagens `Unexpected error occurred in scheduled task` do Spring Session no log vêm da limpeza agendada depois que os contêineres de teste param; já apareciam na execução anterior e não afetam o resultado. Log local: `backend/target/review-fix-verify.log`.

## Cobertura dos critérios da SC-008

| Critério | Evidência |
| --- | --- |
| Pedido com número, total = itens + frete, reserva visível | Criação com frete fixo (51,80 + 12,50 = 64,30), número 1; retirada com frete 0,00, número 2 e endereço nulo; reservas ACTIVE por item; prazo ≈ 30 min; cotação passa a indicar INSUFFICIENT para o que foi reservado. |
| Idempotência | Mesmo `requestId` e payload → 200, mesmo pedido, sem segunda reserva e sem token; payload diferente → 409 `REQUEST_CONFLICT`; 6 tentativas concorrentes da mesma chave → um 201 e cinco 200. |
| `PRICE_CHANGED` sem gravar | 409 com `currentTotal`; nenhuma linha em pedidos, itens ou reservas; a mesma chave é aceita em seguida com o total correto. |
| Concorrência pela última unidade | 8 checkouts distintos → exatamente um 201 e sete `INSUFFICIENT_STOCK`; uma reserva. |
| Snapshot imutável e custo nulo | Após alterar preço, custo e nome no catálogo, o pedido mantém 15,90 e o nome original; custo ausente gravado como nulo. |
| Precisão monetária | Totais textuais com duas casas; `expectedTotal` com mais casas ou negativo → 400; CHECKs de escala e de soma no banco. |
| Limites do D4 | 5 pendentes por e-mail na loja → 6º 429 (inclusive com outra caixa); outro e-mail e outra loja aceitos; pendentes vencidos deixam de contar; 20 tentativas por IP assinado → 21ª 429 com `Retry-After`; header sem assinatura válida cai no endereço da conexão; tabela sem IP em claro. |
| Dados pessoais | Respostas sem e-mail, telefone, SKU, custo ou identificadores internos; logs da execução sem e-mail, telefone, token ou IP (`OutputCaptureExtension`); token só como hash. |
| A/B entre tenants | Variante da loja B no checkout da A → `ITEM_UNAVAILABLE` sem gravar; mesmo `requestId` em A e B gera pedidos independentes; token do pedido de A não abre o pedido no slug de B; FKs compostas loja/pedido/variante por tenant. |
| Entrega e validação | Opção desativada → `FULFILLMENT_UNAVAILABLE`; endereço em retirada, falta de endereço no frete, campos desconhecidos, linhas duplicadas, 21 linhas, quantidade 11, e-mail/telefone/nome inválidos → 400; corpo acima de 8 KiB → 413; loja em rascunho, inexistente ou slug inválido → 404. |
| Migração | V12 → V13 com reserva órfã preexistente: aplicada, reserva preservada e disponível inalterado; nova reserva órfã rejeitada; reserva com pedido real aceita. |

## Revisão de segurança multi-tenant

Revisão do coordenador conforme [SECURITY.md](../agents/SECURITY.md); a revisão independente por agente especializado não foi executada.

- O tenant vem sempre da loja publicada resolvida pelo servidor; IDs do cliente são apenas seletores. Variantes, entrega, numeração e reservas são consultadas com loja e tenant; o banco reforça por FKs compostas.
- A cadeia pública continua sem sessão e cookies; o token do comprador viaja em header, então não há autoridade ambiente e CSRF não se aplica. POSTs vindos de navegador com `Origin` diferente da origem autorizada são recusados pelo filtro existente.
- Locks: chave da requisição e e-mail do comprador por advisory lock; saldos travados em ordem pelo serviço de reservas; numeração sob lock da linha do contador.
- **Riscos residuais registrados (sem bloqueio):** (1) inferência de saldo por tentativas, limitada por 10 unidades por linha e pelos limites do D4, risco já aceito no ADR; (2) um atacante com muitos e-mails e IPs ainda pode reservar estoque por 30 minutos; (3) `order_rate_limit` não tem teto de cardinalidade (há limpeza por chave e em lotes de 100); considerar um teto como na identidade antes de produção; (4) a validade jurídica de base legal e retenção continua pendente.

## Limitações e pendências

- Sem verificação em navegador: não há interface nesta tarefa (SC-009).
- A anonimização após 90 dias e a expiração materializada dos pedidos são da SC-010.
- O helper `start-backend.ps1` não lê o segredo do `.env`; para usar o IP assinado em DEV, defina a variável no terminal antes de executar o helper.
- A skill `supabase-postgres-best-practices` citada no AGENTS.md não está instalada neste ambiente; foram seguidos os padrões das migrações existentes.
- SEC-003 continua aberta e inalterada.
