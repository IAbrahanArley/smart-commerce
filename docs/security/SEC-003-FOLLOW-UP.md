# SEC-003 — Plano de acompanhamento

Data: 2026-10-15; atualizado em 2026-10-18 ao ser resgatado do [PR #1](https://github.com/IAbrahanArley/smart-commerce/pull/1), fechado sem merge. Estado: **aberta, sem exceção de risco aprovada**. Este documento planeja acompanhamento; não aplica upgrades, não executa nova auditoria e não cria automação ou tarefa agendada.

## Referência e situação conhecida

Achado e evidência: [SC-001-REVIEW.md](SC-001-REVIEW.md), incluindo a consulta registrada em 2026-10-15. Vincular a issue correspondente neste documento pelo coordenador quando houver URL confirmada; não presumir número ou issue criada.

Consulta de 2026-10-18 (`npm audit --json` e `npm audit --omit=dev --json` nos lockfiles da `main` em `096125e`): `admin-web` e `storefront-web` têm, cada um, cinco entradas altas e zero críticas na auditoria completa — `eslint-config-next`, `@next/eslint-plugin-next`, `fast-glob`, `micromatch` e `braces` — e zero na árvore de produção. A vitrine reutiliza a mesma cadeia do admin, portanto o achado agora cobre as duas aplicações. Registro anterior, de 2026-10-15: auditoria completa com cinco entradas altas da cadeia `eslint-config-next` → `@next/eslint-plugin-next` → `fast-glob` → `micromatch` → `braces@3.0.3`; produção com zero alertas. O advisory [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) não tinha versão corrigida indicada naquela consulta. ESLint 9 EOL é risco associado de manutenção e não prova de um segundo CVE. A CI aprovada não executa um gate de auditoria completo e não encerra o achado.

## Responsabilidade e cadência proposta

| Papel | Responsabilidade |
| --- | --- |
| Security | Conduzir consulta de advisory/registry e alcançabilidade; atualizar parecer e recomendar tratamento. |
| Frontend | Identificar release/combinação compatível, preparar patch e lockfile para revisão, preservando regras de lint e peers. |
| QA | Reproduzir instalação, auditorias e verificações do patch; conferir evidências de CI. |
| Coordenador / reviewer | Manter issue/documentação coerentes, registrar bloqueios e submeter eventual exceção explicitamente ao humano. |
| Responsável humano do projeto | Decidir aceitação temporária de risco, custo ou mudança de escopo; nenhum agente aceita por omissão. |

Cadência **manual proposta: a cada sete dias e antes de cada release**. Usando a consulta de 2026-10-18 como referência, a próxima verificação proposta é até 2026-10-25, em America/Sao_Paulo; antecipar se houver release ou gatilho abaixo. Essa data não representa lembrete configurado nem promessa de monitoramento automático.

Reavaliar antes do prazo se publicar patch, mudar configuração de lint/globs, aceitar contribuições externas, aumentar permissões do workflow, disponibilizar secrets/runner persistente, mudar ambiente de execução ou descobrir exploração. Também revisar ao atualizar Next, ESLint ou plugins, pois compatibilidade isolada não garante remoção da cadeia.

## Roteiro para a próxima verificação autorizada

1. Ler advisory, versões e dependências publicadas no registry oficial, verificando peers de ESLint/Next/React. Comparar com manifest e lockfile efetivos; registrar data e fontes.
2. Executar `npm audit --json`, `npm audit --omit=dev --json` e `npm explain braces`; guardar códigos e saídas locais sem segredos, resumindo no documento versionado. Não usar `audit fix --force`, downgrade incompatível ou versão de override inexistente.
3. Se houver correção real, propor diff preciso e compatível para aprovação/revisão. Não retirar regras Next ou substituir configuração por cobertura menor apenas para limpar contagem.
4. Manter padrões de glob estáticos e revisados, sem entrada de usuário/metadados de PR; PR é código não confiável, inclusive seus scripts/configuração. Preservar runners hospedados efêmeros, token somente leitura, checkout sem credenciais persistidas, ausência de secrets/deploy e timeout dos jobs admin e storefront. Essas medidas limitam impacto; não corrigem braces.
5. Se não houver correção compatível, manter achado aberto, registrar bloqueio e recomendar decisão humana; não transformar ausência de patch, auditoria de produção limpa ou CI verde em exceção tácita.

## Critérios para encerrar como corrigida

- Versão corrigida comprovada por fonte oficial e cadeia efetiva sem a vulnerabilidade, ou remoção justificada da dependência com cobertura equivalente de lint; lockfile revisado e peers compatíveis.
- Instalação limpa `npm ci`, auditoria completa e de produção, explain da cadeia, lint, typecheck, build e smoke aprovados no commit proposto; eventuais alertas diferentes exigem análise separada, sem declarar árvore livre de problemas pelo desaparecimento de um pacote.
- Evidências de CI para o SHA revisado, revisão independente de segurança/QA e atualização da issue/parecer. Não encerrar com base em resultado de outro commit.
- ESLint EOL tratado ou separado explicitamente como pendência com responsável; não ocultar manutenção pendente ao corrigir SEC-003.

Uma eventual exceção temporária exige decisão humana explícita com justificativa, escopo, responsável, prazo e controles verificáveis. Ela altera o estado para risco temporariamente aceito, **não para corrigido**, e mantém acompanhamento. Nenhuma exceção é concedida por este plano.
