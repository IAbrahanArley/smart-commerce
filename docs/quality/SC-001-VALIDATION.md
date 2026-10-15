# SC-001 — Registro de validação

Data: 2026-10-15. Estado: **validação encerrada com pendências; aceite integral não concedido**.

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
| CI: actionlint após migração | Aprovado, executado pelo coordenador | actionlint 1.7.12, checksum oficial verificado, retornou 0 após migração aprovada das actions para runtime Node 24. Execução remota permanece pendente. |
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
| GitHub Actions | Configuração revisada; nenhuma execução remota comprovada. |
| Compatibilidade remota das actions | CI-001 mitigada localmente por migração aprovada e SHAs oficiais reconfirmados; runtime Node 24 e inputs verificados, actionlint aprovado. A execução no GitHub continua indispensável; não houve falha remota demonstrada. |
| Publicação | Este conjunto de 62 arquivos compõe o commit inicial local autorizado. Destino/remoto e push aguardam definição; repositório deverá ser privado inicialmente. Scans finais e revisão do índice integram o fechamento; autorização do commit não aceita SEC-003 nem autoriza SC-002. |

Histórico da primeira rodada: Docker Desktop 4.55.0 retornava `already running`, mas o named pipe permanecia indisponível; logs indicavam falha do Inference manager/socket `dockerInference`. A integração falhou em `beforeAll`, sem executar seus métodos. O usuário restabeleceu Docker; Engine 29.1.3 foi confirmado e o reteste integrado passou. Não houve reset ou exclusão de dados pelos agentes.

Na primeira execução com Docker, a comparação literal de health falhou porque Spring Boot 4 inclui `groups` no health agregado. Foi corrigida somente a expectativa de `BootstrapIT`: JSON é analisado, exige status UP e ausência de details/components. Aplicação e versões permaneceram iguais. A execução completa seguinte passou; warnings de conexão no teste de pausa são esperados.

O reteste Docker não repetiu build/lint do frontend nem auditoria npm, pois não houve alteração nesses arquivos. A auditoria foi reexecutada posteriormente pela segurança, conforme seção acima. Os logs e relatórios em `.tools/` e `backend/target/` são artefatos locais ignorados; este registro preserva o resumo para revisão.

Os logs do helper terminam com falha Maven decorrente da parada intencional da JVM filha; o helper lançou erro capturado pelo harness e executou finally. Isso não representa falha de startup ou dos checks HTTP e não altera o resultado aprovado do ciclo `verify` independente.

O Testcontainers usa sua própria inicialização PostgreSQL: seus testes não validam automaticamente `infrastructure/postgres/init-app.sh`. Reaplicar `flyway.migrate()` no mesmo contexto também não substitui testar persistência após reiniciar o Compose.

## Fechamento da preparação para GitHub

O coordenador exportou os 62 arquivos exatos do índice Git, comparou os candidatos com os dois valores secretos locais em memória sem imprimi-los e executou Gitleaks 8.30.1 com redação de achados: nenhum vazamento encontrado, saída 0. O lockfile foi examinado também sob nome neutro para evitar exclusão automática por nome; nenhum achado, saída 0. actionlint 1.7.12 aprovou o workflow preparado. Os binários locais foram obtidos dos releases oficiais e validados contra os checksums publicados.

Após registrar estes resultados, a exportação do índice e os scans são repetidos para incluir os documentos finais. Relatórios brutos, ferramentas, senhas locais, builds e logs ficam ignorados em `.tools/`, `.env` e `target/`. Este conjunto compõe o commit inicial local autorizado; destino e push permanecem pendentes, com visibilidade privada inicial aprovada. A varredura não é garantia absoluta de ausência de segredos desconhecidos; cobre o snapshot e as regras examinadas. Novas alterações exigem nova revisão.

Não houve alteração em manifests, lockfile, POM ou Wrapper nesta rodada; os seis testes backend e checks do frontend mantêm as evidências anteriores, sem repetição. Após aprovação humana, os commits oficiais das actions foram reconfirmados pelo revisor às 16:38 BRT em `.tools/actions-reconfirmation.json`; coordenador conferiu YAML oficial, runtime Node 24 e inputs utilizados, aplicando:

- checkout v7.0.1: `3d3c42e5aac5ba805825da76410c181273ba90b1`, nos dois jobs;
- setup-java v6.1.0: `b843ebf9b913146b38da2eaa7aeddf76074ae1ad`, com `cache-jdk: false`;
- setup-node v7.1.0: `949feb2413d6458794dcd2491c4babbbce0c15c1`.

QA releu o workflow aplicado; Corretto 21.0.9, Node 24.12.0, cache Maven/npm e política de credenciais foram preservados. `workflow_dispatch` permanece habilitado. CI-001 fica mitigada localmente; [SC-001-PUBLICATION.md](SC-001-PUBLICATION.md) registra passos e critérios da CI remota. CI verde, quando obtida, não concederá exceção automática à SEC-003.

## Cobertura futura por escopo

- SC-002: autenticação e testes negativos de credenciais e acesso.
- SC-003/004: isolamento A/B em consultas e comandos, tenant derivado de contexto confiável e rejeição de acesso cruzado.
- Futuras tarefas de estoque/pagamentos: concorrência de reserva/baixa, idempotência, webhook repetido e fora de ordem, validação do provedor e conciliação.

Esses testes estão pendentes por inexistência dos respectivos módulos. Não devem ser criadas funcionalidades comerciais apenas para obter cobertura na SC-001.
