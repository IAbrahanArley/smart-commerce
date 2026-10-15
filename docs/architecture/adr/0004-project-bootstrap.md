# ADR-0004 — Bootstrap do projeto

Status: aceito para SC-001 após aprovação do planejamento.

## Contexto

A SC-001 prepara execução local, migrações e integração contínua. Os ADRs 0001 e 0002 permanecem válidos. O modelo de domínio é preliminar e não autoriza antecipar autenticação, empresa/loja ou funcionalidades comerciais.

## Decisão

- Preservar os diretórios de raiz: `backend/`, `admin-web/`, `storefront-web/`, `infrastructure/` e `docs/`. A vitrine permanece reservada, sem aplicação funcional nesta tarefa.
- Backend: Java 21, Spring Boot e um único projeto/artefato Maven, com Maven Wrapper versionado. A separação futura dos módulos será por pacotes e contratos explícitos, mantendo o monólito modular.
- Admin: Node.js 24, npm com lockfile versionado, Next.js e TypeScript. Os patches exatos das ferramentas e dependências são definidos nos manifests, wrapper e lockfile após verificação de disponibilidade; este ADR não substitui esses arquivos como fonte de versões.
- Docker Compose executa somente PostgreSQL no ambiente local. A porta publicada usa loopback; backend e admin executam no host. Dados persistem em volume nomeado, sem limpeza automática na rotina de desenvolvimento.
- Flyway controla as migrações. A V1 cria somente `app_metadata`, para comprovar a migração inicial; não cria tabelas de usuários, tenants ou outras entidades de negócio. `app_metadata` é infraestrutura global e não armazena dados de lojistas.
- Configuração varia por ambiente. Credenciais são fornecidas por variáveis de ambiente; exemplos contêm apenas valores demonstrativos. Arquivos locais com segredos ficam fora do Git. Nenhuma credencial de produção é necessária para bootstrap ou CI.
- Healthcheck expõe somente informação operacional mínima. A prontidão verifica o banco; a vivacidade verifica o processo. A SC-001 não oferece autenticação funcional nem endpoints de negócio.
- GitHub Actions executa build, testes e verificações estáticas disponíveis, sem deploy. Testes de integração do backend usam PostgreSQL via Testcontainers e validam Flyway, evitando diferenças de comportamento de um banco substituto.

## Invariantes para as próximas tarefas

O tenant autorizado será derivado de identidade autenticada e vínculo de participação autorizado. Frontend, payload, header ou slug não constituem, isoladamente, prova de autorização. Um seletor apresentado pelo cliente exige validação desse vínculo pelo backend.

Serviços e repositórios devem aplicar autorização e escopo de tenant. Ao criar o domínio, as constraints e referências devem preservar a integridade entre registros do mesmo tenant. Testes negativos entre tenants A/B serão implementados na SC-003 e consolidados na SC-004. A SC-001 não demonstra isolamento de dados de negócio, pois ainda não os possui.

RLS continua pendente de ADR específico, como defesa adicional. A estratégia de autenticação permanece para SC-002. A infraestrutura inicial não resolve essas decisões implicitamente.

## Consequências e critérios verificáveis

- Um único backend e um único banco reduzem a complexidade inicial; a revisão deve impedir acoplamento indevido quando os módulos forem criados.
- Um clone limpo deve conseguir iniciar PostgreSQL, aplicar V1, iniciar backend e admin seguindo comandos verificados no README.
- CI deve falhar se build, teste, lint ou checagem de tipos configurados falharem; testes de integração exigem Docker disponível.
- O diff da SC-001 não inclui funcionalidades de domínio, pagamentos, agentes de IA do produto, infraestrutura paga ou deploy.
- Autenticação, autorização tenant e testes A/B são entregas futuras explícitas, não garantias já fornecidas pelo bootstrap.
