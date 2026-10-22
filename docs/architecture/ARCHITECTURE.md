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

## Fluxo de pagamento
Lojista autoriza Mercado Pago via OAuth → credenciais protegidas por tenant → pedido pendente + reserva de estoque → Checkout Pro → webhook validado + consulta de status → transição idempotente → baixa de estoque + registros financeiros → reconciliação. Confirmar requisitos atuais e compatibilidade do provedor antes de implementar.

Implementado e revisado até a SC-017 (sandbox, validação real pendente, [ADR-0013](adr/0013-mercado-pago-checkout-pro.md)): OAuth com tokens cifrados por tenant (SC-012), criação da order do Checkout Pro a partir do pedido (SC-013) webhook assinado com confirmação por consulta e baixa de estoque (SC-014) conciliação periódica pelo mesmo caminho de confirmação (SC-015) e reembolso total controlado pelo lojista (SC-016). O módulo `orders` confere o comprador e o pedido e entrega ao `payments` só o snapshot. O `payments` chama o provedor fora de transação, com o token da loja do pedido e uma chave de idempotência derivada do pedido. A dependência é de `orders` para `payments`, nunca o contrário: na confirmação, `payments` chama a interface `PaymentApprovalListener`, implementada em `orders`, dentro da mesma transação que aprova o pagamento. O webhook só grava o evento; uma rotina consulta `GET /v1/orders/{id}` com o token da loja do pedido e, se estiver pago, aprova o pagamento, marca o pedido `PAID` e baixa o estoque (movimento `SALE` do ator de sistema). A conciliação consulta, com `SKIP LOCKED`, os pagamentos ainda não confirmados (a cada minuto enquanto a order está aberta e por 24 h depois do vencimento). O reembolso é pedido pelo OWNER no admin, enviado ao provedor com chave de idempotência por pedido e confirmado pela mesma consulta: só uma order `refunded` leva o pedido a `REFUNDED`, com devolução ao estoque (`SALE_REVERSAL`) apenas se escolhida.

## Livro financeiro (Sprint 5)
O módulo `finance` guarda os fatos financeiros num livro só de acréscimo (`finance_entry`, [ADR-0015](adr/0015-finance-dashboard.md)): venda e CMV quando o pedido é pago, reembolso e eventual estorno do CMV quando é reembolsado, tudo na mesma transação da mudança de estado e com chave idempotente por fato. As despesas manuais (SC-020) também ficam no módulo: cada despesa gera um lançamento, e uma correção cancela a anterior com um lançamento de estorno, sem apagar nada. Os relatórios e o dashboard leem só o livro: o relatório do período (SC-021) soma os lançamentos por competência no calendário de São Paulo, separa a receber e caixa pela liquidação e marca a margem como parcial quando faltam taxas ou custos. O dashboard (módulo `analytics`, SC-022) só compõe o relatório, as vendas por dia do livro e as listas de estoque baixo e sem giro do `inventory`, sem cálculos próprios. A dependência é de `orders` para `finance` e de `analytics` para `finance` e `inventory`.

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

## Carrinho, reservas e pedidos

O [ADR-0012](adr/0012-stock-reservation-cart-orders.md) foi adotado para D1–D7 em 2026-10-17: carrinho sem estado no servidor, reserva por variante criada junto do pedido `PENDING_PAYMENT`, disponibilidade calculada por reservas não expiradas, frete fixo ou retirada e checkout público idempotente. Plano em [SPRINT_03.md](../product/SPRINT_03.md).

Entregues SC-005 a SC-008. O inventory mantém `inventory_reservation` e a função SQL única `inventory_available_quantity`, usada pela vitrine, pela cotação e pelo admin, com o instante do `Clock` do backend. O serviço interno de reserva não tem endpoint e será chamado pelo módulo orders (SC-008). O tenant mantém a configuração de entrega 1:1 com a loja, com leitura pública das opções ativas. O storefront ganha `POST /api/public/stores/{slug}/cart/quote`, stateless e sem gravação; o storefront-web guarda o carrinho em cookie HttpOnly por loja e mutações por server action. A cadeia pública continua sem sessão e aceita somente esse POST além dos GET.

Na SC-008, o módulo `orders` (controllers/services/repositories/domain/DTOs, ADR-0008) recebe o checkout público: resolve a loja publicada pelo slug, revalida cada variante nessa loja, calcula totais no backend, confirma o total visto pelo comprador e grava pedido, itens com snapshot e reservas numa única transação READ COMMITTED, usando o serviço interno de reservas do inventory. Idempotência por `requestId` com impressão digital do payload; token do comprador guardado só como hash. Limites do D4 em PostgreSQL: pedidos pendentes por e-mail e loja e tentativas por IP por hora, com o IP assinado por HMAC pelo servidor do storefront-web. A cadeia pública aceita os POSTs de cotação e checkout e o GET do pedido, continua sem sessão e cookies, e o checkout só é habilitado por configuração (D6).

Na SC-009, o storefront-web ganha `/lojas/{slug}/checkout` e `/lojas/{slug}/pedidos/{id}`, ambos SSR e ativos só com `STOREFRONT_CHECKOUT_ENABLED=true`. A server action lê os itens do cookie do carrinho, nunca do formulário, e chama o checkout do backend. O token do pedido fica num cookie HttpOnly limitado ao caminho do próprio pedido e segue ao backend como `X-Order-Token`. O IP do visitante só é assinado quando a implantação declara um proxy confiável (`STOREFRONT_TRUST_FORWARDED_FOR`), porque o Next não sobrescreve `X-Forwarded-For`. [Validação](../quality/SC-009-VALIDATION.md).

Na SC-010, o módulo `orders` ganha a área do lojista (`/api/tenants/{id}/orders`: lista por cursor, detalhe e `POST .../{orderId}/cancel`), sempre com o vínculo OWNER e uma identidade ativa na própria query, e 404 uniforme para pedido de outra loja. O cancelamento trava a linha do pedido e depois o estoque (mesma ordem em todo lugar), faz a troca de estado condicional e libera as reservas na mesma transação. Duas rotinas `@Scheduled` no processo do backend (`OrderMaintenanceJob`) usam lotes com `FOR UPDATE SKIP LOCKED`:
- a expiração, a cada minuto, fecha as reservas vencidas sem travar o estoque, porque elas já não contam no disponível;
- a anonimização D2, diária.

O admin-web ganha `/lojas/{id}/pedidos` (filtro e página na URL) e o detalhe com cancelamento. [Validação](../quality/SC-010-VALIDATION.md).

A SC-011 revisou o fluxo inteiro ([modelo de ameaças](../security/SC-011-CHECKOUT-THREAT-MODEL.md)):

- o limite por IP do checkout agrupa IPv6 por /64 (`ClientIpBucket`);
- as rotinas esvaziam a fila dentro de um limite de tempo;
- a anonimização cobre o motivo de cancelamento;
- a V16 valida a FK reserva → pedido;
- o admin só aceita IDs de rota em formato UUID.

[Validação](../quality/SC-011-VALIDATION.md).
