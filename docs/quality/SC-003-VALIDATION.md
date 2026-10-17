# SC-003 — Empresa e loja do proprietário

Data: 2026-10-17. Entrega autorizada pelo responsável após validação local da autenticação. Alterações locais, sem commit/push ou nova CI remota.

## Funcionalidade entregue

Entrar → criar empresa/loja → visualizar Minhas lojas → abrir detalhes. Empresa, membership OWNER e loja são criadas atomicamente; uma loja por empresa. IDs/papel definidos pelo servidor, sem contexto de tenant confiado ao browser. Loja DRAFT exibida como “Em configuração”, sem vitrine pública. [ADR-0006](../architecture/adr/0006-tenant-store-onboarding.md).

## Evidências

- Backend: **48 testes aprovados (20 unitários +28 integrados)**, zero falhas/erros/ignorados. Spotless, compilação, package e verify passaram. Conclusão17/10/2026 às09:03:42 BRT; log ignorado backend/target/sc003-verify.log e XML Surefire/Failsafe. Java21/Boot4.1.1/PostgreSQL18.6/Testcontainers2.0.5 existentes, sem novas dependências.
- Sete testes novos:1 de nomes/Unicode e6 HTTP/PostgreSQL. Dois proprietários só listam/consultam os próprios registros; outro tenant e UUID inexistente retornam404 equivalente. Cobrem sessão ausente, CSRF, origem, corpo excessivo, manipulação de owner/role/header/query, revogação, slug concorrente e rollback sem órfãos. Unknown JSON fields não atribuem IDs ou permissões.
- V5 aditiva cria tenant, tenant_membership e store, com FK, unicidade de slug e de store.tenant_id. V1–V4 preservadas. Aplicada também ao Compose local, sem apagar volumes.
- Frontend: lint, typecheck, build e smoke aprovados, incluindo rota /lojas/[id]. Sandbox apresentou EPERM/concorrência em artefatos Next na tentativa inicial; execução final única autorizada passou. Sem manifest/lockfile ou dependências novas.
- Navegador in-app: login com conta sintética; estado sem lojas; criação pelo formulário; listagem; detalhe; persistência após reload; identificador duplicado409 com erro e foco no campo. Detalhes também verificados a320 CSSpx sem overflow horizontal. A/B verificado pelos testes HTTP reais, sem alegar uma segunda sessão manual de browser.
- Interface usa DESIGN.md; revisão de código pelas Web Interface Guidelines consultadas em17/10/2026. Axe/leitor de tela/auditoria WCAG completa não executados; nenhuma dependência de browser adicionada à CI.

## Revisões e limites

Backend implementou módulo/migração e guards. Frontend reutilizou API CSRF e componentes/tokens. QA criou os testes e conferiu os relatórios da execução coordenada. Segurança revisou autorização/constraints/transação/404 sem achado bloqueante; apontou estado visual antigo ao trocar loja, corrigido por remount com key por ID e incluído no build final.

Sem edição/exclusão/publicação de loja, convites, funcionários, domínio comercial, pagamentos ou IA. Isolamento demonstrado para estes endpoints; cada módulo futuro exige seus próprios guards/testes. RLS e auditoria ampliada não foram implementados. Limites de abuso e configuração de produção precisam de avaliação antes de exposição pública.

**SEC-003 permanece aberta e inalterada, sem aceite de risco.** Benchmark de senhas não repetido. Revisão final de diff, links e Gitleaks do snapshot local sem segredos; índice Git vazio.

## Arquivos desta entrega

- backend/src/main/java/com/smartcommerce/tenant: controller, serviço, repositório JDBC, DTO e erros.
- V5__tenant_and_store.sql; ajustes de autorização e guard de origem/corpo da API.
- TenantIsolationIT, TenantServiceTest e atualização dos testes existentes para as rotas/tabelas autorizadas.
- Admin: conta, stores/store-detail, helper API/tipos, rota lojas/[id], CSS e smoke.
- ADR-0006, DATA_MODEL, ARCHITECTURE, README e este registro.

Alterações anteriores de autenticação/design continuam locais e foram preservadas. Próximo gate: aceite/revisão humana e autorização de publicação/CI. Nenhuma SC-004 ou funcionalidade comercial foi iniciada. Demonstração: [README](../../README.md).
