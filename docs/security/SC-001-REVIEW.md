# SC-001 — Revisão independente de segurança

Data: 2026-10-15. Escopo: bootstrap local e configuração de CI, conforme ADR-0004. Revisão estática inicial e posterior leitura das evidências do reteste funcional executado pelo coordenador e pelo responsável backend. O revisor não executou builds ou testes concorrentes.

## Parecer

Há uma dependência de desenvolvimento afetada por advisory de severidade alta (SEC-003). Não foi demonstrada exploração por rota HTTP do shell estático. Não foi identificado achado crítico no código examinado. O reteste posterior obteve validação funcional local de PostgreSQL/Compose, Flyway, controles HTTP e helper, conforme evidências abaixo; o bloqueio inicial de Docker foi superado. A CI remota foi concluída com sucesso no commit `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`, conforme seção de publicação abaixo. SEC-003 e o aviso EOL do ESLint continuam abertos; o aceite integral de segurança não é concedido. Este parecer não aprova deploy de produção nem declara a árvore de dependências livre de vulnerabilidades altas.

O arquivo local `infrastructure/.env` não foi aberto nem impresso. Foram lidos exemplos, configurações, código e os resultados de testes explicitamente indicados pelo coordenador. Houve consulta pública ao advisory citado abaixo, sem acesso a contas ou serviços de negócio e sem alterações de código nesta revisão.

## Controles observados na revisão estática inicial

| Área | Evidência | Resultado da revisão estática |
| --- | --- | --- |
| Fronteira HTTP | `backend/src/main/java/com/smartcommerce/bootstrap/BootstrapSecurityConfiguration.java` | Somente GET nas três rotas exatas de health é público. Demais requisições usam `denyAll`; login, Basic, logout e request cache desabilitados. CSRF não foi desabilitado. Não há autenticação funcional. |
| Usuário padrão | `backend/src/main/java/com/smartcommerce/SmartCommerceApplication.java` | Exclusão explícita de `UserDetailsServiceAutoConfiguration`, evitando usuário e senha gerados. O teste de slice também aplica exclusão; confirmação no contexto completo deve usar a execução integrada. |
| Actuator e erros HTTP | `backend/src/main/resources/application.yml` | Apenas health exposto, sem detalhes/componentes. Readiness inclui banco e liveness inclui estado do processo. Mensagens, stack traces e binding errors não são incluídos nas respostas padrão. |
| Banco e Flyway | `application.yml`, `db/migration/V1__bootstrap.sql` | Credenciais obrigatórias por ambiente, limpeza Flyway desabilitada e validação de migração habilitada. V1 cria somente marcador técnico global `app_metadata`, sem domínio ou dados pessoais. |
| Rede local | `infrastructure/compose.yaml`, `application.yml`, `admin-web/package.json` | PostgreSQL publicado em 127.0.0.1; backend tem loopback como padrão; scripts dev/start do admin usam loopback. Override explícito de configuração pode mudar esse comportamento. |
| Papel PostgreSQL | `infrastructure/postgres/init-app.sh` | Papel da aplicação criado com NOSUPERUSER, NOCREATEDB, NOCREATEROLE e NOREPLICATION; proprietário do banco para executar Flyway local. Variáveis psql usam quoting de identificadores/literais. Não confundir esse proprietário com papel de runtime limitado para produção. |
| Helper local | `scripts/start-backend.ps1` | Parser restritivo sem avaliação de comandos; valida identificadores e porta; exporta DB_URL/DB_USERNAME/DB_PASSWORD e restaura valores anteriores em finally. Não imprime configurações. Não transfere a senha administrativa lida do arquivo para uma nova variável de ambiente do backend. |
| Frontend | `admin-web/src/app/`, `next.config.ts`, `.env.example` | Shell estático, sem login, domínio, credenciais, fetch de dados privados ou configuração pública de segredos. Error boundary usa mensagem genérica. |
| CI | `.github/workflows/ci.yml` | Push/PR, `contents: read`, actions fixadas por SHA, sem secrets de produção ou deploy. Maven verify e checagens do admin configurados; Testcontainers fornece seu próprio PostgreSQL. |
| Exclusões | `.gitignore` | Arquivos .env, ferramentas e builds locais ignorados; exemplos continuam disponíveis para versionamento. Ignore não protege arquivos já rastreados nem substitui revisão de segredos. |

## Achados e melhorias

### SEC-001 — Baixa: checksum da distribuição Maven ausente

Local: `backend/.mvn/wrapper/maven-wrapper.properties`.

A distribuição usa HTTPS e versão explícita, mas não define `distributionSha256Sum`. Os scripts `mvnw` e `mvnw.cmd` já suportam essa validação. Fixar o checksum acrescenta verificação de integridade ao primeiro download; sua ausência não demonstra comprometimento.

Estado: corrigido durante a revisão pelo responsável backend. Nova leitura confirmou `distributionSha256Sum` no arquivo. O revisor não validou o hash contra download externo nem executou o cenário de corrupção. Reprodução do cenário original: consultar o arquivo de propriedades e procurar a propriedade ausente. Aceite complementar: propriedade corresponde ao ZIP e wrapper rejeita um arquivo divergente em teste isolado com cache novo, sem alterar o cache habitual.

### SEC-002 — Baixa: checkout mantém credencial de leitura por padrão

Local: ambos os passos `actions/checkout` de `.github/workflows/ci.yml`.

Não há `persist-credentials: false`. O token já tem apenas `contents: read`, mas o checkout pode disponibilizá-lo aos passos seguintes. Como o job apenas compila e testa, manter a credencial não é necessário.

Estado: corrigido durante a revisão pelo coordenador; nova leitura confirmou `persist-credentials: false` nos dois passos. Reprodução do cenário original: inspecionar os passos checkout e a permissão global. Aceite complementar: pipeline continua funcionando sem credencial Git persistida. Não executar comandos que imprimam headers Git ou tokens para conferir isso.

Essas duas melhorias são de defesa adicional e não demonstram quebra de autorização ou isolamento. Não foi feita validação externa dos SHAs de actions nem auditoria completa de dependências Java nesta revisão.

### SEC-003 — Alta no advisory: DoS em dependência transitiva de desenvolvimento

Local: `admin-web/package-lock.json`, entrada `node_modules/braces` versão 3.0.3 marcada `dev: true`, via `micromatch` 4.0.8 / `fast-glob` 3.3.1 / configuração de lint.

O coordenador reportou cinco entradas altas em `npm audit` para essa cadeia. Contagem de pacotes afetados transitivamente não significa cinco vulnerabilidades independentes. O revisor confirmou no lockfile a presença de braces afetado e consultou diretamente [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm): severidade alta, versões até 3.0.3 afetadas e nenhuma versão corrigida indicada na consulta de 2026-10-15. O problema é exaustão da pilha ao processar padrões profundamente aninhados.

Exposição observada: tooling de desenvolvimento/CI, sem entrada de glob controlada por comprador ou lojista no shell estático examinado. Não há evidência de exploração remota no runtime da aplicação. Classificação `dev` reduz a exposição observada, mas não corrige o pacote nem elimina risco no processamento de conteúdo não confiável durante desenvolvimento/CI. Os jobs têm limite de tempo, token somente leitura e não usam segredos de produção.

Reprodução não destrutiva: executar `npm audit --json`, `npm audit --omit=dev --json` e `npm explain braces` em `admin-web`, registrando resultados e códigos de saída. Não executar padrão de DoS no processo habitual. O teste `--omit=dev` distingue a árvore de produção, mas não certifica todos os artefatos gerados nem substitui uma correção.

Alternativas a avaliar com o responsável frontend: atualizar para versão efetivamente corrigida quando disponível; substituir a configuração de lint por alternativa compatível que remova a cadeia mantendo lint TypeScript/React/Next equivalente, validando o lockfile e as checagens; ou documentar exceção temporária explícita para tooling com responsável, justificativa e gatilho de revisão. A exceção não deve ser presumida como aprovada pelo usuário. Não inventar override para versão inexistente nem forçar downgrade de Next por sugestão automática de `npm audit fix --force`.

Limite da alternativa de substituição: o lockfile mostra que `@next/eslint-plugin-next` 16.4.1 depende diretamente de `fast-glob` 3.3.1. Trocar apenas o pacote de configuração pelo plugin direto preserva a cadeia vulnerável. Remover regras Next para eliminar o alerta reduz cobertura e exige decisão explícita; não é uma correção equivalente automática. Criar fork próprio também acrescenta manutenção fora do objetivo de bootstrap.

Resultado informado pelo responsável frontend ao coordenador: `npm audit --omit=dev` executado com saída 0, sem vulnerabilidades de produção reportadas; auditoria completa continua com cinco entradas altas para a mesma origem `@next/eslint-plugin-next` → `fast-glob` → `micromatch` → `braces`. Esses comandos não foram executados novamente pelo revisor.

Decisão de implementação informada pelo coordenador: manter a configuração compatível sem `--force`, sem retirar regras Next e sem presumir exceção aprovada. SEC-003 permanece aberta, e o aceite integral de segurança não é concedido. O frontend usa ESLint 9.39.5 por compatibilidade de peers; o responsável também reportou aviso EOL/depreciação no registry. Compatibilidade não equivale a suporte de segurança; acompanhar atualização compatível de toda a cadeia. Não classificar SEC-003 como corrigida apenas porque build e lint passam. A revisão pontual desse advisory não constitui auditoria completa de CVEs.

## SEC-003 — Nova consulta de versões e mitigações

Consulta executada pelo revisor em **2026-10-15, entre 16:24 e 16:27, America/Sao_Paulo** (19:24–19:27 UTC). Esta seção atualiza a análise de dependências; preserva as observações históricas acima e não constitui exceção de risco aprovada. Nenhuma dependência, manifest ou lockfile foi alterado. Não foi lido o `.env`.

### Auditorias executadas nesta consulta

Comandos executados em `admin-web`, com registry público `https://registry.npmjs.org`, `--fetch-retries=0` e timeout de consulta de 20 segundos:

| Comando | Código de saída | Resultado |
| --- | --- | --- |
| `npm.cmd audit --json` | 1 | 5 entradas altas, 0 críticas, 0 moderadas, 0 baixas; todas ligadas ao mesmo advisory de braces. |
| `npm.cmd audit --omit=dev --json` | 0 | 0 vulnerabilidades reportadas na árvore de produção consultada. |
| `npm.cmd explain braces --json` | 0 | Uma instalação de braces 3.0.3, marcada como desenvolvimento, pela cadeia abaixo. |

Cadeia confirmada pelo comando explain e pelo lockfile: `eslint-config-next@16.4.1` → `@next/eslint-plugin-next@16.4.1` → `fast-glob@3.3.1` → `micromatch@4.0.8` → `braces@3.0.3`. As cinco entradas correspondem aos cinco pacotes afetados dessa cadeia; não são cinco CVEs independentes. As relações intermediárias aparecem como dependências do pacote pai no explain, mas a raiz do projeto as inclui por dependência de desenvolvimento.

Evidência local ignorada: `.tools/sec003-20261015/audit-full-current.json`, `audit-production-current.json`, `explain-braces-current.json`, `exit-codes-current.json` e metadados `*-latest.json`/`*-versions.json`/`eslint-pinned.json`. A primeira tentativa no sandbox falhou por conectividade (logs sem sufixo `-current`); seus códigos não devem ser confundidos com as auditorias bem-sucedidas acima. A nova execução e consultas de registry usaram a permissão de rede concedida para esta análise. Nenhum `audit fix`, instalação ou padrão de DoS foi executado.

### Versões disponíveis verificadas

| Pacote | Instalado | Última versão estável consultada | Conclusão |
| --- | --- | --- | --- |
| braces | 3.0.3 | 3.0.3 | A lista completa publicada termina em 3.0.3; não há release corrigida identificada. [Registry](https://registry.npmjs.org/braces), [advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm). |
| micromatch | 4.0.8 | 4.0.8 | Ainda depende de braces `^3.0.3`. [Registry](https://registry.npmjs.org/micromatch/latest). |
| fast-glob | 3.3.1 | 3.3.3 | A atualização disponível ainda depende de micromatch `^4.0.8`; não resolve SEC-003. O plugin Next fixa 3.3.1. [Registry](https://registry.npmjs.org/fast-glob/latest). |
| @next/eslint-plugin-next | 16.4.1 | 16.4.1 | Ainda fixa fast-glob 3.3.1; trocar apenas o wrapper de configuração por esse plugin não resolve. [Registry](https://registry.npmjs.org/@next%2Feslint-plugin-next/latest). |
| eslint-config-next | 16.4.1 | 16.4.1 | Continua incluindo o plugin Next afetado. [Registry](https://registry.npmjs.org/eslint-config-next/latest). |
| ESLint | 9.39.5 | 10.12.0 | 9.39.5 tem aviso deprecated; ESLint 9 está EOL desde 2026-08-06. Upgrade isolado para 10 não tem compatibilidade de peers confirmada com o plugin React abaixo e não remove a cadeia Next. [Registry da versão instalada](https://registry.npmjs.org/eslint/9.39.5), [latest](https://registry.npmjs.org/eslint/latest), [suporte oficial](https://eslint.org/version-support/). |
| eslint-plugin-react | 7.37.5 | 7.37.5 | Peer ESLint declarado: `^3 || ^4 || ^5 || ^6 || ^7 || ^8 || ^9.7`; não inclui 10. [Registry](https://registry.npmjs.org/eslint-plugin-react/latest). |

O advisory consultado continua classificando o problema como alto, afetando braces até 3.0.3, sem patched version indicada. O JSON npm usa CVSS 3.1 7.5 e a página GitHub apresenta CVSS 4.0 8.7; são métricas distintas, ambas altas. Não reinterpretar essa diferença como correção ou falso positivo. As tags canary de Next/plugin/config não são versões estáveis; a mera existência de pré-release não comprova correção ou compatibilidade.

`npm audit` sugere `eslint-config-next@14.2.35` com `isSemVerMajor: true`. Isso é proposta de downgrade da ferramenta para outra linha, não release corrigida de braces nem patch compatível comprovado para este Next 16. Não aplicar `--force`, `--legacy-peer-deps`, override para release inexistente ou redução de regras para fazer o alerta desaparecer.

### Mitigações avaliadas sem retirar lint ou CI

1. **Controlar padrões de glob:** manter argumentos estáticos nos scripts e configuração revisada. Inspeção do plugin instalado em `dist/utils/get-root-dirs.js` mostra que `settings.next.rootDir`, quando string/array, é passado a `fast-glob.globSync`; na ausência dessa configuração usa o diretório de trabalho. O `eslint.config.mjs` atual não define esse rootDir e os ignores observados são constantes. Não derivar globs de payload HTTP, metadados de PR, nomes de branch, variáveis ou arquivos enviados por usuários. Isso reduz a alcançabilidade observada, sem corrigir a biblioteca.
2. **Tratar PR como código não confiável:** configuração de lint e scripts do próprio PR podem ser alterados. Globs estáticos na branch principal não garantem segurança de executar um PR arbitrário. Manter runner hospedado efêmero e revisão/controles de execução de contribuidores externos; não executar esses PRs em máquina com credenciais ou runner persistente privilegiado. Uma revisão aprovada por humano não equivale a ausência de código malicioso.
3. **Preservar limites já configurados:** workflow usa `pull_request`, permissões `contents: read`, ambos os checkouts com `persist-credentials: false`, actions por SHA, sem secrets/deploy e timeout de 15 minutos no job admin. Essas medidas limitam impacto e retenção de acesso, mas não impedem que um padrão malicioso interrompa o lint. Não trocar por `pull_request_target` executando código do PR nem disponibilizar tokens de escrita/secrets ao lint. Manter lint, tipos, build e smoke obrigatórios.
4. **Evitar execução local privilegiada:** para contribuições não confiáveis, preferir o ambiente efêmero da CI sem segredos. Não disponibilizar `.env` local, credenciais de nuvem ou acesso de deploy a jobs que processam o conteúdo do PR. Acesso ao Docker ou rede e políticas efetivas do repositório exigem análise própria; o YAML sozinho não comprova isolamento completo.
5. **Acompanhar correção real:** nova versão precisa remover a cadeia afetada ou incluir braces corrigido, com compatibilidade declarada de peers e instalação limpa. Após aprovação de um patch concreto, executar audit completo e produção, explain, `npm ci`, lint, tipos, build e smoke, revisando o diff do lockfile. Não basta o audit de produção retornar zero.

**Conclusão atualizada:** não foi identificado patch estável compatível capaz de corrigir SEC-003 na consulta atual. Não há alteração de package/lock para propor como correção comprovada. A atualização isolada de fast-glob ou ESLint não resolve o achado. Recomenda-se manter SEC-003 bloqueando o aceite integral de segurança até correção verificada ou decisão humana explícita sobre uma exceção temporária documentada, com responsável, escopo de tooling, prazo de revisão e gatilhos de reavaliação. As mitigações acima podem sustentar essa decisão, mas não a concedem automaticamente. EOL do ESLint permanece como risco adicional de manutenção da mesma cadeia; não é um novo CVE demonstrado.

## Verificações executadas pelo revisor

- Leitura integral dos documentos de produto, arquitetura, orquestração e papel de segurança; leitura dos ADRs 0001–0004 e dos arquivos listados acima.
- `git check-ignore` confirmou exclusão dos caminhos `infrastructure/.env`, `backend/.env`, `admin-web/.env.local`, `backend/target/probe`, `admin-web/.next/probe`, `admin-web/node_modules/probe` e `.tools/probe`. A consulta dos dois `.env.example` não os identificou como ignorados. Nenhum conteúdo secreto foi lido.
- Inspeção dos testes `BootstrapSecurityTest` e `BootstrapIT`. Os testes foram lidos, não executados por este revisor. Resultados de outros responsáveis devem ser consultados em `docs/quality/SC-001-VALIDATION.md` e nos relatórios correspondentes.

## Reteste funcional local após recuperação do Docker

O coordenador informou Docker 29.1.3 disponível e PostgreSQL 18.6 saudável no Compose. Os testes foram executados pelo coordenador/responsável backend; o revisor conferiu somente os arquivos abaixo, sem reexecutar testes, iniciar processos ou ler o `.env`.

| Controle | Resultado atualizado | Origem da evidência e limites |
| --- | --- | --- |
| Suíte backend | 6 testes, zero falhas, erros ou ignorados: 3 de segurança e 3 de integração | Revisor leu `backend/target/surefire-reports/com.smartcommerce.bootstrap.BootstrapSecurityTest.txt` e `backend/target/failsafe-reports/com.smartcommerce.BootstrapIT.txt`. Sucesso do comando final Maven verify informado pelo coordenador. |
| Inicialização real Compose e privilégios | Papel autenticado `smart_commerce`, proprietário do banco; superuser, createdb, createrole, replication e bypassrls falsos | Execução e consulta de flags informadas pelo coordenador. O revisor não consultou o banco. Esta verificação cobre o init real, separadamente do PostgreSQL de Testcontainers. |
| Fronteira HTTP no backend real | 11 verificações: os três GET de health/probes responderam 200; login, env, configprops, heapdump, health/db, api/tenants, tentativa com headers/cookie forjados e POST health responderam 403 | Revisor leu `.tools/http-up-results.json`, que registra paths/status. Método POST e variante com X-Tenant-ID, Authorization e Cookie fictícios foram informados pelo coordenador; o JSON não registra esses detalhes das requisições. Não equivale a teste tenant A/B. |
| Respostas mínimas | Apenas status e, no health agregado quando presente, nomes de grupos; sem componentes, credenciais ou detalhes do banco | Conteúdo das respostas conferido pelo coordenador; os JSONs de resultados lidos pelo revisor contêm somente paths/status. A suíte integrada também foi aprovada pelo backend. |
| Indisponibilidade real do banco | Readiness 503, health agregado 503 e liveness 200 após parar PostgreSQL preservando volume | Revisor leu `.tools/http-down-results.json`. Ausência de detalhes nas respostas foi informada pelo coordenador. |
| Recuperação e persistência | Após subir o banco, três GET retornaram 200; marcador `bootstrap\|SC-001` e migração `1\|t` permaneceram iguais | Recuperação HTTP informada pelo coordenador. Revisor leu `.tools/metadata-before.txt` e `.tools/metadata-after.txt`, ambos com os mesmos dois registros. Não houve remoção de volume. |
| Helper real | JVM de teste encerrada; DB_URL, DB_USERNAME e DB_PASSWORD restaurados | Revisor leu `.tools/helper-first-restore.json`: `urlRestored`, `usernameRestored` e `passwordRestored` verdadeiros, sem valores secretos. Encerramento da JVM informado pelo coordenador. |

Segunda inicialização concluída: o revisor conferiu em `.tools/helper-restart.log` somente as linhas de validação Flyway, com uma migração validada e nenhuma migração necessária, e releu `.tools/http-up-results.json` com os 11 resultados esperados. `.tools/helper-restore.json` também registra as três restaurações como verdadeiras. O coordenador confirmou encerramento apenas da JVM usada no teste, mantendo PostgreSQL saudável e volume preservado.

Os resultados locais acima superam a limitação inicial de Docker. Na conclusão desse reteste, permaneciam SEC-003 (incluindo acompanhamento da cadeia de lint/ESLint EOL) e execução de CI remoto. A CI foi posteriormente comprovada na seção abaixo; SEC-003 permanece aberta. A auditoria npm não foi reexecutada na rodada do reteste funcional; foi reexecutada posteriormente na nova consulta de SEC-003 registrada neste documento. Artefatos em `.tools/` e `target/` são locais e ignorados; os resultados e a origem foram registrados aqui para manter o histórico legível.

## Publicação e comprovação de CI remota

Atualização em 2026-10-15. Publicação e consultas GitHub foram executadas pelo coordenador após autorização do usuário para o remoto `https://github.com/IAbrahanArley/smart-commerce.git`, push e CI. O revisor não executou operações Git, chamadas autenticadas ou nova auditoria; leu somente os metadados e trechos de logs indicados a seguir.

- Privacidade: `.tools/github-private-confirmed.json` registra `IAbrahanArley/smart-commerce`, `private: true`, `visibility: private`, conferidos às 19:52:34 UTC (16:52:34 de São Paulo), antes do run de publicação. A mudança para privado antes de qualquer push foi informada pelo coordenador; o revisor não reconstruiu o histórico de visibilidade por chamadas GitHub. Nenhum token ou `.env` foi lido nesta conferência.
- Histórico da CI: o coordenador informou que o primeiro run `38797506400` teve admin aprovado e falha de setup Corretto no backend (`Only major versions supported`), com testes backend não executados nesse run. Não interpretar essa falha de preparação como resultado de testes de aplicação. O ajuste posterior mudou o seletor Java para `21` e adicionou registro das versões da toolchain; não foi atualização de dependências da aplicação.
- Run aprovado: [GitHub Actions 38797904235](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235), evento push, commit `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`, status completed, conclusion success. `.tools/github-run-38797904235.json` registra criação às 19:56:19 UTC e atualização final às 19:57:32 UTC (16:56:19–16:57:32 de São Paulo).
- Backend: job `116639039608` concluído com sucesso; leitura de `.tools/github-job-116639039608.log` confirmou três testes de segurança e três de integração, todos sem falhas, erros ou ignorados, e `BUILD SUCCESS`. Java efetivo 21.0.12.1, distribuição Corretto 21.0.12-12.1. O seletor `21` resolve a versão disponível na execução; a evidência registra a versão realmente usada.
- Admin: job `116639040158` concluído com sucesso; `.tools/github-run-38797904235-jobs.json` registra instalação, lint, tipos, build e smoke aprovados. `.tools/github-job-116639040158.log` confirma Node 24.12.0, npm 11.6.2 e smoke HTTP 200/404 aprovado. O job usa instalação com `--no-audit`; seu sucesso não é auditoria de vulnerabilidades aprovada.
- Controles de workflow conferidos novamente: actions fixadas por SHA, `contents: read`, ambos os checkouts com `persist-credentials: false`, limites de tempo e sem deploy. O workflow também disponibiliza `workflow_dispatch`. Não foram lidas configurações de branch protection ou concedida aprovação humana de merge/risco.

Esta evidência comprova a execução do pipeline para o commit citado, não para edições documentais posteriores ainda não publicadas. Encerra a pendência de comprovação remota nesse commit, mas não encerra SEC-003/ESLint EOL, não aceita exceção temporária e não aprova produção, autenticação de SC-002 ou isolamento tenant A/B.

## Comandos reproduzíveis e cobertura de aceite

1. Com Java 21 configurado, executar em `backend`: `./mvnw.cmd test` no Windows ou `./mvnw test` no Linux. `BootstrapSecurityTest` deve verificar ausência de UserDetailsService no slice, presença de CsrfFilter, GET health público, bloqueio das demais rotas e POST negado mesmo com CSRF válido.
2. Com Docker funcional, executar `./mvnw.cmd verify` ou `./mvnw verify`. `BootstrapIT` deve validar marcador Flyway, reaplicação sem nova migração, respostas health exatas sem detalhes e readiness 503 durante indisponibilidade do banco, com liveness 200 e recuperação da prontidão. Não ignorar esses testes silenciosamente se Docker faltar.
3. Iniciar o Compose conforme README. Consultar flags do papel efetivamente usado pelo backend, por exemplo, com conexão administrativa local no container e a consulta `SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls FROM pg_roles WHERE rolname NOT LIKE 'pg_%';`. O papel da aplicação deve ter todas essas flags falsas. Não consultar hashes de senha. Testcontainers não executa automaticamente `infrastructure/postgres/init-app.sh` e não comprova essas flags do Compose.
4. Com backend real disponível: GET `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness` deve retornar somente status; `/login`, `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump` e `/actuator/health/db` devem ser negados. Repetir acesso a uma rota protegida com `X-Tenant-ID` fictício, Authorization inválido e cookie inventado deve continuar negado. Esta última variação ainda não está automatizada na suíte examinada.
5. Parar temporariamente o serviço PostgreSQL, sem remover volume, e confirmar readiness indisponível e ausência de detalhes sensíveis na resposta HTTP. Inspecionar logs com cuidado e sem reproduzir valores secretos no relatório. Reiniciar e comprovar recuperação/persistência sem `down -v` ou exclusão de dados.
6. Em `admin-web`, executar `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build` e `npm run test:smoke`. O smoke verifica HTTP/HTML e 404; não é teste de autenticação ou de autorização. Revisar somente artefatos públicos e código do frontend para garantir ausência de dados privados/segredos.
7. Confirmar execução real de GitHub Actions após configurar remoto autorizado. Cumprido para o commit `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b` pelo run aprovado acima; revisão do YAML isolada não teria satisfeito esse item.

Histórico da primeira revisão: não foram executados builds, testes de aplicação, Compose ou pipeline remoto por este revisor, para evitar concorrência com os agentes responsáveis. Naquele momento, o coordenador informou falha Maven verify na integração com `Could not find a valid Docker environment` e falha do Docker Desktop ao iniciar seus serviços. Os controles de banco/Compose e o comportamento HTTP com banco ainda não tinham comprovação integrada. Esse bloqueio foi superado no reteste registrado acima; a falha inicial não representa o estado funcional local atual. Não houve necessidade de remover dados nesta revisão.

## Threat model para as próximas tarefas

| Fronteira | Ameaça prioritária | Teste exigido quando existir a funcionalidade |
| --- | --- | --- |
| Autenticação e CSRF — SC-002 | Sequestro/reuso de sessão; comando autenticado por cookie sem proteção CSRF | Credencial inválida, expiração/logout, invalidação de sessão e comandos com CSRF ausente/inválido. Estratégia exige ADR antes da implementação. |
| Tenant/IDOR — SC-003/004 | Usuário A acessa ou altera recurso B trocando ID, slug, header, query ou payload | Duas identidades e duas lojas: negar leitura e escrita cruzadas e verificar ausência de efeito no banco. Tenant vem de identidade e associação autorizada, não do seletor sozinho. |
| OAuth do lojista — futuro | Callback vincula conta de pagamento à loja errada; roubo/reuso de autorização | State inválido, reutilizado, de outra sessão/tenant e redirect não autorizado; credenciais cifradas e ausentes de logs. |
| Webhook — futuro | Evento forjado/repetido/fora de ordem produz baixa ou crédito incorreto | Assinatura inválida, pagamento de outra loja, repetição e ordem invertida; validar status com provedor e transição/idempotência no backend. |
| Dados pessoais — futuro | Dados do comprador compartilhados entre lojas ou retidos indevidamente | Negativas A/B para clientes/pedidos; acesso mínimo; política de retenção e logs sem payload pessoal. |
| Segredos — futuro | Token por tenant exposto em browser, logs, banco ou ferramentas de IA | Inspeção de respostas/bundles/logs com fixtures fictícias; testes de autorização de acesso e rotação/revogação de credenciais. |

SC-001 não contém usuários, tenants, clientes, OAuth ou webhooks. Portanto, esta revisão não certifica tenant A/B, conformidade de privacidade ou segurança de pagamentos. Não implementar essas funcionalidades apenas para encerrar o bootstrap.
