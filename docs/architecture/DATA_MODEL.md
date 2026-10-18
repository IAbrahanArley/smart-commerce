# Modelo de domínio preliminar (não é schema final)
- User(id, email, auth_subject, status)
- Tenant(id, legal_name, status)
- TenantMembership(id, tenant_id, user_id, role)
- Store(id, tenant_id, slug, display_name, status)
- Product(id, tenant_id, store_id, title, description, status)
- ProductVariant(id, tenant_id, product_id, sku, price, cost, currency)
- InventoryBalance(id, tenant_id, variant_id, on_hand, reserved)
- InventoryMovement(id, tenant_id, variant_id, type, quantity, reference_id)
- Customer(id, tenant_id, name, email, phone)
- Order(id, tenant_id, store_id, customer_id, status, currency, totals)
- OrderItem(id, tenant_id, order_id, variant_id, quantity, price_snapshot, cost_snapshot)
- PaymentConnection(id, tenant_id, provider, encrypted_credentials_reference, status)
- Payment(id, tenant_id, order_id, provider, external_id, status, amount)
- PaymentEvent(id, tenant_id, provider, event_id, payload_reference, processed_at)
- Expense(id, tenant_id, category, amount, due_date, paid_at)
- FinancialEntry(id, tenant_id, source_type, source_id, type, amount, occurred_at, settled_at)
- AuditEvent(id, tenant_id, actor_id, action, resource_id, occurred_at)

Decisões pendentes: chaves, constraints, índices, normalização, política de exclusão e retenção. Não usar este documento como migração pronta.

## Subconjunto implementado na SC-002 Etapa 1

V2 cria somente `identity_user` e `identity_local_credential`, conforme [IDENTITY_FOUNDATION.md](IDENTITY_FOUNDATION.md). Usuário global: UUID, e-mail original/chave única sensível à caixa local, status, auth_version e datas; credencial separada: PK/FK user_id, hash versionado e datas. Na Etapa 1 não havia auth_subject externo ou tabelas empresariais; a evolução autorizada está descrita abaixo.

Conta pendente não recebe credencial pelo repositório; a entrega funcional posterior implementou prova de e-mail seguida de ativação explícita. V1 permanece intacta. Modelo global não constitui garantia de autorização empresarial ou isolamento A/B. Contrato de comparação e limites IDNA/Unicode constam no documento específico, sem aplicar políticas futuras de retenção nesta etapa.

## SC-003 — Empresa e loja

V5 adiciona tenant (empresa), tenant_membership (vínculo do usuário global com OWNER) e store (loja com tenant_id, slug global único, nome e status DRAFT). A unicidade de store.tenant_id limita a uma loja por empresa. Criação conjunta transacional; consultas exigem identidade e membership, sem contexto confiado ao browser. Nomes são autodeclarados, sem dados fiscais. Sem novos domínios comerciais. Contrato e critérios: [ADR-0006](adr/0006-tenant-store-onboarding.md).

## Catálogo básico autorizado

V6 cria catalog_product (UUID, tenant_id, store_id, name, sku, price, cost opcional, currency BRL, version e datas). SKU único por tenant, com comparação sensível à caixa. Dinheiro em NUMERIC sem typmod com CHECK de escala e faixa; preço positivo, custo não negativo ou NULL. FK composta store_id/tenant_id impede referências cruzadas. Versão otimista evita sobrescrita. Contrato: [ADR-0007](adr/0007-basic-catalog.md).

## Estoque básico autorizado

V7 acrescenta inventory_balance (produto, tenant, quantidade inteira não negativa) e inventory_movement (sequência, UUID público, tenant/produto/ator/requestId, tipo, delta, saldo após, motivo e instante). FKs compostas preservam tenant; requestId é único por tenant. Atualização de saldo e ledger é transacional, com bloqueio de saldo negativo e repetição idempotente. Sem reservas nesta entrega. [ADR-0009](adr/0009-basic-inventory.md).

## Vitrine pública autorizada

V8 expande store.status para DRAFT/PUBLISHED e adiciona catalog_product.published=false. Publicação da loja e seleção individual são necessárias, com autorização no admin e DTOs públicos restritos no novo módulo storefront. A aplicação storefront-web consulta somente a API pública, sem compartilhar autenticação com admin. [ADR-0010](adr/0010-public-storefront.md).

## Fotos, descrição e variantes

V9 acrescenta descrição HTML sanitizada e tipo SIMPLE/VARIABLE; cria catalog_variant com SKU/preço/custo/opções e unicidade de combinação. A variante padrão preserva o UUID do produto simples, e as referências de estoque passam a apontar para a unidade vendável sem reescrever saldos ou movimentos. V10 cria catalog_product_image com FK composta produto/tenant, bytes JPEG/PNG normalizados, dimensões, descrição alternativa e ordem. Não há armazenamento em caminhos fornecidos pelo cliente. [ADR-0011](adr/0011-catalog-media-description-variants.md).

## Reservas e entrega (Sprint 3)

V11 cria inventory_reservation (tenant, variante, pedido, quantidade, estado ACTIVE/CONSUMED/RELEASED/EXPIRED, expires_at, closed_at), com FK composta variante/tenant, uma reserva por pedido/variante e índice parcial das ativas. Reservas não alteram inventory_balance nem o ledger. A FK composta de order_id para sales_order será criada na SC-008, que introduz o pedido. As funções inventory_reserved_quantity e inventory_available_quantity concentram a fórmula de disponibilidade. V12 cria store_fulfillment 1:1 com a loja (FK composta loja/tenant): retirada com instruções e frete fixo em NUMERIC sem arredondamento (zero = grátis), área atendida e versão otimista. [ADR-0012](adr/0012-stock-reservation-cart-orders.md).

## Pedidos (SC-008)

V13 cria `sales_order` (tenant, loja, número por loja via `sales_order_counter`, estado PENDING_PAYMENT/EXPIRED/CANCELLED, totais em NUMERIC sem arredondamento com total = subtotal + frete, entrega escolhida e texto exibido, dados mínimos do comprador do D2, endereço só para frete fixo, prazo da reserva, `request_id` único por tenant com impressão digital e hash do token) e `sales_order_item` (snapshot de nome, opções, SKU, preço e custo opcional, quantidade e total da linha), com FKs compostas por tenant para loja, pedido e variante. A FK composta `inventory_reservation(order_id, tenant_id) → sales_order` foi criada como NOT VALID: vale para toda reserva nova, sem rejeitar reservas anteriores aos pedidos. `order_rate_limit` guarda contadores de tentativas com chave HMAC efêmera, sem IP em claro. Não há tabela Customer. [ADR-0012](adr/0012-stock-reservation-cart-orders.md).
