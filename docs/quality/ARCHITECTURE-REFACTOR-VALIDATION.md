# Validação — Refatoração de arquitetura

Data: 2026-10-17. Referência: project-rh, commit e790a56c3f8d6d110692600f74b94f6c0022c557. Decisão e convenções: [ADR-0008](../architecture/adr/0008-layered-persistence-refactoring.md), [padrões](../architecture/CODING_STANDARDS.md).

## Alterações

Camadas explícitas dentro de identity/tenant/catalog; DTOs request/response separados, entidades JPA para empresa/loja/vínculo/produto e injeção por construtor. Serviços de identidade/limites não executam mais SQL. Falhas de negócio/persistência usam razões sem status HTTP; handlers preservam message/code e status existentes. Repositórios não dependem de serviços/controllers/DTOs HTTP.

JPA é usado na persistência e consulta de tenant/catalog. SQL nativo/JDBC continua restrito a repositórios para autorização, locks e CAS; identidade e Spring Session mantêm JDBC. Flyway V1–V6 intactas, Hibernate somente validate, OSIV desativado e logs de erros SQL Hibernate desabilitados para evitar exposição de parâmetros. Sem mudança de API, senha, sessão, tema ou dados locais.

## Dependências verificadas

- Java 21 / Spring Boot 4.1.1 preservados.
- spring-boot-starter-data-jpa 4.1.1, Spring Data JPA 4.1.1, Hibernate 7.4.5.Final e Jakarta Persistence 3.2.0: versões gerenciadas pelo BOM existente e resolvidas pelo Maven.
- springdoc-openapi-starter-webmvc-ui 3.1.1: documentação oficial compatível com Boot 4; disponível apenas em DEV/autenticada, com execução interativa e persistência de autorização desabilitadas.
- ArchUnit Core 1.5.1 somente em testes; independente de engine adicional JUnit.

Fontes consultadas em 2026-10-17: [Spring Boot/JPA](https://docs.spring.io/spring-boot/reference/data/sql.html), [springdoc](https://springdoc.org/), [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html). Não copiar versões Boot 3.5/springdoc 2.x da referência.

## Verificações

BUILD SUCCESS em 09:56:20 -03:00: 62 testes (26 unitários, incluindo 3 regras arquiteturais, e 36 integrações), zero falhas/erros/ignorados. Maven Wrapper, JDK 21, Spotless e verify sobre PostgreSQL 18.6/Testcontainers. Log local ignorado: backend/target/architecture-final-verify.log; resultados XML em Surefire/Failsafe. As três regras ArchUnit verificam ausência de acesso direto ao banco por controllers/serviços, independência de repositórios em relação a serviços/HTTP e ausência de dependências HTTP nos serviços.

O teste adicional de transação confirma JpaTransactionManager real, mesmo pg_backend_pid() para JDBC e EntityManager, rollback conjunto de ambas as escritas e bloqueio da revogação de membership por conexão independente (SQLSTATE 55P03). O teste de documentação verifica negação anônima, acesso autenticado ao OpenAPI/Swagger, contrato de senha writeOnly e configuração sem execução de operações. Integrações anteriores preservam autenticação, revogação, CSRF, isolamento A/B, precisão e concorrência.

A primeira execução falhou por bloco logging duplicado no YAML; corrigido antes da regressão completa. Após mover pacotes, classes/relatórios gerados foram removidos especificamente para evitar bytecode antigo; caches Maven e dados locais preservados. Não houve repetição do benchmark. Nenhum frontend foi alterado nesta refatoração; lint/build/smoke do frontend não foram repetidos e seus resultados anteriores não são apresentados como execução atual.

## Revisões e limites

Architect analisou a referência e documentou as adaptações. Backend extraiu persistência de identidade e migrou tenant/catalog. Segurança revisou isolamento/locks/CAS, configuração de logs e documentação DEV; pediu comprovação JPA/JDBC incorporada ao teste. O agente QA atingiu seu limite de uso; o coordenador implementou e executou a verificação pendente.

Paginação do catálogo e otimização da listagem de lojas para volumes maiores continuam evolução futura. Sem alegação de prontidão para produção. SEC-003 aberta e inalterada, sem aceite de risco. Sem commit/push/CI remota desta entrega; alterações locais anteriores permanecem no mesmo checkout.

## Evidências finais

Login com conta sintética no navegador, consulta de lojas, detalhe e catálogo aprovados após reiniciar o JAR refatorado. Produto existente preservou preço R$ 54,90 e custo R$ 25,00. Backend disponível em 127.0.0.1:8080 e painel em 127.0.0.1:3000. Nenhuma credencial ou token foi incluído nas evidências.

Git diff --check aprovado; links locais e arquivos novos revisados; Gitleaks sem vazamentos no conjunto local de alterações e comparação silenciosa com segredos locais sem correspondências. Documentos SEC-003 e migrações preservados. Nenhum arquivo staged e nenhum commit ou push executado.
