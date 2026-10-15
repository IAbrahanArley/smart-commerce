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

V2 cria somente `identity_user` e `identity_local_credential`, conforme [IDENTITY_FOUNDATION.md](IDENTITY_FOUNDATION.md). Usuário global: UUID, e-mail original/chave única sensível à caixa local, status, auth_version e datas; credencial separada: PK/FK user_id, hash versionado e datas. Não há auth_subject externo ou tabelas tenant/store/membership/role na implementação atual; a lista acima continua preliminar para tarefas futuras.

Conta pendente não recebe credencial pelo repositório; não existe ativação funcional. V1 permanece intacta. Modelo global não constitui garantia de autorização empresarial ou isolamento A/B. Contrato de comparação e limites IDNA/Unicode constam no documento específico, sem aplicar políticas futuras de retenção nesta etapa.
