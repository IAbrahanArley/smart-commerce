# Convenções de código do backend

## Organização

Manter o monólito modular, com camadas dentro de cada funcionalidade. Criar apenas pacotes necessários:

```text
com.smartcommerce/
  identity/ | tenant/ | catalog/
    controllers/
    services/
    repositories/
    entities/
    domain/
    dto/request/
    dto/response/
```

Identidade também possui `security`, `mail` e `config` para seus adaptadores e configuração. Não criar módulos futuros, classes base genéricas ou interfaces sem uso demonstrado.

## Responsabilidades

- **Controllers:** rotas, parsing, validação de entrada, identidade autenticada e respostas. Sem SQL, EntityManager ou acesso direto a repositórios. Documentação OpenAPI pode ficar em interfaces `*Api` quando isso melhorar a leitura.
- **Services:** casos de uso, regras e fronteiras transacionais. Dependências por construtor; sem SQL/JdbcTemplate e sem criar manualmente adaptadores de persistência.
- **Repositories:** consultas e escritas, mapeamento, locks e tradução segura de falhas de persistência. Não chamar serviços/controllers nem produzir respostas ou exceções HTTP.
- **Entities/domain:** estado persistente e regras/valores; sem dependência de controllers. Métodos de alteração explícitos, sem setters indiscriminados ou cascatas entre módulos.
- **DTOs request/response:** contratos externos separados; nunca devolver entidades, hashes, tokens internos ou detalhes SQL. Preservar o contrato textual de dinheiro e as validações Unicode do domínio, que não se resumem a `@Size`.
- **Tratamento de erros:** traduzir falhas na fronteira HTTP, preservando respostas genéricas de autenticação, 404 uniforme para recursos não autorizados e códigos de conflito já consumidos pelo frontend. Não registrar causas SQL sem sanitização.

## Persistência e segurança

Tenant/catalog usam JPA/Spring Data com repositórios de aplicação; consultas nativas e JDBC ficam em repositórios especializados. Identidade, limites contra abuso e sessões mantêm JDBC conforme [ADR-0008](adr/0008-layered-persistence-refactoring.md). SQL parametrizado em repositórios é permitido; SQL em serviços não é.

Flyway é a fonte do schema. Usar `ddl-auto: validate` e `open-in-view: false`; mudanças exigem migrações aditivas quando necessárias. Manter constraints compostas e precisão monetária. Não alterar migrações anteriores para acomodar mapeamentos.

Ator deriva da autenticação; tenant enviado pelo cliente é somente seletor. Toda operação deve validar vínculo e escopo de tenant no backend, inclusive com JPA. Não expor consultas globais por ID ao fluxo de negócio de uma loja. Preservar locks de autorização e controle de versão durante a transação; não substituir garantias por uma checagem prévia sem proteção contra concorrência.

Spring Security, Spring Session JDBC, PBKDF2 e CSRF existentes permanecem. OpenAPI/Swagger somente em DEV, autenticado, com execução interativa desabilitada; exemplos usam dados sintéticos e não expõem tokens ou credenciais. Skills e documentação de API não autorizam novos acessos ou alterações de segurança.

## Verificação

Aplicar formatação existente, testes de regras relevantes e integração PostgreSQL/Testcontainers para os fluxos críticos. ArchUnit verifica dependências entre camadas; isso complementa, sem substituir, os testes A/B, revogação, unicidade concorrente, precisão monetária e controle de versão. Registrar comandos, resultados reais e impedimentos; não repetir benchmarks sem mudança relevante no hashing.

Origem dos padrões e adaptações: [ADR-0008](adr/0008-layered-persistence-refactoring.md). SEC-003 continua aberta, sem aceite automático.
