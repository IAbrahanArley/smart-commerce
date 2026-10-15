# SC-001 — Entrega do bootstrap

Data: 2026-10-15. Implementação preparada para revisão; aceite integral pendente das validações e do achado de segurança descritos abaixo. SC-002 não foi iniciada.

## Inventário das alterações

Arquivos existentes modificados: `README.md`, `.gitignore`, `docs/architecture/ARCHITECTURE.md`.

Arquivos criados:

```text
.gitattributes
.github/workflows/ci.yml
backend/
  pom.xml
  mvnw
  mvnw.cmd
  .mvn/wrapper/maven-wrapper.properties
  src/main/java/com/smartcommerce/SmartCommerceApplication.java
  src/main/java/com/smartcommerce/bootstrap/BootstrapSecurityConfiguration.java
  src/main/resources/application.yml
  src/main/resources/db/migration/V1__bootstrap.sql
  src/test/java/com/smartcommerce/BootstrapIT.java
  src/test/java/com/smartcommerce/bootstrap/BootstrapSecurityTest.java
admin-web/
  .env.example
  package.json
  package-lock.json
  tsconfig.json
  next.config.ts
  next-env.d.ts
  eslint.config.mjs
  scripts/smoke.mjs
  src/app/layout.tsx
  src/app/page.tsx
  src/app/loading.tsx
  src/app/error.tsx
  src/app/not-found.tsx
  src/app/globals.css
infrastructure/
  compose.yaml
  .env.example
  postgres/init-app.sh
scripts/start-backend.ps1
docs/architecture/adr/0004-project-bootstrap.md
docs/quality/SC-001-VALIDATION.md
docs/quality/SC-001-DELIVERY.md
docs/quality/SC-001-PUBLICATION.md
docs/security/SC-001-REVIEW.md
```

Removidos somente marcadores vazios `.gitkeep` em `backend/`, `admin-web/` e `infrastructure/`. O marcador em `storefront-web/` permanece. Os documentos de produto, ADRs 0001–0003, `AGENTS.md` e configurações dos agentes foram preservados.

Git foi inicializado em `main`. A revisão inicial usou intent-to-add; este conjunto de 62 arquivos revisados compõe o commit inicial local autorizado. Destino/remoto e push aguardam definição, com repositório privado inicialmente. `infrastructure/.env` foi preparado localmente com senhas aleatórias distintas e está ignorado, assim como caches, dependências instaladas, logs de build e o JAR gerado. Esses artefatos locais não integram a entrega versionável.

## Versões efetivamente utilizadas

| Componente | Versão |
| --- | --- |
| Java | Amazon Corretto 21.0.9, selecionado por processo |
| Spring Boot | 4.1.1 |
| Spring Security | 7.1.1, gerenciado pelo Boot |
| Maven / Wrapper | 3.10.0 / 3.3.4; checksum da distribuição fixado |
| Flyway | 12.4.0, gerenciado pelo Boot |
| Driver PostgreSQL | 42.7.13, gerenciado pelo Boot |
| Testcontainers / JUnit Jupiter | 2.0.5 / 6.0.3 |
| Spotless / google-java-format | 3.10.4 / 1.28.0 |
| Node / npm | 24.12.0 / 11.21.0 |
| Next.js / React e React DOM | 16.4.1 / 19.3.1 |
| TypeScript / ESLint | 5.9.3 / 9.39.5 |
| Docker Compose disponível | 2.40.3 |
| Docker Engine no reteste | 29.1.3 |
| PostgreSQL executado | 18.6-bookworm, digest fixado; Compose e Testcontainers aprovados |

Spring Boot e dependências foram confirmados no Initializr/Maven Central e resolvidos por Maven. Frontend foi confirmado pelo registry npm e reinstalado pelo lockfile. A imagem PostgreSQL foi executada no reteste por Compose e Testcontainers. Os manifests e o lockfile são as fontes completas das dependências; a árvore Java resolvida está no artefato local ignorado `backend/target/dependency-tree.txt`.

## Contribuições dos agentes

| Papel | Implementação ou revisão |
| --- | --- |
| Arquitetura | ADR-0004, limites do monólito, distinção entre metadados técnicos e domínio, contexto de tenant por identidade e vínculo autorizado; atualização de ARCHITECTURE.md. |
| Backend | Scaffold oficial, verificação de artefatos, Wrapper/checksum, JDBC/Flyway/app_metadata, health/probes, barreira HTTP e testes; compilação, JAR e reteste completo aprovado após restabelecer Docker; ajuste da expectativa JSON de health. |
| Frontend | App Router/TypeScript, shell acessível, estados básicos, lockfile, smoke HTTP e checks após instalação limpa; investigação dos alertas npm e compatibilidade de peers. |
| QA | Quatro testes negativos do helper, revisão de exclusões Git/CI/README e leitura dos relatórios finais; sugestão de recuperação da prontidão; consolidação dos seis testes backend aprovados e evidências locais, preservando limites e histórico. |
| Segurança | Revisão de configuração, segredos, HTTP, Compose, CI e advisory; sugestões de checksum Maven e checkout sem persistência de credenciais incorporadas; SEC-003 mantida aberta. |
| Coordenador | Git, Compose/init PostgreSQL sem superuser da aplicação, helper, workflow, README e consolidação; validação de sintaxe/CI, Compose real, papel PostgreSQL, HTTP, persistência, reinicialização e restauração do ambiente do helper. |

## Resultados, problemas e pendências

- Aprovados: resolução Maven, formatação/compilação/empacotamento; seis testes backend (três segurança e três integração), zero falhas/erros/ignorados; instalação limpa npm, lint, tipos, build e smoke HTTP; sintaxe/helper negativo, exclusões Git e whitespace; parse/referências do workflow.
- Reteste backend: `verify` concluiu com saída 0 e `BUILD SUCCESS` às 13:15:38 -03:00, registrado em `backend/target/bootstrap/retest.log` e relatórios Surefire/Failsafe. Integrações comprovaram V1/Flyway, reaplicação sem nova migração, HTTP e indisponibilidade/recuperação do banco.
- Reteste local: Compose saudável; autenticação pelo papel da aplicação e cinco flags administrativas falsas; banco pertencente a `smart_commerce`. Helper iniciou aplicação real, onze verificações HTTP passaram com banco disponível; três verificações passaram com banco parado (readiness/health 503 sem detalhes, liveness 200); após reinício, onze passaram novamente e marcador/V1 foram preservados. O helper restaurou as três variáveis DB_* verificadas com sentinelas no finally após parada intencional da JVM filha; caminho de erro validado, sem comprovar Ctrl+C gracioso. Falhas Maven ao final desses logs decorrem da parada controlada, não do startup; o ciclo `verify` independente passou.
- Segundo startup pelo helper às 13:18:08 -03:00 validou uma migração e confirmou schema atualizado sem reaplicação, com onze verificações HTTP aprovadas. Somente JVMs dos testes foram encerradas; PostgreSQL permanece saudável e o volume foi preservado. Não houve alteração de código da aplicação ou helper nesta rodada.
- Histórico: o primeiro `verify` falhou antes dos métodos por Docker indisponível; após o usuário restabelecer Docker, a primeira integração encontrou uma expectativa JSON literal incompatível com `groups` de health do Boot 4. Corrigido somente `BootstrapIT` para analisar status UP e ausência de details/components; execução seguinte passou. Os testes não foram desabilitados.
- Docker Desktop 4.55.0 apresentou inicialmente falha do Inference manager/socket dockerInference; o bloqueio foi superado pelo usuário e Engine 29.1.3 funcionou no reteste. Não houve reset nem exclusão de dados pelos agentes. Java global 8 foi contornado selecionando JDK 21 no processo. Restrições de rede e SWC do sandbox foram contornadas com execuções autorizadas fora dele.
- Formatter incompatível com Java 21 e parsing inicial de tag/digest do Testcontainers foram corrigidos antes do resultado final. CI usa `bash ./mvnw`, evitando depender de bit executável no checkout Windows.
- Auditoria npm completa: cinco alertas altos, mesma origem transitiva de desenvolvimento, sem patch publicado; produção com `--omit=dev`: zero alertas. ESLint 9 compatível, porém fora de suporte. Sem downgrade forçado, redução de regras ou exceção de segurança presumida.
- Frontend e auditoria npm não foram repetidos no reteste Docker, pois não houve mudança nesses arquivos. Posteriormente, segurança repetiu a auditoria em 2026-10-15 às 16:24–16:27 America/Sao_Paulo: completa saída 1/cinco altas/zero críticas; produção saída 0/zero alertas. QA confirmou contagens nos JSONs locais. Braces latest 3.0.3 continua sem patch; fast-glob latest 3.3.3 mantém a cadeia. Sem upgrade, alteração de lockfile ou exceção: SEC-003 continua aberta.
- Publicação autorizada no destino confirmado `https://github.com/IAbrahanArley/smart-commerce.git`, privado antes do push. Origin/main configurado, commit inicial `4e3daaa` publicado sem sobrescrever histórico. Primeiro run remoto 38797506400 aprovou admin, mas falhou no setup backend porque Corretto só aceita major, não o seletor `21.0.9`; build/testes backend não foram executados nessa tentativa.
- Correção compatível `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`: seletor `21` e logs de versões no workflow, sem alterar código ou dependências. [Run 38797904235](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235) aprovou backend/admin: seis testes backend sem falhas/erros/ignorados, lint/tipos/build/smoke admin aprovados. Corretto remoto 21.0.12.12.1 / Java 21.0.12.1, Maven 3.10.0, Node 24.12.0, npm 11.6.2; runner 2.337.0 / Ubuntu 24.04.5. CI remota comprovada, aceite humano final e SEC-003 ainda pendentes.
- Migração das actions aprovada e aplicada após reconfirmação oficial dos commits às 16:38 BRT: checkout v7.0.1 (`3d3c42e5aac5ba805825da76410c181273ba90b1`), setup-java v6.1.0 (`b843ebf9b913146b38da2eaa7aeddf76074ae1ad`) e setup-node v7.1.0 (`949feb2413d6458794dcd2491c4babbbce0c15c1`), todas runtime Node 24. `cache-jdk: false`, Node 24.12.0 e dependências preservados. O input Corretto foi corrigido de patch para major `21` após a primeira tentativa remota; a CI seguinte passou. `workflow_dispatch` permanece habilitado.
- Pré-publicação: snapshot examinado de 61 candidatos/347.931 bytes, zero caminhos sensíveis e zero correspondências com dois segredos locais comparados em memória pelo coordenador. Gitleaks 8.30.1 verificado por checksum retornou 0/zero achados; cópia do lockfile sob nome neutro também passou, com hash idêntico confirmado por QA. Esses resultados pertencem ao snapshot: regenerar inventário e scan após concluir documentos. Não certificam ausência absoluta de segredos nem histórico Git.
- `.gitignore` ampliado para credenciais npm, certificados, logs e dumps; README usa placeholder em lugar do caminho pessoal encontrado. `.env`, `.tools` e artefatos de build permanecem excluídos. O destino privado foi confirmado antes dos pushes; scans do histórico não encontraram vazamentos. Publicação e CI verde não aceitam SEC-003 nem liberam SC-002.

Preparação final para GitHub: preflight do índice com 62 arquivos sem caminhos locais sensíveis, sem correspondências com os dois valores secretos locais; Gitleaks do candidato e lockfile examinados sem achados; actionlint aprovado. Os testes foram repetidos remotamente e aprovados após a correção do seletor Corretto. Nenhuma dependência da aplicação foi atualizada e nenhuma exceção foi aceita. A última auditoria identificada mantém cinco altas de tooling e zero alertas de produção; CI verde não altera esse resultado.

Detalhes: [validação](SC-001-VALIDATION.md), [segurança](../security/SC-001-REVIEW.md), [preparação de publicação](SC-001-PUBLICATION.md), [execução local](../../README.md).

Integrações, fluxo local e CI remota foram comprovados. Aceite integral ainda depende de revisão humana final e da resolução ou decisão explícita sobre o achado de tooling. SC-002 exige nova aprovação e ADR de autenticação. Entidades de negócio e testes A/B permanecem em SC-003/004.
