# Sprint 1 — Fundação (planejamento)
**Objetivo:** repositório preparado e fluxo de criação de empresa autenticado com isolamento básico.

## SC-001 — Bootstrap
- Criar monorepo e documentação de execução.
- Backend Spring Boot Java 21; admin-web Next.js TypeScript; PostgreSQL Docker Compose.
- Healthcheck, Flyway, configuração por ambiente, .env.example sem segredos.
**Aceite:** backend e admin sobem localmente; CI executa build e testes básicos; README contém comandos verificados.

## SC-002 — Identidade e autenticação
- Definir abordagem de autenticação por ADR (Spring Security + provedor OIDC ou sessão própria).
- Criar cadastro, login e proteção de endpoints, com testes.
**Aceite:** acesso sem autenticação bloqueado; credenciais protegidas; testes negativos.

## SC-003 — Tenant e loja
- Criar entidade Empresa/Loja e vínculo de proprietário.
- Endpoint de criação e consulta da loja autenticada; slug único.
**Aceite:** proprietário cria loja; outro usuário não acessa nem altera seus dados.

## SC-004 — Segurança e observabilidade
- Auditoria mínima de criação de loja; logs estruturados e tratamento padronizado de erros.
- Testes de isolamento tenant A/B e documentação de ameaças.
**Aceite:** tentativa de acesso cruzado resulta em negação e é coberta por testes.

## Definition of Done
Testes executados, lint/build sem falhas, revisão de segurança, migrações versionadas, docs atualizados e aprovação humana.

## Sequência
Executar SC-001 isoladamente primeiro. Não implementar pagamentos, catálogo nem agentes de IA do produto nesta sprint.
