# ADR-0008 — Camadas explícitas e persistência de domínio

Status: adotado na revisão autorizada em 2026-10-17; validações da implementação são registradas separadamente.

## Contexto

A organização inicial por funcionalidade tinha controllers, serviços e repositórios, mas misturava SQL no serviço de autenticação e erros HTTP na persistência. O responsável solicitou revisão e autorizou usar [project-rh](https://github.com/IAbrahanArley/project-rh/tree/e790a56c3f8d6d110692600f74b94f6c0022c557) como referência, inclusive dependências necessárias.

Na referência, `controllers/JobOpeningController` implementa o contrato `JobOpeningApi`; `services/JobOpeningService` usa injeção por construtor e transações; `repositories/JobOpeningRepository` usa Spring Data JPA; entidades encapsulam alterações; DTOs request/response e tratamento de erros ficam separados. Sua configuração mantém Flyway, `ddl-auto: validate` e `open-in-view: false`.

## Decisão

- Preservar monólito modular e pacotes por funcionalidade. Adotar as camadas da referência **dentro** de identity, tenant e catalog, sem transformar o backend em pacotes globais por camada. Convenções: [CODING_STANDARDS.md](../CODING_STANDARDS.md).
- Controllers tratam transporte; serviços coordenam regras/transações; repositórios encapsulam acesso ao banco; entidades e objetos de domínio preservam invariantes; DTOs de entrada/saída não expõem entidades ou credenciais.
- Usar JPA/Spring Data em tenant e catalog, com fachada de repositório. Manter SQL nativo ou JDBC em repositórios especializados para autorização, locks e compare-and-swap. JPA não substitui verificação de vínculo nem escopo de tenant.
- Extrair SQL do serviço de autenticação para repositórios. Preservar JDBC na identidade, nos contadores contra abuso e em Spring Session, sem reescrever fluxos sensíveis apenas para uniformizar tecnologia.
- Preservar V1–V6, IDs, contratos HTTP, parâmetros PBKDF2, sessões, CSRF, ativação e constraints de tenant. Flyway altera schema; Hibernate somente valida. Não habilitar Open Session in View nem serializar entidades JPA nos endpoints.
- Separar erros de negócio/persistência de sua tradução HTTP. Preservar códigos e mensagens consumidos pelo frontend; não propagar detalhes SQL, hashes, tokens ou dados pessoais.
- Documentar a API com springdoc/OpenAPI disponível apenas em DEV, autenticada e com execução interativa desabilitada. Documentar sessão/cookie e CSRF existentes; não apresentar autenticação bearer/JWT. Documentação não amplia rotas públicas nem muda controles de origem.
- Verificar regras de dependência entre camadas com ArchUnit nos testes, além dos testes de integração existentes.

## Dependências e limites

Java 21 e Spring Boot 4.1.1 permanecem. JPA/Hibernate e Spring Data seguem o BOM do projeto; springdoc 3.1.1 e ArchUnit Core 1.5.1 são as dependências selecionadas nesta revisão, com compatibilidade e execução registradas na validação. Não copiar versões do project-rh, que usa Boot 3.5.0 e springdoc 2.8.9.

Não adotar JWT, Angular, AWS, Lombok ou MapStruct por estarem na referência. Não copiar autorização global por ADMIN ou consultas por ID sem escopo. Identidade autorizada e vínculo determinam acesso; relacionamentos entre módulos não devem gerar cascatas ou grafos JPA extensos.

## Consequências e aceite

Camadas ficam visíveis e SQL sai dos serviços, ao custo de mapeamentos JPA e duas tecnologias de persistência deliberadamente delimitadas. A introdução de JPA não promete eliminar SQL nem resolve multi-tenancy automaticamente.

O aceite exige build/testes executados, regras arquiteturais, fluxos de autenticação e isolamento A/B preservados, unicidade e atualizações concorrentes corretas, valores monetários sem arredondamento e acesso controlado à documentação DEV. Resultados e limitações pertencem ao registro de validação; este ADR não declara testes aprovados.

SEC-003 permanece aberta e inalterada, sem aceite formal ou automático de risco. Esta revisão não autoriza novas funcionalidades, deploy, commit ou push.
