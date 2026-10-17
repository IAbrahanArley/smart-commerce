# Arquitetura v0.1
## Estilo
Monólito modular no backend Spring Boot, com interfaces explícitas entre módulos e um PostgreSQL. Admin e vitrine são aplicações Next.js distintas. Deploy inicial simples, sem microserviços.

## Organização da implementação

Backend organizado por funcionalidade dentro do monólito, com camadas explícitas em cada módulo: `controllers`, `services`, `repositories`, `entities`, `domain` e `dto/request`, `dto/response`, conforme a necessidade real. Spring MVC recebe e responde HTTP/JSON; serviços coordenam regras e transações; repositórios concentram persistência. As telas pertencem ao Next.js, que consome a API. Não há templates de views renderizados pelo Spring nem divisão em microserviços.

Tenant e catálogo usam JPA/Spring Data para entidades e persistência de domínio, com operações SQL especializadas encapsuladas em repositórios quando necessárias à autorização e concorrência. Identidade e Spring Session preservam JDBC; SQL não pertence aos controllers nem aos serviços de aplicação. Flyway continua dono do schema, com Hibernate em `validate` e Open Session in View desativado. O [ADR-0008](adr/0008-layered-persistence-refactoring.md) registra a evolução da implementação JDBC inicial; as [convenções de código](CODING_STANDARDS.md) definem responsabilidades e limites.

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
Modelo de assinatura SaaS, expiração de reserva, frete, modelagem de variantes, arquitetura de deploy, RLS, armazenamento de imagens e estratégia de observabilidade. Registrar ADR antes de decisões irreversíveis.

## Fundação de identidade — SC-002 Etapa 1

O [ADR-0005](adr/0005-identity-authentication.md) registra a direção local Spring Security e aprovação de D1/D2/D3 com autorização exclusiva da fundação. A [implementação mínima](IDENTITY_FOUNDATION.md) acrescenta usuário global/credencial, política/encoder, migração aditiva e testes/benchmark com JDBC existente. Não há API de autenticação, ativação, sessão, tokens ou frontend; Etapas 2–6 dependem de nova autorização. Identidade global não concede tenant, empresa ou papel.

## Entrega funcional local de identidade

Após aprovação de D4–D6, a SC-002 acrescenta cadastro por e-mail, prova de posse seguida de definição explícita de senha, login/logout Spring Security e sessão PostgreSQL via Spring Session JDBC. O admin Next.js encaminha `/api` para o backend, mantendo mesma origem; links de prova usam fragmento, e mutações exigem CSRF. Transporte de mensagens restrito ao perfil DEV, sem envio externo. Sessão ociosa de 30 minutos, duração absoluta de oito horas e validação de status/auth_version em cada acesso protegido. Recuperação de senha e vínculos empresariais continuam fora desta entrega.

## SC-003 — Onboarding da loja

O módulo tenant implementa criação conjunta de empresa/loja/vínculo OWNER e leitura escopada pela identidade autenticada. O frontend lista somente vínculos próprios e consulta detalhes com validação backend; nenhum tenant_id informado pelo cliente concede acesso. Uma loja por empresa, criada em configuração, sem vitrine pública. [ADR-0006](adr/0006-tenant-store-onboarding.md).

## Catálogo básico autorizado

O módulo catalog implementa produto simples e consultas autorizadas por vínculo. A entrega inicial usava JDBC; a revisão do [ADR-0008](adr/0008-layered-persistence-refactoring.md) organiza suas camadas e introduz JPA, preservando autorização transacional, escopo de tenant e SQL especializado para atualização concorrente. Contrato monetário e concorrência: [ADR-0007](adr/0007-basic-catalog.md). Esta entrega foi autorizada posteriormente ao planejamento da Sprint 1; não inclui estoque, variantes ou vitrine.

## Estoque básico autorizado

Inventory mantém saldo físico inteiro e histórico auditável por produto. Serviço usa contrato de autorização do catálogo dentro da transação; repositórios concentram JPA e SQL especializado para inicialização concorrente/locks. Escritas idempotentes e serializadas impedem saldo negativo; leituras usam snapshot consistente e paginação por cursor. Sem reservas/pedidos ou novos serviços. [ADR-0009](adr/0009-basic-inventory.md).

## Vitrine pública autorizada

Storefront é modelo público de leitura no mesmo backend, com controllers/services/repositories e projeções restritas por slug publicado. Nova cadeia GET stateless não usa sessões administrativas. storefront-web é aplicação SSR separada, sem proxy administrativo, credenciais encaminhadas ou cache de catálogo. Publicação explícita da loja/produto e contratos: [ADR-0010](adr/0010-public-storefront.md).

## Evolução autorizada do catálogo

Fotos normalizadas em PostgreSQL, descrição HTML sanitizada e unidades vendáveis por combinação de opções evoluem os módulos catalog/inventory/storefront existentes. FKs compostas e autorização continuam obrigatórias; o estoque legado é preservado na variante padrão. Limites, contratos e decisões: [ADR-0011](adr/0011-catalog-media-description-variants.md).

## Carrinho, reservas e pedidos — proposta

O [ADR-0012](adr/0012-stock-reservation-cart-orders.md), ainda **proposto**, trata expiração de reserva e frete listados em decisões em aberto: carrinho sem estado no servidor, reserva por variante criada junto do pedido `PENDING_PAYMENT`, disponibilidade calculada por reservas não expiradas, frete fixo ou retirada e checkout público idempotente. Nada é implementado antes das aprovações D1–D7; plano em [SPRINT_03.md](../product/SPRINT_03.md).
