# Qualidade

Índice canônico das verificações do Smart Commerce. Os demais arquivos desta pasta são evidências datadas de cada entrega; este documento diz o que cada check prova e quando executá-lo. Convenções de backend: [CODING_STANDARDS.md](../architecture/CODING_STANDARDS.md).

## Comandos e o que provam

| Escopo | Comando | Prova | Não prova |
| --- | --- | --- | --- |
| Backend | `.\mvnw.cmd -B -ntp verify` em `backend/` com JDK 21 | Formatação, compilação, testes unitários, ArchUnit de camadas e integração PostgreSQL descartável (Testcontainers), incluindo isolamento A/B entre tenants | Comportamento em navegador, produção ou outro PostgreSQL |
| Frontend (admin e vitrine) | `npm run lint` | Presets Next/TypeScript, regras assíncronas tipadas e limite de 350 linhas, com zero avisos | Comportamento em execução |
| Frontend | `npm run typecheck` | Tipos de rotas gerados e `tsc --noEmit` | — |
| Frontend | `npm run build` | Build de produção Next.js | — |
| Frontend | `npm run test:smoke` | Build iniciado em porta local; respostas HTTP, cabeçalhos e conteúdo esperado | Renderização visual, acessibilidade, interação real |

Execute os checks do escopo alterado antes de concluir; mudanças transversais rodam todos. A CI (`.github/workflows/ci.yml`) executa os mesmos comandos por aplicação em push/PR, sem deploy nem segredos. Não há comando combinado nem suíte E2E; Playwright/Axe continuam propostos em [QA.md](../agents/QA.md), sem instalação autorizada.

## Regras de lint do frontend

Adotadas em 2026-10-18 (setup do toolkit), em `eslint.config.mjs` de cada aplicação:

- `@typescript-eslint/no-floating-promises` e `@typescript-eslint/no-misused-promises` como `error`, com lint tipado (`projectService`). Medição antes da adoção: zero violações na vitrine; seis `no-misused-promises` no admin (handlers assíncronos em `onSubmit`/`onClick`), corrigidas com `void` no ponto de chamada, já que os handlers tratam erros e chamam `preventDefault()` de forma síncrona.
- `max-lines` 350 (sem linhas em branco/comentários) como `error`; nenhuma violação no código de produção.
- Custo medido localmente: lint do admin de ~11s para ~14s e da vitrine de ~9s para ~11s.
- Autoverificação: um arquivo temporário com promise flutuante fez o lint falhar com a regra esperada.

Backend usa ferramentas nativas (formatação no ciclo Maven e ArchUnit); não há ESLint fora do frontend. Testes de integração Java acima de 350 linhas são aceitos: o limite de referência vale para código de produção.

## Revisão após implementação ou correção

Escopo proporcional ao risco do diff real: requisitos, regressões e testes, segurança e dados, integrações e configuração, acessibilidade/UX, desempenho medido e manutenção. Para mudanças não triviais, use revisão independente quando disponível (papéis em [docs/agents/](../agents/)). Registre alvo exato, chamadores, contratos e testes conectados, achados confirmados e cobertura. Termine quando os achados relevantes estiverem resolvidos ou com disposição explícita e os checks afetados passarem. Rodadas extras exigem evidência nova, risco aberto ou pedido explícito; contagem de revisões não prova correção.

## Revisão de segurança

Prompt reutilizável (externo, não copiado para este repositório): bloco “Prompt” de `docs/prompts/13-security-review.md` do [Vibe Coding Toolkit](https://github.com/soumatheusgomes/vibe-coding-toolkit), revisão `de66fe371a49f5a4d3b521759e5c401f390b6424`. Para atualizar, leia a mesma seção na revisão atual do toolkit e registre o novo SHA aqui.

- **Baseline do repositório:** peça ao agente para executar o bloco em modo repositório, preservando cliente/modelo/autenticação e salvando relatório redigido fora do repositório.
- **Diff:** `Use diff mode, base=<ref real>, head=<ref real>, comparison=endpoint ou merge-base, dirty=exclude ou include.` Use `differential-review` quando instalada e compatível; sem ela, rastreamento manual explícito.
- Revisão é read-only; relatórios ficam em local privado do usuário (por exemplo `%LOCALAPPDATA%\SmartCommerce\security-reviews`), nunca versionados. Correções exigem instrução separada. Priorize tenant, autenticação/sessão/CSRF, upload de imagens, HTML sanitizado, carrinho público, segredos e CI.
- Nenhum achado significa apenas que nada foi comprovado no escopo inspecionado. Revisões anteriores: [SC-001-REVIEW.md](../security/SC-001-REVIEW.md).

## Dependências

Execute `npm audit` e `npm audit --omit=dev` em cada aplicação. Em 2026-10-18: cinco alertas altos na cadeia de desenvolvimento (`eslint-config-next` → `@next/eslint-plugin-next`, `fast-glob`, `micromatch`, `braces`) e zero em produção, com correção apenas por versão major. Esse é o risco SEC-003, que continua aberto e sem aceite; atualização major exige decisão separada.

## Avaliação do harness

Mudanças relevantes em instruções, skills, memória ou orquestração rodam os casos descartáveis de `docs/07-harness-evaluation.md` do toolkit, fora do repositório. Resultados e casos não executados ficam em [docs/setup.md](../setup.md).
