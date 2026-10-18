# SC-001 — Proteção de main e validação por pull request

> **Registro histórico**, resgatado em 2026-10-18 do [PR #1](https://github.com/IAbrahanArley/smart-commerce/pull/1), que foi fechado sem merge porque seus planos da SC-002 foram substituídos pelo [ADR-0005](../architecture/adr/0005-identity-authentication.md), [SC-002-PLAN.md](../product/SC-002-PLAN.md) e [SC-002-TEST-PLAN.md](SC-002-TEST-PLAN.md). Descreve a proteção de `main` como configurada em 2026-10-15. Desde então a CI ganhou o job `storefront`; a inclusão dele entre os checks obrigatórios é decisão separada do responsável e deve ser registrada aqui quando aplicada.

Data: 2026-10-15. Escopo: governança do repositório e documentação. Nenhum código da SC-002 foi implementado.

## Proteção aplicada

Destino confirmado: `IAbrahanArley/smart-commerce`, privado, branch `main`. A API retornou 200 na aplicação e na leitura posterior da regra. Não houve contratação de plano, mudança para público ou alteração de cobrança.

Os checks existentes foram consultados no commit `83794ff95feb8847957d9d5fb2afe064c32b279e`: nomes reais `backend` e `admin`, ambos emitidos por GitHub Actions (`app_id=15368`). A política exige esses nomes e esse emissor, evitando aceitar um status homônimo de outro aplicativo.

Configuração aplicada pela API de proteção de branch:

```json
{
  "required_status_checks": {
    "strict": true,
    "checks": [
      { "context": "backend", "app_id": 15368 },
      { "context": "admin", "app_id": 15368 }
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
```

Efeitos verificados na leitura remota:

- Checks `backend` e `admin` obrigatórios e branch atualizada antes de merge (`strict=true`).
- A regra também vale para administradores; não foi criada lista de bypass.
- Mudanças passam por PR. São exigidas zero aprovações externas, pois não há equipe revisora definida e o autor não pode aprovar seu próprio PR. Isso não é revisão humana independente nem aceite automático de produto/segurança. Uma exigência adicional de aprovação pode ser configurada quando houver revisor disponível.
- Force push e exclusão de `main` bloqueados; conversas devem ser resolvidas.
- Assinaturas obrigatórias, histórico linear e merge queue não foram habilitados.

Não foi feito push deliberadamente inválido nem tentativa de merge com falha para testar negação. A evidência da política é a configuração retornada pela API; a evidência do evento `pull_request` será registrada após o PR documental. Uma tentativa inicial combinando os campos alternativos `contexts` e `checks` recebeu 422, sem alterar a regra; o payload final usa somente `checks` e foi aceito.

Referências oficiais: [proteção de branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches), [API de proteção](https://docs.github.com/en/rest/branches/branch-protection?apiVersion=2022-11-28). Dados brutos locais ignorados: `.tools/main-protection-applied.json`, `.tools/main-protection-current.json` e `.tools/main-required-checks-current.json`.

## PR de documentação

Branch: `docs/sc-001-governance`. [PR #1](https://github.com/IAbrahanArley/smart-commerce/pull/1) aberto contra `main`, sem alterar aplicação, dependências ou workflow, para comprovar o evento `pull_request` nos dois jobs. Não será mesclado automaticamente; ficará disponível para revisão humana. Também contém o planejamento da SC-002 explicitamente proposto, sem implementação.

Primeira validação: [run 38804591777](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38804591777), evento `pull_request`, concluído com sucesso em 2026-10-15T20:46:56Z. Head `dcff5cc413034699a8852e47130eed86a9527976`, base `83794ff95feb8847957d9d5fb2afe064c32b279e`, merge de teste `c01658d14419f0368bb72c02f066a375584f5f4e`. Jobs backend `116662036552` e admin `116662037148` aprovados. A API retornou PR aberto, mergeable e estado clean após os checks. Esse merge SHA é o merge de teste da CI; não houve merge em main. Atualizações documentais posteriores exigem nova execução, sem atribuir a elas antecipadamente este resultado.

Critérios verificáveis:

1. Registrar número/URL do PR, head SHA e SHA de merge de teste do GitHub; confirmar base `main`.
2. Identificar execução `CI` com evento `pull_request` vinculada a esse PR. Uma execução de `push` na branch não substitui essa evidência.
3. Backend aprovado com três testes de segurança + três integrados, zero falhas/erros/ignorados, PostgreSQL real e `BUILD SUCCESS`.
4. Admin aprovado com instalação limpa, lint, tipos, build e smoke HTTP 200/404.
5. Confirmar os dois required checks sem pendência no PR e a manutenção da política de proteção.
6. Registrar evidências em [SC-001-VALIDATION.md](SC-001-VALIDATION.md), distinguindo o commit efetivamente testado de posteriores mudanças documentais.

## SEC-003 e limites do aceite

SEC-003 continua aberta, sem aceitação formal do risco. O acompanhamento possui responsável por papel, revisão manual e critérios de fechamento em [SEC-003-FOLLOW-UP.md](../security/SEC-003-FOLLOW-UP.md). CI verde e proteção de branch não corrigem a dependência de lint afetada nem equivalem a auditoria de dependências.

A validação técnica da SC-001 foi reconhecida pelo usuário. Esta etapa adiciona proteção e prova de PR; não autoriza implementar SC-002 ou presumir aprovadas as escolhas de autenticação propostas no ADR-0005.
