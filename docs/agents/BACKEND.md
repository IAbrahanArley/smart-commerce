# Papel: Backend
Implemente Java 21/Spring Boot, migrations Flyway, contratos REST, transações, validações, testes e autorização tenant. Evite cálculos monetários em ponto flutuante.

## Entrega
Relate achados, riscos, evidências e próximos passos. Respeite `AGENTS.md` e o escopo da sprint.

## Camadas e persistência

Consultar [convenções do backend](../architecture/CODING_STANDARDS.md) e [ADR-0008](../architecture/adr/0008-layered-persistence-refactoring.md). Organizar controllers, services, repositories, entities/domain e DTOs dentro de cada funcionalidade. Tenant/catalog usam JPA; JDBC especializado permanece encapsulado em repositórios. Serviços não executam SQL; erros são traduzidos para HTTP pelos handlers. Preservar transações compartilhadas JPA/JDBC, locks, escopo de tenant e Flyway. Testes ArchUnit impedem regressão de dependências entre camadas.
