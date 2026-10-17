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
