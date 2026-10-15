# SC-001 — Registro de validação

Data: 2026-10-15. Estado: **validação técnica local/remota reconhecida pelo usuário; SEC-003 aberta, sem aceite de risco**.

Este documento distingue verificações executadas, revisão estática e testes ainda não concluídos. Não certifica o isolamento de dados de negócio: SC-001 não cria usuários, tenants, catálogo, estoque ou pagamentos.

## Verificações executadas

| Verificação | Resultado | Evidência e limite |
| --- | --- | --- |
| Helper PowerShell: porta fora da faixa | Aprovado | `scripts/start-backend.ps1 -EnvironmentFile <temporário>` com `DB_PORT=70000` recusou antes de invocar Maven. |
| Helper: identificador inválido | Aprovado | `DB_NAME=invalid-name` recusado com validação de nome. |
| Helper: senha ausente | Aprovado | `DB_PASSWORD=` recusado como variável obrigatória ausente. |
| Helper: formato não permitido | Aprovado | Senha fictícia entre aspas recusada pela validação do formato. Os quatro arquivos temporários foram removidos ao final. |
| Exclusões do Git | Aprovado | `git check-ignore` confirmou `.env` de infraestrutura/backend, `.env.local` do admin, `backend/target/`, `.next/` e `node_modules/`; os dois `.env.example` não foram ignorados. |
| Sintaxe PowerShell | Aprovado, executado pelo coordenador | Parser de `scripts/start-backend.ps1`, sem erros reportados. Não equivale à inicialização do backend. |
| Compose | Aprovado, executado pelo coordenador | `docker compose --env-file infrastructure/.env -f infrastructure/compose.yaml config --quiet`. Valida configuração; não inicia banco. |
| JDK | Aprovado, executado pelo coordenador | Corretto Java 21.0.9 encontrado e executado; Java padrão 1.8 precisa ser substituído no processo de execução. |
| Backend: formatação, compilação e empacotamento | Aprovado | Reteste completo `verify` pelo backend terminou com `BUILD SUCCESS`, saída 0. QA leu `backend/target/bootstrap/retest.log`, finalizado às 13:15:38 -03:00; JAR de aproximadamente 31 MB gerado. |
| Testes de segurança backend | Aprovado | Relatório final `backend/target/surefire-reports/com.smartcommerce.bootstrap.BootstrapSecurityTest.txt` lido por QA: 3 testes, 0 falhas, 0 erros, 0 ignorados. Cobrem rotas permitidas/negadas, CSRF habilitado e ausência de usuário gerado no slice. |
| Backend: integração via `verify` | Aprovado no reteste | Relatório final `backend/target/failsafe-reports/com.smartcommerce.BootstrapIT.txt` lido por QA: 3 testes, 0 falhas, 0 erros, 0 ignorados. Flyway V1/histórico/reexecução sem migração, health HTTP sem details/components, bloqueios 403 e pausa/readiness 503/liveness 200/recuperação 200 aprovados. Somados aos três testes de segurança, são seis testes aprovados. |
| Compose: inicialização real | Aprovado, executado pelo coordenador | Com Docker Engine 29.1.3 restabelecido, `up -d --wait postgres` terminou com saída 0 e serviço saudável; `init-app.sh` executou. |
| Compose: autenticação e privilégios do papel | Aprovado, executado pelo coordenador | Conexão real autenticou `smart_commerce` no banco `smart_commerce`; superuser, createdb, createrole, replication e bypassrls retornaram false. Nenhuma senha foi impressa. |
| Backend local pelo helper | Aprovado, executado pelo coordenador | `scripts/start-backend.ps1` iniciou backend Java 21 conectado ao Compose, às 13:16:42 -03:00. Após encerrar intencionalmente somente a JVM do teste, restauração das três variáveis DB_URL/DB_USERNAME/DB_PASSWORD confirmada com sentinelas; `.tools/helper-first-restore.json` registra três resultados true, sem valores secretos. Valida o finally no caminho de erro/encerramento; Ctrl+C gracioso não foi comprovado. |
| HTTP local: banco disponível | Aprovado, executado pelo coordenador | Onze verificações: três GETs health/probes 200 com status UP e somente status/groups; seis rotas negadas 403 (`/login`, `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump`, `/actuator/health/db`, `/api/tenants`); tentativa com X-Tenant-ID/Authorization/cookie fictícios 403; POST health 403. |
| HTTP local: parada e recuperação do banco | Aprovado, executado pelo coordenador | Com `stop postgres`: readiness 503, liveness 200 e health agregado 503 sem detalhes. Após `up` saudável, as onze verificações HTTP anteriores passaram novamente. |
| Compose: persistência | Aprovado, executado pelo coordenador | Snapshots SQL antes/depois da parada e reinício preservaram `bootstrap=SC-001` e V1 com success=true; volume mantido. Proprietário do banco confirmado como `smart_commerce`. |
| Reinicialização da aplicação pelo helper | Aprovado, executado pelo coordenador | Segundo startup às 13:18:08 -03:00; `.tools/helper-restart.log` confirmou validação de uma migração e schema atualizado, sem migração necessária. As onze verificações HTTP passaram novamente. Somente JVMs do teste foram encerradas; PostgreSQL permaneceu saudável e volume preservado. |
| Maven Wrapper | Aprovado nas verificações reportadas | Backend/coordenador confirmaram Maven 3.10.0, wrapper 3.3.4, JDK 21.0.9 e checksum da distribuição validado. |
| Admin: lint e tipos | Aprovado, executado pelo frontend | `npm run lint` e `npm run typecheck` concluídos com sucesso. |
| Admin: instalação limpa pelo lockfile | Aprovado, executado pelo frontend | `npm ci` final concluiu com 354 pacotes e saída 0. Lint, tipos, build e smoke foram reexecutados após essa instalação, todos com saída 0. |
| Admin: build de produção | Aprovado, executado pelo frontend | `npm run build` concluído; necessária execução escalada devido a restrição de canonicalização do SWC no sandbox. |
| Admin: smoke de produção | Aprovado, executado pelo frontend | `npm run test:smoke`: home HTTP 200, HTML pt-BR, main/h1 esperados e página inexistente HTTP 404. É teste HTTP, sem certificação visual ou de acessibilidade completa. |
| Whitespace Git | Aprovado na conferência registrada | Após `git add --intent-to-add .`, `git diff --check` retornou 0 com os arquivos novos incluídos. Nova conferência do índice integra o fechamento do commit inicial local. |
| CI: sintaxe e referências | Aprovado, executado pelo coordenador | Parse de `.github/workflows/ci.yml` com Node/js-yaml; scripts npm existentes e actions fixadas por SHA hexadecimal de 40 caracteres confirmados. Não comprova execução remota nem verificação externa dos commits. |
| CI: actionlint após migração | Aprovado, executado pelo coordenador | actionlint 1.7.12, checksum oficial verificado, retornou 0 após migração aprovada das actions para runtime Node 24 e novamente após correção do seletor Corretto. A execução remota aprovada está registrada abaixo. |
| Pré-publicação: inventário e segredos locais | Aprovado para o snapshot examinado | Coordenador gerou `.tools/publication-preflight.json`: 61 candidatos, 347.931 bytes, zero achados; comparou dois valores secretos locais em memória sem imprimi-los. QA leu somente metadados e confirmou ausência de caminhos sensíveis no manifesto; não abriu `.env`. Regerar snapshot após alterações finais dos documentos. |
| Pré-publicação: Gitleaks | Aprovado para o snapshot examinado | Coordenador executou Gitleaks 8.30.1 com checksum oficial validado, modo dir e `--redact`: saída 0, relatório vazio. Como nomes de lockfiles são ignorados por padrão, examinou cópia do lockfile sob nome neutro separadamente, também saída 0/zero achados. QA confirmou hash idêntico dessa cópia ao lockfile real e os relatórios vazios. Não equivale a garantia de ausência de segredos nem scan de histórico Git. |

Os testes negativos do helper usaram valores fictícios e temporários criados com `GetTempFileName`; nenhum valor secreto foi impresso.

## Revisão estática

- Compose publica PostgreSQL em loopback, usa volume persistente e exige senhas fornecidas pelo ambiente.
- O script de inicialização cria o papel da aplicação com `NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION`. A aplicação recebe a credencial desse papel; autenticação e flags foram comprovadas no PostgreSQL do Compose conforme tabela acima.
- O helper importa apenas as variáveis necessárias para o processo backend e possui `finally` para restaurá-las; o coordenador comprovou restauração das três variáveis após execução real, conforme tabela acima.
- A configuração Spring restringe a exposição a health, oculta detalhes, inclui banco na prontidão e mantém a vivacidade separada. A segurança permite apenas GET nas três rotas exatas de health e nega as demais.
- V1 cria somente `app_metadata`, marcador técnico global sem dados de lojistas. O domínio preliminar não foi convertido em schema.
- CI executa Maven `verify` e, no admin, instalação pelo lockfile, lint, tipos, build e `test:smoke`. O nome inicialmente divergente do script smoke foi corrigido pelo coordenador.
- Após sugestão de QA, o teste de indisponibilidade do banco passou a prever espera limitada pela recuperação da prontidão após retirar a pausa. Esse teste passou no reteste integrado.
- README atualizado foi relido por QA: comandos correspondem ao helper, scripts npm e Compose; documenta JDK 21 por terminal, configuração local, preservação de volume, Testcontainers e limites do smoke. Inicialização real pelo helper, backend/Compose e checks HTTP foram comprovados no reteste; verificações do admin mantêm as evidências já aprovadas, sem repetição por inexistência de mudança.

## Auditoria de dependências

O responsável frontend/coordenador reportou `npm audit` completo com cinco entradas de severidade alta na mesma cadeia transitiva de desenvolvimento: `@next/eslint-plugin-next` 16.4.1 → `fast-glob` → `micromatch` → `braces` 3.0.3. São pacotes afetados pela mesma origem, não cinco vulnerabilidades independentes. `npm audit --omit=dev` retornou 0, sem vulnerabilidades de produção reportadas.

A revisão independente [SC-001-REVIEW.md](../security/SC-001-REVIEW.md) registra SEC-003 e o advisory sem patch disponível na consulta. A decisão foi manter as regras compatíveis, sem downgrade forçado nem remoção de cobertura do lint. **Não há exceção de risco aprovada: SEC-003 permanece aberta.** Auditoria de produção sem alertas e build aprovado não corrigem o tooling afetado nem certificam a árvore inteira. O aviso EOL/depreciação de ESLint 9 também permanece como risco de manutenção.

Consulta atualizada de segurança em 2026-10-15, 16:24–16:27 America/Sao_Paulo: `npm audit` completo retornou 1, com cinco altas e zero críticas; `npm audit --omit=dev` retornou 0, sem alertas. QA confirmou essas contagens nos JSONs `.tools/sec003-20261015/audit-full-current.json` e `audit-production-current.json`. Segurança confirmou braces latest 3.0.3 sem patch e fast-glob latest 3.3.3 ainda dependente da cadeia afetada. Não houve upgrade, alteração de lockfile ou exceção. A consulta posterior atualiza a auditoria anterior; os testes de aplicação não foram repetidos por ausência de mudança no código.

## Pendências para o aceite da SC-001

| Verificação | Estado atual |
| --- | --- |
| Dependência de desenvolvimento com advisory alto | SEC-003 aberta; sem correção ou exceção aprovada. |
| Decisão humana sobre risco | O usuário reconheceu a validação técnica local/remota da SC-001. SEC-003 continua sem correção ou exceção; esse reconhecimento não autoriza implementação da SC-002. |

CI remota foi comprovada no SHA `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`, conforme seção abaixo. CI-001 está resolvida operacionalmente nessa execução após correção do seletor Corretto. A publicação autorizada ocorreu no repositório privado; nenhuma dessas ações encerra SEC-003.

Histórico da primeira rodada: Docker Desktop 4.55.0 retornava `already running`, mas o named pipe permanecia indisponível; logs indicavam falha do Inference manager/socket `dockerInference`. A integração falhou em `beforeAll`, sem executar seus métodos. O usuário restabeleceu Docker; Engine 29.1.3 foi confirmado e o reteste integrado passou. Não houve reset ou exclusão de dados pelos agentes.

Na primeira execução com Docker, a comparação literal de health falhou porque Spring Boot 4 inclui `groups` no health agregado. Foi corrigida somente a expectativa de `BootstrapIT`: JSON é analisado, exige status UP e ausência de details/components. Aplicação e versões permaneceram iguais. A execução completa seguinte passou; warnings de conexão no teste de pausa são esperados.

O reteste Docker não repetiu build/lint do frontend nem auditoria npm, pois não houve alteração nesses arquivos. A auditoria foi reexecutada posteriormente pela segurança, conforme seção acima. Os logs e relatórios em `.tools/` e `backend/target/` são artefatos locais ignorados; este registro preserva o resumo para revisão.

Os logs do helper terminam com falha Maven decorrente da parada intencional da JVM filha; o helper lançou erro capturado pelo harness e executou finally. Isso não representa falha de startup ou dos checks HTTP e não altera o resultado aprovado do ciclo `verify` independente.

O Testcontainers usa sua própria inicialização PostgreSQL: seus testes não validam automaticamente `infrastructure/postgres/init-app.sh`. Reaplicar `flyway.migrate()` no mesmo contexto também não substitui testar persistência após reiniciar o Compose.

## Fechamento da preparação para GitHub

O coordenador exportou os 62 arquivos exatos do índice Git, comparou os candidatos com os dois valores secretos locais em memória sem imprimi-los e executou Gitleaks 8.30.1 com redação de achados: nenhum vazamento encontrado, saída 0. O lockfile foi examinado também sob nome neutro para evitar exclusão automática por nome; nenhum achado, saída 0. actionlint 1.7.12 aprovou o workflow preparado. Os binários locais foram obtidos dos releases oficiais e validados contra os checksums publicados.

Na preparação do commit inicial, exportação e scans foram repetidos para incluir os documentos finais. Relatórios brutos, ferramentas, senhas locais, builds e logs ficam ignorados em `.tools/`, `.env` e `target/`. O commit inicial `4e3daaab1e63122e4461167c7d4698a71e761dc8` contém 62 arquivos; foi publicado no destino posteriormente confirmado. A varredura não é garantia absoluta de ausência de segredos desconhecidos; cobre o snapshot e as regras examinadas. Novas alterações exigem nova revisão.

Na preparação inicial para publicação, não houve alteração em manifests, lockfile, POM ou Wrapper nem repetição dos builds locais; os testes foram posteriormente repetidos na CI remota descrita abaixo. Após aprovação humana, os commits oficiais das actions foram reconfirmados pelo revisor às 16:38 BRT em `.tools/actions-reconfirmation.json`; coordenador conferiu YAML oficial, runtime Node 24 e inputs utilizados, aplicando:

- checkout v7.0.1: `3d3c42e5aac5ba805825da76410c181273ba90b1`, nos dois jobs;
- setup-java v6.1.0: `b843ebf9b913146b38da2eaa7aeddf76074ae1ad`, com `cache-jdk: false`;
- setup-node v7.1.0: `949feb2413d6458794dcd2491c4babbbce0c15c1`.

Na migração inicial, Corretto 21.0.9, Node 24.12.0, cache Maven/npm e política de credenciais foram preservados. A execução remota revelou a restrição do seletor Corretto; a correção e as versões efetivamente utilizadas estão abaixo. `workflow_dispatch` permanece habilitado. [SC-001-PUBLICATION.md](SC-001-PUBLICATION.md) registra a publicação e os critérios da CI; CI verde não concede exceção à SEC-003.

## Publicação autorizada e CI remota

Destino confirmado pelo usuário: `https://github.com/IAbrahanArley/smart-commerce.git`. O coordenador identificou o repositório inicialmente público, alterou sua visibilidade para privada e confirmou `private=true` antes do primeiro push. QA leu somente os metadados `.tools/github-private-confirmed.json`, com conferência às 16:52:34 BRT (19:52:34 UTC), sem acessar credenciais ou `.env`. `origin` e upstream `origin/main` foram configurados no destino exato; push de `main` autorizado foi concluído. Scan do histórico inicial registrou um commit e zero achados; após a correção CI, dois commits e zero achados, segundo o coordenador.

### Primeira execução — falha de setup, admin aprovado

[Run 38797506400](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797506400), evento push, SHA `4e3daaab1e63122e4461167c7d4698a71e761dc8`, criado em 2026-10-15 às 16:53:18 BRT (19:53:18 UTC): conclusão failure.

- [Backend 116637656355](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797506400/job/116637656355): setup-java recusou `21.0.9` para Corretto com `Only major versions are supported`. Build e testes ficaram skipped; não foram aprovados nessa execução.
- [Admin 116637655982](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797506400/job/116637655982): success, concluído às 16:54:00 BRT (19:54:00 UTC). Instalação, lint, tipos, build e smoke aprovados.

A correção `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b` mudou apenas o seletor do workflow para `java-version: '21'` e acrescentou impressão das versões da toolchain. Mantém Java major 21; o provedor seleciona seu patch. Nenhuma fonte, dependência, POM ou lockfile da aplicação foi alterado. Actionlint e conferência Git passaram antes do novo push.

### Segunda execução — backend e admin aprovados

[Run 38797904235](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235), evento push, tentativa 1, SHA `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`: completed/success. Criado em 2026-10-15 às 16:56:19 BRT (19:56:19 UTC), atualizado às 16:57:32 BRT (19:57:32 UTC). Runner GitHub Actions 2.337.0, Ubuntu 24.04.5 LTS.

| Job | Resultado e versões efetivas | Evidência |
| --- | --- | --- |
| [Backend 116639039608](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235/job/116639039608) | Success. Corretto 21.0.12.12.1, Java 21.0.12.1+12-LTS; Maven 3.10.0. Setup, formatação, compilação, empacotamento e `verify` aprovados. Surefire: 3; Failsafe: 3; zero falhas, erros ou ignorados em ambas. Testcontainers 2.0.5 iniciou PostgreSQL 18.6 real; Flyway aplicou/validou V1 e a reexecução sem nova migração passou. | Build terminou às 16:57:25 BRT (19:57:25 UTC) com BUILD SUCCESS; job concluído às 16:57:31 BRT (19:57:31 UTC). |
| [Admin 116639040158](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235/job/116639040158) | Success. Node 24.12.0, npm 11.6.2, Next.js 16.4.1. Instalação limpa, lint, tipos, build e smoke aprovados: home 200, HTML pt-BR, main/h1 e 404. npm remoto difere do 11.21.0 usado localmente. | Job concluído às 16:57:03 BRT (19:57:03 UTC); smoke confirmado no log às 16:57:00 BRT (19:57:00 UTC). |

QA conferiu os metadados locais `.tools/github-run-38797904235.json`, `github-run-38797904235-jobs.json` e logs dos dois jobs capturados pelo coordenador, sem requisições de rede paralelas. CI-001 fica resolvida operacionalmente nessa execução. A CI não inclui gate de auditoria npm: permanece válida a consulta de segurança das 16:24–16:27 BRT, com cinco altas de desenvolvimento e zero alertas de produção; não houve nova auditoria nem aceitação de risco.

Esta evidência cobre o SHA citado; posteriores commits somente de documentação devem ter seu próprio resultado vinculado, sem presumir aprovação antecipada. Esse run de push não comprovou evento `pull_request` nem proteção de branch; ambos foram verificados na etapa posterior abaixo. A implementação da SC-002 não foi iniciada.

## Proteção de main e PR documental

O coordenador aplicou a proteção de `main` com resposta HTTP 200 e conferiu a configuração por GET. QA leu `.tools/main-protection-current.json`: checks obrigatórios `backend` e `admin` vinculados ao GitHub Actions (app ID 15368), `strict=true` para exigir base atualizada, enforcement para administradores, force push e exclusão desabilitados e conversas resolvidas antes de merge. Pull request é obrigatório; a contagem de aprovações exigidas é zero, para não bloquear o único proprietário. Portanto, não há garantia de aprovação independente obrigatória. Nenhum plano pago foi contratado.

[PR #1](https://github.com/IAbrahanArley/smart-commerce/pull/1) documental, aberto de `docs/sc-001-governance` para `main`, sem merge automático. Evidência consultada em `.tools/governance-pr-current.json`: head `dcff5cc413034699a8852e47130eed86a9527976`, base `83794ff95feb8847957d9d5fb2afe064c32b279e`, merge sintético `c01658d14419f0368bb72c02f066a375584f5f4e`. Esse merge sintético serve à validação de CI e não significa que o PR foi integrado.

[Run 38804591777](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38804591777), evento `pull_request`, tentativa 1: completed/success. Criado em 2026-10-15 às 17:46:01 BRT (20:46:01 UTC), atualizado às 17:46:56 BRT (20:46:56 UTC). A API associa o run ao head `dcff5cc413034699a8852e47130eed86a9527976`; o log de checkout confirma execução sobre `refs/pull/1/merge`, SHA sintético `c01658d14419f0368bb72c02f066a375584f5f4e`.

| Job do PR | Evidência conferida |
| --- | --- |
| [Backend 116662036552](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38804591777/job/116662036552) | Success às 17:46:54 BRT (20:46:54 UTC). Surefire 3 + Failsafe 3, zero falhas/erros/ignorados; BUILD SUCCESS às 17:46:52 BRT (20:46:52 UTC). Runner 2.337.0/Ubuntu 24.04.5, Java 21.0.12.1, Maven 3.10.0. |
| [Admin 116662037148](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38804591777/job/116662037148) | Success às 17:46:47 BRT (20:46:47 UTC). Instalação limpa, lint, tipos, Next.js 16.4.1 build e smoke aprovados; home 200/HTML pt-BR/conteúdo esperado/404. Node 24.12.0 e npm 11.6.2. |

QA conferiu JSONs locais do run/jobs e logs dos dois jobs capturados pelo coordenador; não executou chamadas de rede nem alterações remotas. `.tools/governance-pr-checks.json` também confirma `backend`/`admin` success associados ao head `dcff5cc413034699a8852e47130eed86a9527976`, app ID 15368, com URLs dos jobs PR acima; os checks do run de push 38804288050 aparecem separadamente e não foram confundidos com o evento PR. Esses resultados provam funcionamento do evento PR no head/merge citados, sem simular bypass ou tentativa destrutiva contra a proteção. Novos commits documentais neste PR exigem checks próprios; não se presume que resultados anteriores cubram alterações posteriores.

Nenhuma auditoria npm foi repetida nesta etapa: permanece a consulta identificada das 16:24–16:27 BRT e SEC-003 aberta. O [plano de testes da SC-002](SC-002-TEST-PLAN.md) é proposta documental pendente de aprovação e implementação; nenhum teste ou código de autenticação foi criado/executado nesta atividade.

Na consolidação documental, o coordenador executou actionlint 1.7.12 (saída 0; integrações opcionais shellcheck/pyflakes indisponíveis e desabilitadas), `git diff --check` (saída 0), conferência dos links relativos dos seis novos/relacionados documentos (nenhum destino ausente), preflight dos 68 arquivos candidatos contra caminhos sensíveis e dois valores secretos locais em memória (zero achados) e Gitleaks 8.30.1 no snapshot documental e histórico de quatro commits existente (zero vazamentos). Esses checks locais complementam a CI; não substituem auditoria de dependências nem testes de autenticação. O histórico escaneado antecede o commit de consolidação.

## Revalidação dos documentos consolidados

O [run 38805174880](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38805174880), evento `pull_request`, passou no head `27526b290db0607bc058ff48b39ee98763ddbadd`, com todos os oito documentos da proposta incluídos. Criado em 2026-10-15T20:50:25Z e concluído em 20:51:20Z (17:51:20 BRT). Merge sintético `41286a77378220828b5a1ada11016671147dc0a3`, base `83794ff95feb8847957d9d5fb2afe064c32b279e`.

- Backend [116664038962](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38805174880/job/116664038962): success; logs confirmam três testes de segurança e três integrados, zero falhas/erros/ignorados e BUILD SUCCESS.
- Admin [116664039748](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38805174880/job/116664039748): success em instalação limpa, lint, tipos, build e smoke 200/404.
- Checks confirmados no head pelo GitHub Actions, app ID 15368; PR aberto e estado clean após conclusão. Main continua protegida e sem merge. Gitleaks repetido após commit examinou cinco commits, zero vazamentos.

Esta anotação é documentação posterior ao head testado, não modifica código e terá sua própria execução de CI ao ser publicada. O teste comprova os checks existentes da SC-001; cenários de autenticação continuam somente propostos.

## Cobertura futura por escopo

- SC-002: autenticação e testes negativos de credenciais e acesso.
- SC-003/004: isolamento A/B em consultas e comandos, tenant derivado de contexto confiável e rejeição de acesso cruzado.
- Futuras tarefas de estoque/pagamentos: concorrência de reserva/baixa, idempotência, webhook repetido e fora de ordem, validação do provedor e conciliação.

Esses testes estão pendentes por inexistência dos respectivos módulos. Não devem ser criadas funcionalidades comerciais apenas para obter cobertura na SC-001.
