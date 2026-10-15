# SC-001 — Preparação para GitHub e aprovação final

Data: 2026-10-15. Estado: migração das actions aprovada e aplicada; este conjunto compõe o primeiro commit local da SC-001. O destino GitHub ainda será confirmado e deverá ser privado inicialmente; nenhum push foi autorizado antes dessa confirmação. Aceite integral depende da CI remota e da SEC-003. SC-002 não foi iniciada.

## Pendências atuais

1. **SEC-003 aberta:** nova consulta do agente de segurança confirmou cinco alertas altos derivados de uma única cadeia de desenvolvimento; auditoria de produção com zero alertas. Não foi identificada versão estável corrigida de `braces`. Nenhum pacote, lockfile, versão do Wrapper ou POM foi alterado nesta rodada. Não há exceção aprovada.
2. **CI remota não executada:** destino privado e remoto ainda serão confirmados. O primeiro commit é preparado somente localmente. A revisão estática e os seis testes locais aprovados não substituem uma execução do GitHub Actions no commit publicado.
3. **CI-001 — configuração migrada:** as actions anteriores usavam Node 20 e `setup-java` v4 depreciada. A migração aprovada para Node 24 foi aplicada após reconfirmação oficial de todos os SHAs, com actionlint aprovado. A pendência de configuração antiga foi tratada; a compatibilidade operacional ainda exige CI remota. [Aviso oficial do runtime](https://github.blog/changelog/2026-09-23-node-20-is-no-longer-available-in-github-actions/), [metadados históricos da action Java anterior](https://raw.githubusercontent.com/actions/setup-java/cf277c60eb25467037889841efdb72551f06f6c3/action.yml).

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

Os inputs utilizados continuam disponíveis. A migração preservou Corretto 21.0.9, Node 24.12.0, cache Maven/npm, caminhos e política de credenciais. Foi aplicado `cache-jdk: false` para não habilitar o novo cache de JDK além do cache de dependências já existente. Todos os alvos usam runtime Node 24, que requer runner mínimo 2.327.1; execução autenticada de Git dentro de container Docker requer 2.329.0 no checkout, cenário não utilizado aqui. Interfaces compatíveis pela documentação e actionlint aprovado não substituem teste remoto das novas majors. As dependências da aplicação não mudaram.

## Passos para validar a execução remota

1. Confirmar proprietário e nome do repositório GitHub; a visibilidade inicial **privada** já foi aprovada. A migração e o primeiro commit local foram autorizados, mas push permanece bloqueado até a confirmação do destino. Publicar o código não equivale a aprovar SEC-003 ou autorizar produção.
2. Após confirmar o destino, criar um repositório **privado** vazio, sem adicionar README/licença automaticamente, para evitar histórico divergente. Conferir a indicação Private no GitHub antes do envio. Não incluir credenciais em URL, scripts ou arquivos do projeto. Autenticar usando o mecanismo seguro do Git/GitHub.
3. Na raiz, revisar o commit inicial local, `git status --short` e a lista de arquivos commitados. Os preflights, scans e `git diff --cached --check` são executados antes da criação do commit. A identidade de autoria configurada é utilizada, sem inventar autor ou alterar configuração global. Nenhum remoto é configurado nesta entrega.

   ```sh
   git log -1 --oneline
   git status --short
   git remote add origin https://github.com/OWNER/REPOSITORY.git
   git push -u origin main
   ```

   Os comandos de remote/push são instruções para execução somente após confirmação do destino; não foram executados nesta preparação. Substituir OWNER/REPOSITORY pelo destino privado autorizado. O commit inicial local não deve ser criado novamente se já estiver presente.

4. Em Settings → Actions, habilitar workflows e as actions oficiais utilizadas. Usar o runner hospedado `ubuntu-24.04`. Não configurar segredos de aplicação ou credenciais do PostgreSQL local: Testcontainers cria seu próprio banco descartável.
5. Conferir a execução `CI` disparada pelo push, ou usar Actions → CI → Run workflow em `main`. Associar resultados ao SHA exato do commit enviado.
6. Exigir job `backend` aprovado: setup correto, formatação, compilação, JAR e seis testes (três segurança + três integração), zero falhas/erros/ignorados. Erros do pull de imagem, inicialização do Docker ou runner contam como execução não aprovada; não pular testes.
7. Exigir job `admin` aprovado: instalação limpa pelo lockfile, lint, tipos, build e smoke HTTP 200/404. Logs devem confirmar as ferramentas esperadas; CI verde não encerra SEC-003, pois esse workflow não é um gate de auditoria npm.
8. Abrir um PR somente de documentação, em branch separada, para validar também o evento `pull_request`. Conferir o SHA e os dois checks no commit de merge de teste do PR. Revisar antes de aprovar execução de PRs de colaboradores externos.
9. Registrar URL da execução, SHA, data, runner, versões e totais de testes em SC-001-VALIDATION.md. Somente depois do sucesso configurar os checks reais `backend` e `admin` como obrigatórios na proteção de `main`, usando os nomes exibidos pelo GitHub.
10. Consolidar a decisão sobre SEC-003 e o aceite humano da SC-001. SC-002 continua exigindo nova autorização e ADR de autenticação.

Upload de relatórios de falha é melhoria opcional ainda não implementada. Se aprovado futuramente, limitar a Surefire/Failsafe revisados e evitar publicar diretórios de build, configurações, dumps ou logs completos.

## Aprovações e evidências restantes

- Migração das actions: aprovada e aplicada com SHAs oficiais reconfirmados e actionlint aprovado; validação operacional remota pendente.
- SEC-003: permanece aberta; nenhuma exceção aprovada.
- Publicação: primeiro commit local com 62 arquivos revisados; visibilidade inicial privada definida, proprietário/nome ainda pendentes. Sem remoto ou push.
- CI remota: aguardando publicação autorizada e execução com evidência vinculada ao SHA.
- Testes locais: seis testes backend e fluxo Compose aprovados; frontend aprovado na rodada anterior. Não foram repetidos builds de aplicação nesta rodada, pois as alterações são de documentação e configuração das actions, sem mudanças em código ou dependências da aplicação. Actionlint, preflight, verificações Git e scans são repetidos após a migração, antes do commit.
