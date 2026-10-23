# Sprint 5 — Validação das telas e preparação da SC-023

Data: 2026-10-22. Base: SC-020 a SC-022, ADR-0015 e DESIGN.md. Código-base: `363247e`, integrado na `main` pelo merge `6cd07de` (PR #25), mais as correções desta validação.

**Estado:** inspeção de interface realizada e regressões corrigidas. Não encerra a SC-023 nem a Sprint 5: a SC-018 e a validação real do Mercado Pago em sandbox continuam pendentes.

## Ambiente e limites da evidência

- Windows, Node.js 24, admin Next.js em desenvolvimento e Chromium do navegador integrado do Codex.
- API HTTP sintética local, separada do PostgreSQL e do Mercado Pago. Identidade, loja, produtos, vendas e despesas fictícios. Registro e cancelamento nesta sessão alteraram somente a memória dessa API, sem tocar dados da loja do responsável.
- Cenário financeiro conhecido: receita 180,00; três pedidos; ticket 60,00; reembolsos 80,00; CMV 70,00; despesas variáveis 15,00; margem parcial 15,00 (15,00%); despesas fixas 500,00; a receber 180,00; caixa −15,00. Taxas e um custo ausentes, explicitados pela interface. Os números da fixture verificam apresentação; os cálculos reais do backend permanecem cobertos pelas integrações das tarefas anteriores.
- Larguras 320, 390, 768 e 1440 CSS px, altura de 844 a 1000 px. Medição nas três telas: `scrollWidth` da página igual ao `clientWidth`, sem rolagem horizontal global nos cenários inspecionados. Essa medição não substitui a conferência de cada conteúdo possível.

## Achados e correções

| Severidade | Local | Evidência e disposição |
| --- | --- | --- |
| P2 | `admin-web/src/lib/catalog.ts:26` | Caixa −15,00 aparecia como “Valor indisponível”: o formatador aceitava apenas valores positivos. Corrigido sem conversão monetária para float; smoke cobre −15,00, −0,01 e valor grande. Navegador confirmou −R$ 15,00 após a correção. |
| P2 | `admin-web/src/components/store-expenses.tsx:63` e `:120` | Navegar ao próximo mês apagou o rascunho sem aviso; corrigir outra despesa substituiu o rascunho. Adicionadas guarda de links/saída do documento, seguindo o padrão existente do admin, e confirmação em diálogo antes de substituir o formulário na correção. Escape preservou o rascunho; confirmação explícita permitiu a correção. |
| P2 | `admin-web/src/components/store-expenses.tsx:57` e `:194` | “Manter despesa” deixou foco no BODY; correção focou aviso no topo após rolar ao formulário. Agora o foco volta ao botão de origem ou ao título do formulário, respectivamente; ambos conferidos no navegador. |
| P2 | `admin-web/src/lib/expenses.ts:64` e `store-expenses.tsx:90` | A validação de competência só verificava formato e a mensagem de 400 não explicava a competência inválida. Helper espelha piso de 2000, teto de um ano e pagamento até hoje no fuso de São Paulo. Data além do limite exibiu erro associado e foco no campo; smoke inclui calendário inválido, fuso e ano bissexto. |
| P2 | `admin-web/src/components/store-expenses.tsx:189` e `:200` | Campos permaneciam editáveis enquanto o POST estava pendente, permitindo perder alterações no reset de sucesso. Campos agora desabilitados durante envio/cancelamento; conferidos com atraso sintético de 2,5 segundos. |
| P2 | `admin-web/src/components/store-finance.tsx:77` | Texto sugeria completar CMV histórico alterando o custo atual, embora pedidos usem snapshots. Texto esclarece que o cadastro de custo beneficia os próximos pedidos e não completa os anteriores. |
| P2 | `admin-web/src/components/store-dashboard.tsx:51` | Resposta 401 mostrava apenas erro genérico e nova tentativa. Dashboard agora orienta entrar novamente; 403/404 têm estado de loja indisponível, como financeiro e despesas. |
| P3 | `admin-web/src/components/store-finance.tsx:71`, `store-dashboard.tsx:81` e `store-expenses.tsx:173` | Na apresentação móvel, valores financeiros/diários recebiam “:” sem rótulo e categoria de despesa ficava colada à descrição. Rótulos de valor/pedidos/receita e quebra da informação secundária corrigidos. Descrição do gráfico usa “1 dia”; estado vazio de estoque não presume vendas recentes de produtos novos. |

## Verificações executadas

| Verificação | Resultado |
| --- | --- |
| Dashboard, financeiro e despesas em quatro larguras | Executada; conteúdo e ações preservados nos cenários inspecionados, sem overflow global |
| Gráfico SVG e tabela equivalente | Executada; tabela aberta/fechada com Enter, sete dias com receita 180,00 e três pedidos no último dia |
| Filtros de período | Executada; dashboard mudou para últimos sete dias, financeiro para hoje; URL, seleção e intervalo atualizados. Mês anterior sem vendas conferido por URL |
| Estados vazios | Executada; dashboard com zero vendas/ticket indisponível e listas vazias; financeiro sem vendas e despesas em mês vazio |
| Sessão expirada (401), loja indisponível (404), erro (503) | Executada nas três telas com respostas sintéticas; botão de nova tentativa recuperou o dashboard após restabelecer a API |
| Despesas: registro, histórico cancelado e correção | Executada na fixture; rascunho protegido na substituição, Escape cancela o descarte, foco restaurado e campos bloqueados durante POST |
| Teclado | Executada parcialmente: skip link, menu móvel com Enter, tabela equivalente, diálogo de rascunho/Escape, foco após erro e correção. Foco de botão com contorno sólido de 3 px conferido |
| Contraste pontual | Cores renderizadas: branco sobre ação `#174E73` = 8,85:1; texto secundário `#365165` sobre branco = 8,33:1; foco `#924B00` sobre branco = 6,50:1. Não certifica todos os pares/estados |
| Admin: lint, typecheck, build e test:smoke | Executados/aprovados; smoke ampliado para datas e moeda negativa. Aviso já conhecido de tipo de módulo do Node no smoke |
| Backend e vitrine | Sem mudança de comportamento/código nesta etapa; suíte backend e checks da vitrine não repetidos. Não confundir com uma nova validação do isolamento real A/B |

## Revisão e pendências

Revisão independente de QA somente leitura, seguida de conferência do diff corrigido, sem novo bloqueador. Skills aplicadas: `web-design-guidelines` e `vercel:react-best-practices`. Fonte atual: [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md), consultada em 2026-10-22; DESIGN.md prevalece nas convenções da plataforma. Sem dependência, identidade visual, endpoint ou schema novo.

- Confirmação nativa de saída com rascunho: a tentativa deixou a aba sem resposta na ferramenta de navegador. A proteção está implementada conforme o padrão existente; aceitar/recusar e conferir o rascunho após a recusa continuam pendentes de teste manual. A guarda não cobre Voltar/Avançar em histórico SPA.
- Leitor de tela, zoom manual a 200%/400%, espaçamento de texto e Axe não executados. Reflow a 320 CSS px foi medido; não equivale a afirmar que o zoom manual foi testado. Nenhuma alegação de conformidade WCAG completa.
- Capturas de dashboard desktop, financeiro móvel e despesas móveis foram salvas como artefatos locais da sessão, fora do repositório, com dados sintéticos. Capturas full-page podem reposicionar elementos fixos; a inspeção também usou screenshots de viewport.
- Próxima ação para o fechamento: concluir sandbox da Sprint 4 e SC-018; completar SC-023 com dados integrados, revisão de segurança multi-tenant e demais pendências de acessibilidade.
