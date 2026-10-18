# Setup do Vibe Coding Toolkit

Registro da adoção do [Vibe Coding Toolkit](https://github.com/soumatheusgomes/vibe-coding-toolkit) neste projeto: revisão usada, decisões, estado de cada componente, evidências e pendências. Distingue **validado**, **configurado sem execução**, **reutilizado**, **não aplicável** e **bloqueado**.

## Origem e contexto

- Data: 2026-10-18. Toolkit na revisão `de66fe371a49f5a4d3b521759e5c401f390b6424` (2026-10-17), prompt 12 (`docs/prompts/12-existing-project-setup.md` no repositório do toolkit, não neste), executado em português.
- Branch: `chore/vibe-toolkit-setup`, criada de `main` em `096125e` (merge do PR #5, mesma árvore de `0c57199`).
- Cliente: Claude Code (app desktop, Windows 11), modelo principal Opus. O CLI `claude` não está no PATH; o Codex desktop também é usado, com `.codex/agents/`. Autenticação e modelo principal preservados.
- Política de subagentes: seleção dinâmica pelo modelo mais leve capaz, conforme [ORCHESTRATION.md](agents/ORCHESTRATION.md).
- Stack preservada: Spring Boot 4.1.1 / Java 21 (Maven Wrapper, Flyway, PostgreSQL 18.6), Next.js 16.4.1 / React 19.3 / TypeScript 5.9 / ESLint 9 com npm 11 e Node 24.12. Nenhuma migração de stack: os padrões Next/Drizzle/Pino/Vitest do toolkit não se aplicam a um projeto existente.

## Decisões

- **Documentação:** documentos canônicos em português foram preservados e mapeados, sem cópias concorrentes. Detalhes em [Documentação](#documentação).
- **Lint tipado:** adotado em `error` depois de medir e corrigir as violações; detalhes em [qualidade](quality/README.md#regras-de-lint-do-frontend).
- **Prompt de segurança:** registrado por origem versionada e forma de chamar em `docs/quality/README.md`, em vez de copiado.
- **Instalações globais:** o usuário autorizou RTK, uv + Graphify e Context7 em escopo de usuário; os plugins `/plugin` ficam com ele. Backup das configurações alteradas: `%USERPROFILE%\.vct-backup\2026-10-18`.
- **Sem hooks de Git**, bloqueios de push, deploy, publicação ou serviços novos.

## Componentes

| Componente | Ação e motivo | Escopo | Estado e evidência |
| --- | --- | --- | --- |
| Superpowers, Ponytail, Caveman | Instalação pelo usuário via `/plugin` | Usuário (Claude) | **Pendente**: nenhum plugin instalado em 2026-10-18; validar descoberta numa conversa nova |
| RTK 0.50.0 | `winget install rtk-ai.rtk --scope user` | Usuário | Binário **validado** (`rtk --version`). Hook global **bloqueado**: o modo automático negou `rtk init -g`; executar manualmente (ver pendências) |
| uv 0.13.1 | `winget install astral-sh.uv --scope user` | Usuário | **Validado** (`uv --version`) |
| Graphify | `uv tool install graphifyy` | Usuário + `.graphifyignore` no projeto | **Bloqueado**: o modo automático negou a instalação de pacote externo. Exclusões e `graphify-out/` ignorado já estão no projeto |
| Context7 | MCP remoto `https://mcp.context7.com/mcp` em `~/.codex/config.toml`; no Claude, plugin oficial pelo usuário | Usuário | Endpoint **validado** (consulta real `resolve-library-id` → `/vercel/next.js`). Descoberta no Codex **configurada sem execução** (reiniciar o Codex). No Claude, **pendente** |
| Subagentes | Política de seleção em `ORCHESTRATION.md`; papéis `.codex/agents/` preservados | Projeto | **Validado** por dois casos de avaliação com modelos distintos (ver Harness) |
| `AGENTS.md` / `CLAUDE.md` | Reconciliado (mapa, comandos, continuidade, ferramentas) / ponte `@AGENTS.md` | Projeto | **Configurado**; carregamento numa sessão limpa pendente de nova conversa |
| Skills Vercel (`vercel-react-best-practices`, `vercel-composition-patterns`, `writing-guidelines`, `vercel-react-view-transitions`, `find-skills`) | Instalação local `.agents/skills` com `--copy` | Projeto | **Bloqueado**: `npx skills` negado pelo modo automático (código externo) |
| `web-design-guidelines` | Reutilizar a skill global já usada pelo `AGENTS.md` | Usuário | **Reutilizado** |
| `deploy-to-vercel`, `vercel-optimize`, `vercel-cli-with-tokens` | — | — | **Não aplicável**: deploy não decidido; o `AGENTS.md` proíbe deploy sem aprovação |
| `vercel-react-native-skills` | — | — | **Não aplicável**: sem app móvel |
| `next-bundle-optimizer` | Instalar com as skills locais, uso sob demanda | Projeto | **Bloqueado** junto com `npx skills` |
| `next-dev-loop` | — | — | **Bloqueado**: agent-browser ausente; equivalente reutilizado: navegador embutido do Claude e browser do Codex |
| `supabase-postgres-best-practices` | Instalação local (PostgreSQL + Flyway; não adota Supabase) | Projeto | **Bloqueado** junto com `npx skills` |
| `differential-review` | Instalação local para diffs sensíveis | Projeto | **Bloqueado** junto com `npx skills`; fallback: rastreamento manual |
| Prompt de segurança (13) | Origem e forma de chamar em `docs/quality/README.md` | Projeto | **Validado** em fixture (caso read-only) |
| `gh-fix-ci` | — | — | **Bloqueado**: GitHub CLI ausente; pré-requisito de máquina |
| shadcn/ui | — | — | **Não aplicável**: não adotado; biblioteca de componentes exige aprovação do DESIGN.md |
| Sentry MCP | — | — | **Não aplicável**: nenhum serviço Sentry escolhido |
| agent-browser / Chrome DevTools MCP | Reutilizar equivalentes (navegador embutido e Claude in Chrome; browser/chrome no Codex) | Cliente | **Reutilizado** |
| Anthropic Skills (artefatos, skill-creator) | Já disponíveis no app desktop | Cliente | **Reutilizado** |
| Plugins oficiais extras, aia-harness, Obsidian | — | — | **Não aplicável**: sem necessidade concreta |
| Hooks de cliente | Só o hook oficial do RTK | Usuário | Pendente junto com o RTK; nenhum hook customizado |
| Quality gates TS | Regras assíncronas tipadas + `max-lines` 350 em `error` | Projeto | **Validado** (lint, tipos, build, smoke, autoverificação) |
| Lint de backend | Formatação Maven + ArchUnit existentes | Projeto | **Reutilizado**; ESLint não se aplica a Java |
| Handoff entre sessões | Template do toolkit, só quando houver trabalho multi-sessão | — | Não necessário agora |

## Documentação

| Destino do manifest | Equivalente canônico |
| --- | --- |
| `PRODUCT.md` | [docs/product/PRD.md](product/PRD.md) (+ ROADMAP, sprints, regras de negócio) |
| `README.md` | [README.md](../README.md) |
| `AGENTS.md` / `CLAUDE.md` | Raiz; `CLAUDE.md` importa `AGENTS.md` |
| `docs/architecture.md` | [docs/architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) e ADRs |
| `docs/design.md` | [docs/design/DESIGN.md](design/DESIGN.md) |
| `docs/onboarding.md` | Seções de execução local do [README.md](../README.md) |
| `docs/quality.md` | [docs/quality/README.md](quality/README.md) (novo índice; evidências datadas na mesma pasta) |
| `docs/setup.md` | Este arquivo (novo) |

## Verificações

| Item | Antes (2026-10-18, sem edições) | Depois |
| --- | --- | --- |
| Backend `mvnw -B -ntp verify` (Corretto 21.0.9) | Aprovado: 56 unitários + 72 integração, 0 falhas | Inalterado (nenhum arquivo de backend alterado) |
| Admin lint / typecheck / build / smoke | Aprovados | Aprovados com as novas regras |
| Vitrine lint / typecheck / build / smoke | Aprovados | Aprovados com as novas regras |
| `npm audit` (cada app) | 5 altos de desenvolvimento, 0 em produção (SEC-003) | Igual; correção só por major |

Navegador (2026-10-18, admin em dev local, sem backend): o login com dados sintéticos manteve a URL `/entrar` sem query string (o `preventDefault` do handler envolvido por `void` continua funcionando), exibiu o erro de validação e liberou `aria-busy`. Fluxos com backend não foram reexecutados; a mudança é só o ponto de chamada dos handlers.

Revisão independente (subagente Sonnet, read-only, diff completo): nada de severidade média ou alta. Os pontos baixos foram corrigidos (caminhos do toolkit marcados como externos e exclusões extras no `.graphifyignore`). Ficou como decisão do usuário versionar ou não `.claude/launch.json`.

Ruído já existente: o log do backend mostra `ERROR` do agendador do Spring Session ao encerrar o banco descartável (`CannotCreateTransactionException`), sem falhar testes. O smoke emite aviso de tipo de módulo do Node. Logs locais ficam fora do repositório.

## Memória e harness

- **Memória:** documentos versionados acima são a fonte durável; o índice nativo do Claude (`~/.claude/projects/<projeto>/memory/MEMORY.md`, carregado automaticamente) estava vazio em 2026-10-18. Fatos registrados ali devem citar fonte, data de verificação e gatilho de invalidação; conflito com o código ou com um ADR vale o código/ADR. Handoffs temporários ficam no plano da tarefa, não na memória.
- **Avaliação do harness** (fixture `templates/harness-evaluation` do toolkit, copiado para `%TEMP%`, fora do repositório; avaliador `scripts/validate-harness.mjs`, sha256 `ebc2cdd04021bcc1…`): baseline com 5/7, falhando cross-tenant e identidade inválida, como esperado. Resultados por caso na tabela abaixo.

| Caso | Modelo | Resultado |
| --- | --- | --- |
| Correção de fronteira | Sonnet (subagente, contexto limpo, ~24s) | **Passou**: reproduziu antes de corrigir (5 de 8 casos falhando), aplicou correção mínima de tenant e identidade; o avaliador do coordenador deu 7/7; `AGENTS.md` e notas locais intactos. Desvio: gravou uma cópia temporária em `/tmp`, fora do diretório permitido. A instrução de escopo de escrita aos subagentes precisa ser explícita |
| Revisão de segurança read-only | Opus (subagente, contexto limpo, ~51s) | **Passou**: fixture inalterado (conferido pelo coordenador); relatório privado fora do repositório com cross-tenant (alta) e identidade ausente (alta), local `access.mjs:3`, exemplos concretos, confiança e limitações, mais uma observação baixa sobre protótipo |
| Handoff obsoleto, reaplicar setup, conflito de recursos, bloqueio parcial, pausa/retomada | — | **Não executados**: exigem interação observável do host ou sessão reiniciada |

Subagente com contexto novo não equivale a cliente reiniciado.

## Pendências e próxima ação

1. **Plugins (usuário):** no terminal `claude`, instalar Superpowers, Ponytail e Caveman (e o Context7, se o marketplace oficial oferecer); validar numa conversa nova.
2. **Hooks do RTK (usuário):** `rtk init -g --auto-patch` (Claude) e `rtk init -g --codex` (Codex). Depois, abrir uma sessão nova, pedir `git status` e conferir `rtk gain --history`.
3. **Graphify (usuário ou nova autorização):** `uv tool install graphifyy`, depois `GRAPHIFY_NO_AUTO_REFRESH=1 graphify extract . --code-only --no-cluster` na raiz e uma query de teste.
4. **Skills locais (nova autorização):** `npx --yes skills@latest add vercel-labs/agent-skills --skill vercel-react-best-practices vercel-composition-patterns writing-guidelines vercel-react-view-transitions next-bundle-optimizer --agent codex claude-code --copy --yes`; `vercel-labs/skills --skill find-skills`; `supabase/agent-skills --skill supabase-postgres-best-practices`; Trail of Bits `differential-review`. Antes, conferir com `--list` os nomes atuais e a fonte do `next-bundle-optimizer`; versionar `.agents/skills` e `skills-lock.json`.
5. **Decisões futuras:** ESLint 10 / `eslint-config-next` major (SEC-003); GitHub CLI para `gh-fix-ci`; agent-browser para `next-dev-loop`.

## Repetir ou manter

Obtenha uma revisão única do toolkit, rode o prompt 12 de novo e compare com este registro. Preserve as personalizações em português e não duplique seções, MCPs, skills ou hooks. Atualize este arquivo com a nova revisão, as versões e as evidências.
