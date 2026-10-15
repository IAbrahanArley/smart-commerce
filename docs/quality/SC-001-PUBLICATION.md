# SC-001 — Preparação para GitHub e aprovação final

Data: 2026-10-15. Estado: publicação autorizada concluída em `https://github.com/IAbrahanArley/smart-commerce.git`, com visibilidade privada confirmada antes do push. CI remota aprovada no commit de correção `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`. O usuário reconheceu a validação técnica local e remota da SC-001. A etapa adicional de governança e PR está registrada em [SC-001-GOVERNANCE.md](SC-001-GOVERNANCE.md) e [SC-001-VALIDATION.md](SC-001-VALIDATION.md). SEC-003 permanece aberta, sem risco formalmente aceito. SC-002 está somente em planejamento proposto, sem implementação autorizada.

## Pendências atuais

1. **SEC-003 aberta:** nova consulta do agente de segurança confirmou cinco alertas altos derivados de uma única cadeia de desenvolvimento; auditoria de produção com zero alertas. Não foi identificada versão estável corrigida de `braces`. Nenhum pacote, lockfile, versão do Wrapper ou POM foi alterado nesta rodada. Não há exceção aprovada.
2. **Revisão adicional de governança:** validação técnica da SC-001 reconhecida pelo usuário; main protegida e PR documental preparado para revisão. CI verde não concede exceção à SEC-003 nem autorização para implementar SC-002.
3. **CI-001 — migração validada remotamente:** as actions Node 24 foram aprovadas após corrigir o seletor Corretto de `21.0.9` para `21`; a distribuição aceita apenas major. O primeiro run falho permanece no histórico. Fontes históricas da migração: [aviso do runtime](https://github.blog/changelog/2026-09-23-node-20-is-no-longer-available-in-github-actions/), [action Java anterior](https://raw.githubusercontent.com/actions/setup-java/cf277c60eb25467037889841efdb72551f06f6c3/action.yml).

## Publicação e CI executadas

- O usuário confirmou a URL exata antes de qualquer configuração de origin ou envio. O destino existia como público; foi ajustado para privado conforme a decisão do usuário e confirmado por GET autenticado (`private=true`, `visibility=private`) em 2026-10-15T19:52:34Z, antes do primeiro push. O remoto estava vazio; nenhum histórico existente foi sobrescrito e não houve force push.
- Commit inicial publicado: `4e3daaab1e63122e4461167c7d4698a71e761dc8`, 62 arquivos previamente conferidos e scan de histórico sem vazamentos. Origin sem credenciais na URL, tracking `main` → `origin/main`.
- [Primeiro run 38797506400](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797506400): admin aprovado; backend falhou no setup Java com `Only major versions are supported`. Build/testes backend foram ignorados pelo runner em consequência da falha e não foram declarados aprovados.
- Correção publicada: `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`, somente workflow: `java-version: '21'` e etapa explícita `java -version`/Maven `-v`. A causa foi confirmada no [adaptador Corretto do SHA fixado](https://github.com/actions/setup-java/blob/b843ebf9b913146b38da2eaa7aeddf76074ae1ad/src/distributions/corretto/installer.ts). Major Java 21, dependências e SHAs das actions preservados.
- [Run aprovado 38797904235](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235), evento push, tentativa 1, criado às 19:56:19Z e concluído às 19:57:32Z em 2026-10-15 (16:56:19–16:57:32 America/Sao_Paulo). Backend e admin com conclusão `success`, todas as etapas executadas aprovadas.
- Runner 2.337.0, Ubuntu 24.04.5. Backend: Corretto 21.0.12.12.1 / Java 21.0.12.1, Maven 3.10.0, Testcontainers 2.0.5 e PostgreSQL real; três testes de segurança e três integrados, zero falhas/erros/ignorados, `BUILD SUCCESS`. Admin: Node 24.12.0, npm 11.6.2, Next 16.4.1; instalação limpa, lint, tipos, build e smoke HTTP 200/404 aprovados.
- Logs e metadados brutos permanecem apenas em `.tools/`, ignorados; o resumo durável está neste documento e em [SC-001-VALIDATION.md](SC-001-VALIDATION.md). A autenticação utilizou o helper do Git em memória, sem imprimir ou gravar tokens.

## Nova análise da SEC-003

Consulta do agente de segurança em 2026-10-15: auditoria completa com saída 1 e cinco alertas altos; `npm audit --omit=dev` com saída 0 e zero alertas. São cinco pacotes afetados pela mesma origem, não cinco vulnerabilidades independentes.

| Componente | Consulta e conclusão |
| --- | --- |
| `braces` | Latest 3.0.3; advisory não indica versão corrigida. |
| `micromatch` | Latest 4.0.8 mantém dependência de `braces`. |
| `fast-glob` | Latest 3.3.3 mantém a cadeia afetada; atualizar somente este pacote não corrige o problema. |
| Plugin/configuração Next | Latest 16.4.1; plugin depende de `fast-glob` 3.3.1. Configuração direta do plugin mantém a vulnerabilidade. |
| ESLint | 9.39.5 compatível com os peers atuais, porém fora de suporte; latest 10.12.0 não é aceito pelos peers do plugin React atual. Não forçar instalação. |

Mitigações possíveis preservam lint, padrões glob controlados e revisão dos PRs que alterem configuração, scripts ou lockfiles. O workflow usa runner hospedado efêmero, token somente leitura, checkout sem persistência de credenciais, nenhum segredo de aplicação e limites de tempo. Código de PR continua sendo código não confiável e pode consumir recursos dentro desses limites. Essas medidas reduzem a exposição observada, mas não corrigem o pacote nem constituem aceitação do risco.

Não foi aplicado `npm audit fix --force`, downgrade de Next, override para versão inexistente, remoção das regras do Next, atualização incompatível ou exceção de segurança. Uma eventual exceção temporária exige aprovação humana explícita com responsável, prazo e gatilho de revisão. A recomendação atual é manter SEC-003 aberta e acompanhar correção compatível. Evidências, fontes do registry e advisory: [revisão de segurança](../security/SC-001-REVIEW.md).

## Preparação dos arquivos publicáveis

- `.gitignore` protege `.env`, caches, builds, ferramentas locais, logs, dumps, credenciais npm/netrc, chaves/certificados e diretórios locais de credenciais. Os `.env.example` continuam disponíveis para versionamento, com campos secretos vazios.
- O único caminho de usuário específico desta máquina encontrado nos candidatos foi substituído no README por um caminho ilustrativo de JDK. Configurações dos agentes do projeto foram revisadas; não contêm credenciais ou caminhos de perfil pessoal.
- O preflight compara os candidatos com os dois valores secretos do arquivo local de infraestrutura em memória, sem imprimir valores, e recusa caminhos sensíveis. O `.env` real não é exportado nem publicado.
- Primeiro snapshot: 61 arquivos, nenhum caminho proibido e nenhuma correspondência com os dois valores locais. Gitleaks 8.30.1, com binário validado por checksum oficial, retornou saída 0 e nenhum achado. O lockfile também foi examinado em cópia idêntica com nome neutro para evitar sua exclusão padrão no scanner; saída 0 e nenhum achado.
- Snapshot do índice preparado: **62 arquivos**, nenhum caminho proibido e nenhuma correspondência com os dois valores secretos locais. Gitleaks no candidato e no lockfile examinado separadamente retornou saída 0, nenhum achado; actionlint também retornou 0. O manifesto local registra caminhos, tamanhos e SHA256 dos arquivos exatos. A exportação e os scans são repetidos após o fechamento dos documentos para cobrir o conteúdo final do índice. `.tools/` contém ferramentas, exportações e relatórios locais e permanece ignorada.
- Nenhuma chave privada, arquivo de ambiente real, configuração pessoal de credenciais, dependência instalada, JAR, dump, log ou saída de teste deve aparecer na lista de arquivos do índice. A revisão é do conteúdo do primeiro commit local, que será novamente conferido após sua criação; não há histórico anterior.

Um scan sem achados não é prova absoluta da inexistência de segredos desconhecidos. O resultado cobre o snapshot examinado e as regras executadas; qualquer alteração posterior exige nova conferência antes do push.

## Workflow revisado

O workflow atual mantém `contents: read`, SHAs completos, `persist-credentials: false`, eventos `push`/`pull_request`/`workflow_dispatch`, limites de tempo e concorrência. Não usa `pull_request_target`, segredos de produção ou deploy. A migração aprovada atualizou as três actions para runtime Node 24, sem modificar as versões Java/Node das aplicações ou os comandos de build/teste.

Backend: Java 21 e Maven Wrapper, Spotless, testes de segurança, integração PostgreSQL com Testcontainers e JAR. Admin: Node 24, instalação pelo lockfile, lint, tipos, build e smoke HTTP. actionlint 1.7.12 passou no workflow atual com saída 0; não havia ShellCheck/Pyflakes, então a validação não inclui esses analisadores externos. Essa execução local não comprova a inicialização das actions no runner remoto.

### Migração das actions aprovada e aplicada

Releases e commits oficiais reconfirmados pelo agente revisor em 2026-10-15 às 16:38 America/Sao_Paulo, antes da aplicação. As refs oficiais das três tags resolveram diretamente para commits iguais aos SHAs aprovados; releases não são draft ou prerelease. Os `action.yml` obtidos pelos SHAs exatos declaram `node24` e preservam os inputs usados. Evidência bruta local ignorada: `.tools/actions-reconfirmation.json`.

| Action | Anterior | Aplicada | SHA confirmado |
| --- | --- | --- | --- |
| checkout, dois jobs | v4, `11d5960a326750d5838078e36cf38b85af677262` | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| setup-java | v4, `cf277c60eb25467037889841efdb72551f06f6c3` | v6.1.0 | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` |
| setup-node | v4, `49933ea5288caeca8642d1e84afbd3f7d6820020` | v7.1.0 | `949feb2413d6458794dcd2491c4babbbce0c15c1` |

Fontes oficiais: [checkout v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1), [setup-java v6.1.0](https://github.com/actions/setup-java/releases/tag/v6.1.0), [setup-node v7.1.0](https://github.com/actions/setup-node/releases/tag/v7.1.0).

Os inputs utilizados continuam disponíveis. Java permanece na major 21; após a falha remota comprovada do seletor com patch, foi aplicado `java-version: '21'` para Corretto, resultando em Java 21.0.12.1 no run aprovado. Node 24.12.0, cache Maven/npm, caminhos e política de credenciais foram preservados. `cache-jdk: false` evita habilitar o novo cache de JDK. Todos os alvos usam runtime Node 24; runner remoto 2.337.0 satisfaz os mínimos documentados. A execução remota validou as novas majors após a correção, sem alterar dependências da aplicação.

## Procedimento para repetir a validação remota

1. O destino confirmado é `IAbrahanArley/smart-commerce`, privado. Antes de novos envios autorizados, confirmar que origin e visibilidade continuam iguais. Publicar código não equivale a aceitar SEC-003 ou autorizar produção.
2. O repositório e origin já existem; não recriar o destino, não substituir histórico e não executar `remote add` novamente. Usar autenticação segura sem credenciais em URL ou arquivos.
3. Revisar o commit e os arquivos versionados. Aplicar preflight/scans às mudanças novas e conferir Git antes de enviar. A identidade de autoria configurada é utilizada, sem inventar autor ou alterar configuração global.

   ```sh
   git log -1 --oneline
   git status --short
   git remote get-url origin
   git push -u origin main
   ```

   O destino já foi confirmado e os pushes da publicação foram executados. O exemplo serve para mudanças futuras autorizadas; não executar force push. O commit inicial não deve ser criado novamente.

4. Em Settings → Actions, habilitar workflows e as actions oficiais utilizadas. Usar o runner hospedado `ubuntu-24.04`. Não configurar segredos de aplicação ou credenciais do PostgreSQL local: Testcontainers cria seu próprio banco descartável.
5. Conferir a execução `CI` disparada pelo push, ou usar Actions → CI → Run workflow em `main`. Associar resultados ao SHA exato do commit enviado.
6. Exigir job `backend` aprovado: setup correto, formatação, compilação, JAR e seis testes (três segurança + três integração), zero falhas/erros/ignorados. Erros do pull de imagem, inicialização do Docker ou runner contam como execução não aprovada; não pular testes.
7. Exigir job `admin` aprovado: instalação limpa pelo lockfile, lint, tipos, build e smoke HTTP 200/404. Logs devem confirmar as ferramentas esperadas; CI verde não encerra SEC-003, pois esse workflow não é um gate de auditoria npm.
8. Abrir um PR somente de documentação, em branch separada, para validar também o evento `pull_request`. Conferir o SHA e os dois checks no commit de merge de teste do PR. Revisar antes de aprovar execução de PRs de colaboradores externos.
9. Registrar URL da execução, SHA, data, runner, versões e totais de testes em SC-001-VALIDATION.md. Somente depois do sucesso configurar os checks reais `backend` e `admin` como obrigatórios na proteção de `main`, usando os nomes exibidos pelo GitHub.
10. Consolidar a decisão sobre SEC-003 e o aceite humano da SC-001. SC-002 continua exigindo nova autorização e ADR de autenticação.

Upload de relatórios de falha é melhoria opcional ainda não implementada. Se aprovado futuramente, limitar a Surefire/Failsafe revisados e evitar publicar diretórios de build, configurações, dumps ou logs completos.

## Aprovações e evidências restantes

- Migração das actions: aprovada e aplicada com SHAs oficiais reconfirmados; actionlint e validação operacional remota aprovados após corrigir o seletor Corretto.
- SEC-003: permanece aberta; nenhuma exceção aprovada.
- Publicação: concluída no destino confirmado, privado; origin configurado, main publicada e rastreando origin/main. Atualizações posteriores de documentação registram as evidências e são acompanhadas pela CI, sem mudar o código testado.
- CI remota: comprovada no SHA `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`, run 38797904235, backend e admin aprovados. Proteção de branch e evento pull_request ainda não foram configurados/testados nesta entrega.
- Testes locais: seis testes backend e fluxo Compose aprovados; frontend aprovado na rodada anterior. Não foram repetidos builds locais nesta rodada; os builds e testes foram repetidos e aprovados na CI remota após a correção do seletor Java. Não houve mudança em código ou dependências da aplicação. Actionlint, preflight, verificações Git e scans são repetidos antes de publicar o registro de evidências.
