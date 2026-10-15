# Arquitetura v0.1
## Estilo
Monólito modular no backend Spring Boot, com interfaces explícitas entre módulos e um PostgreSQL. Admin e vitrine são aplicações Next.js distintas. Deploy inicial simples, sem microserviços.

## Módulos previstos
identity, tenant, storefront, catalog, inventory, cart, orders, payments, finance, analytics, ai.

## Limites
- `tenant` controla empresa, loja e permissões.
- `catalog` mantém produtos, preços e variantes.
- `inventory` mantém estoque e reservas, com movimentações auditáveis.
- `orders` mantém pedidos e snapshots financeiros.
- `payments` encapsula provedores, webhooks, idempotência e reconciliação.
- `finance` gera lançamentos/indicadores a partir de eventos confirmados e despesas registradas.
- `analytics` agrega indicadores; `ai` usa ferramentas read-only do backend.

## Multi-tenancy
Banco compartilhado com `tenant_id` em todas as tabelas de domínio específicas da loja; autorização obrigatória no serviço e repositório. O tenant autorizado deriva da identidade autenticada e de vínculo de participação autorizado; frontend, payload, header ou slug não são, isoladamente, evidência de autorização. Um seletor informado pelo cliente exige validação desse vínculo pelo backend. Unicidades compostas e referências devem preservar a integridade entre registros do mesmo tenant; testes negativos A/B na SC-003 e SC-004. Avaliar Row-Level Security como defesa adicional em ADR específico.

## Fundação SC-001
O [ADR-0004](adr/0004-project-bootstrap.md) registra estrutura do repositório, ferramentas, execução local e CI. O bootstrap não implementa domínio, autenticação funcional nem isolamento de dados de negócio; essas garantias serão verificadas nas tarefas seguintes.

## Fluxo de pagamento (planejado)
Lojista autoriza Mercado Pago via OAuth → credenciais protegidas por tenant → pedido pendente + reserva de estoque → Checkout Pro → webhook validado + consulta de status → transição idempotente → baixa de estoque + registros financeiros → reconciliação. Confirmar requisitos atuais e compatibilidade do provedor antes de implementar.

## Princípios financeiros
Guardar fatos auditáveis e separar data da venda, aprovação, liquidação e movimentação de caixa. BigDecimal, BRL, snapshot de custos e taxas. IA nunca calcula o resultado como fonte de verdade.

## Segurança
RBAC, segregação de tenant, tokens cifrados em repouso, secrets fora do Git, auditoria, rate limiting futuro, proteção CSRF conforme estratégia de autenticação, validação de inputs, LGPD.

## Decisões em aberto
Provedor de identidade, modelo de assinatura SaaS, expiração de reserva, frete, modelagem de variantes, arquitetura de deploy, RLS, armazenamento de imagens e estratégia de observabilidade. Registrar ADR antes de decisões irreversíveis.
