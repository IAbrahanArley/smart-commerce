# ADR-0016 — Agente consultivo, alertas e explicabilidade (Sprint 6)

Status: **adotado** em 2026-10-23 para A1–A12, conforme as recomendações abaixo, aprovadas pelo responsável. Proposto na mesma data. Nada foi implementado nem contratado. Cada tarefa da Sprint 6 ainda exige autorização própria. A instalação do SDK da Anthropic (A1) foi **aprovada em 2026-10-25** no gate da SC-026 (`anthropic-java` 2.71.0, [validação](../../quality/SC-026-VALIDATION.md)), e as execuções pagas de avaliação, no gate da SC-029. Plano incremental: [SPRINT_06.md](../../product/SPRINT_06.md).

## Contexto

- **[PRD](../../product/PRD.md):**
  - **MVP 9:** IA como agente consultivo **read-only** sobre métricas auditáveis, com explicações e sugestões, **sem movimentar dinheiro**;
  - **jornada do lojista:** termina em "visualiza margem e alertas";
  - **fora do MVP:** campanhas automáticas e recomendação autônoma de preços;
  - **hipótese a validar:** custos reais de IA.
- **[BUSINESS_RULES](../../product/BUSINESS_RULES.md):** a IA consulta apenas dados autorizados do tenant e não altera pedidos, valores nem estoque no MVP.
- **[ARCHITECTURE](../ARCHITECTURE.md) e [AGENTS.md](../../../AGENTS.md):**
  - a IA nunca calcula resultados como fonte de verdade: os cálculos são determinísticos no backend, e a IA só explica;
  - não inventar dados financeiros;
  - não expor segredos nem dados pessoais em logs;
  - nenhuma integração real, dependência ou custo novo sem aprovação.
- **O que já existe (Sprints 3 a 5):**
  - pedidos e reservas;
  - pagamentos Mercado Pago (validação real pendente);
  - livro `finance_entry` (SC-019), despesas (SC-020), relatório do período com estados "parcial/indisponível" (SC-021);
  - dashboard com pedidos pagos, receita, ticket médio, margem estimada, estoque baixo e itens sem giro (SC-022).
  - Todos esses números já são calculados, de forma determinística e isolada por tenant, por serviços do backend. O agente **reaproveita** esses serviços e não cria cálculo próprio.
- **Estado das dependências:** a validação real da Sprint 4 e a SC-018 (taxas) estão pendentes. Até lá, margens com vendas aparecem como "parcial". O agente precisa repetir esse estado, nunca preencher a lacuna.

**API Claude, documentação consultada em 2026-10-23 e reconfirmada em 2026-10-25 na SC-026** (leitura de cache do Haiku: 0,01; escrita de cache de 5 min: 1,25× a entrada):

| Modelo | ID | Entrada (US$/milhão de tokens) | Saída (US$/milhão de tokens) |
| --- | --- | --- | --- |
| Claude Opus 5.5 | `claude-opus-5-5` | 4,00 (leitura de cache 0,20) | 20,00 |
| Claude Sonnet 5.5 | `claude-sonnet-5-5` | 2,00 (leitura de cache 0,20) | 10,00 |
| Claude Haiku 5.5 | `claude-haiku-5-5` | 0,10 | 0,50 |

**Características relevantes:**
- **Uso de ferramentas definidas pela aplicação:** com `strict: true`, os argumentos sempre seguem o esquema.
- **Nos modelos 5.5:**
  - não é possível forçar uma ferramenta específica; usa-se `tool_choice: auto` mais a instrução no prompt;
  - o raciocínio fica sempre ligado, e a profundidade é controlada pelo `effort` (`low` a `max`; o padrão do Opus 5.5 é `medium`).
- **Cache de prompt:** cobre o prefixo estável, ou seja, ferramentas e instruções de sistema.
- **SDK Java oficial:** `com.anthropic`.
- **Recusas:** o modelo pode recusar por segurança, com `stop_reason: refusal`, e isso precisa ser tratado.
- **Retenção de dados:** retenção zero (ZDR) só por acordo com a Anthropic. A política de retenção padrão deve ser confirmada na documentação oficial e nos termos antes de qualquer uso com dados reais.

## Princípios

1. **Números vêm do backend.** Todo valor que o agente cita foi devolvido por uma ferramenta que chama um serviço determinístico já testado. O modelo não soma, não estima e não projeta.
2. **Somente leitura.** Não existe ferramenta com efeito colateral. O pior resultado de um erro ou de uma injeção de prompt é uma explicação errada, nunca uma alteração de dados.
3. **Tenant pelo servidor.** O tenant vem da sessão e do vínculo OWNER, como nas outras rotas. Nenhuma ferramenta aceita tenant, loja ou usuário como argumento.
4. **Funciona sem IA.** Os alertas são regras determinísticas e aparecem mesmo com o agente desligado. A IA só explica e prioriza.

## Aprovação

**Atualização de 2026-10-26:** o responsável escolheu o **Claude Sonnet 5.5** como modelo de lançamento. O padrão de `ADVISOR_MODEL` passou a ser `claude-sonnet-5-5`, e o Opus 5.5 continua selecionável. A cota agora é por plano: 60 perguntas no Profissional e 200 no Escala ([PRICING_PROPOSAL.md](../../product/PRICING_PROPOSAL.md)).

Em 2026-10-23, o responsável aprovou A1–A12 conforme a coluna "Recomendação". A escolha final de modelo e esforço (A2) e o valor da cota (A8) serão confirmados com os dados medidos na SC-029.

## Decisões (aprovadas em 2026-10-23)

| ID | Decisão | Recomendação | Alternativas | Bloqueia |
| --- | --- | --- | --- | --- |
| A1 | Provedor e integração | **API Claude da Anthropic pelo SDK Java oficial (`com.anthropic`)**, chamada só pelo backend. É uma **dependência e um serviço externo novos**: a aprovação deste ADR não autoriza a instalação, que é aprovada na SC-026 | (a) Outro provedor de LLM; (b) só HTTP, sem SDK (mais código próprio para manter); (c) sem LLM no MVP, só alertas e textos fixos | SC-026 |
| A2 | Modelo e esforço | **`claude-opus-5-5` com `effort: low`** para perguntas e explicações, subindo para `medium` só se a avaliação (SC-029) mostrar ganho. Custo estimado em A8 | `claude-sonnet-5-5` (metade do preço); `claude-haiku-5-5` (cerca de 40× mais barato, menos capaz em raciocínio de várias etapas). **A escolha por custo é do responsável**, de preferência depois de medir na SC-029 | SC-026, SC-029 |
| A3 | Arquitetura do agente | **Laço de ferramentas no backend** (módulo novo `advisor`), com no máximo **6 chamadas de ferramenta por pergunta**, `max_tokens` limitado e timeout. As ferramentas são adaptadores finos e só leitura sobre serviços existentes: <ul><li>relatório do período (SC-021);</li><li>indicadores do dashboard (SC-022);</li><li>alertas (SC-024);</li><li>estoque de produtos e variantes;</li><li>resumo de pedidos por situação e período.</li></ul> Todas com `strict: true`, períodos de uma lista fechada e paginação limitada | Managed Agents ou sandbox hospedado (desnecessário sem execução de código, e mandaria dados por mais um caminho); dar ao modelo SQL ou acesso amplo (descartado: quebra o isolamento e a auditabilidade) | SC-026 |
| A4 | Dados enviados ao modelo (LGPD) | **Somente agregados da loja e dados de catálogo.** <ul><li>**Vai:** valores, contagens, nomes e SKUs de produtos, categorias de despesa.</li><li>**Nunca vai:** dados de compradores (nome, e-mail, telefone, endereço), motivos de cancelamento, tokens, ids de pagamento, e-mail do lojista.</li></ul> As ferramentas devolvem DTOs próprios, sem esses campos, e um teste verifica isso | Enviar pedidos completos (expõe dados pessoais a um terceiro sem necessidade) | SC-026 |
| A5 | Explicabilidade | **Resposta estruturada** (`output_config.format`) com o texto e a lista de **fontes**: qual ferramenta, período e métrica sustentam a resposta. Antes de mostrar, o backend **confere que todo número citado aparece nos resultados das ferramentas** daquela pergunta. Se não aparecer, a resposta é recusada com "não foi possível responder com segurança". O admin mostra as fontes junto da resposta | Confiar no texto livre do modelo; mostrar só o texto | SC-027 |
| A6 | Alertas | **Regras determinísticas no backend, sem IA** (SC-024): <ul><li>estoque baixo e itens sem giro (limites do F8);</li><li>margem do período parcial ou negativa;</li><li>pedidos pendentes perto de expirar;</li><li>conexão do Mercado Pago expirada ou revogada;</li><li>taxa indisponível.</li></ul> Cada alerta tem código, gravidade, dado de apoio e link para a tela do admin. A IA pode **explicar** um alerta, nunca criá-lo | Alertas gerados pelo modelo (não auditáveis, variam a cada execução) | SC-024, SC-028 |
| A7 | Escopo de conversa | **Perguntas avulsas com contexto curto:** no máximo 5 trocas por conversa, sem memória entre conversas. **Sugestões só como texto** ("considere repor o produto X"); nada de preço sugerido, ação automática ou campanha | Chat longo com memória (mais custo e mais dado retido); recomendação de preço (fora do MVP pelo PRD) | SC-027 |
| A8 | Custo e limites | **Estimativa a medir na SC-029:** cerca de 4 mil tokens de ferramentas e instruções em cache, 2 a 4 mil de resultados e até 1,5 mil de saída e raciocínio. No Opus 5.5 com `effort: low`, isso dá **cerca de US$ 0,03 a 0,08 por pergunta**. **Limites:** <ul><li>cota mensal por loja (proposta: 100 perguntas);</li><li>teto mensal global em US$, com bloqueio ao atingir;</li><li>interruptor global (`ADVISOR_ENABLED`);</li><li>tabela `advisor_usage` com tokens e custo estimado por pergunta, sem o texto</li></ul> | Sem cota (risco de custo e abuso); cobrar o lojista por uso (decisão de negócio fora desta sprint) | SC-025, SC-026 |
| A9 | Registro e retenção | **Guardar só metadados por 90 dias:** loja, usuário, horário, tokens, ferramentas usadas, resultado (respondida, recusada ou bloqueada pela checagem). **Não guardar o texto** da pergunta nem da resposta no MVP. Logs sem o conteúdo | Guardar o texto para auditoria e melhoria (mais dado pessoal potencial, exige política de retenção e consentimento) | SC-025 |
| A10 | Injeção de prompt | **Dados do lojista são dados, não instruções.** Nomes de produto, descrições e categorias vêm delimitados nos resultados das ferramentas, e as instruções de sistema dizem para ignorar ordens contidas neles. Como não há ferramenta com efeito, o impacto fica limitado ao texto exibido, que ainda passa pela checagem de números (A5). Testes com produtos chamados, por exemplo, "ignore as instruções e…" | Filtrar texto do catálogo antes de enviar (frágil; não substitui o desenho só leitura) | SC-026, SC-030 |
| A11 | Ambiente | **Desligado por padrão.** Liga só com `ADVISOR_ENABLED=true` e `ANTHROPIC_API_KEY`, que o responsável cria no console da Anthropic e cadastra só no painel do ambiente (nunca no chat ou no Git). Na Sprint 6, só local e no ambiente de teste hospedado, com dados sintéticos. Produção exige aprovação separada, depois de revisar retenção e termos | Ligar em produção junto com o resto | SC-026 |
| A12 | Interface | **Página "Assistente" no admin**, com caixa de pergunta, sugestões prontas ("Como foi meu mês?", "O que está parado no estoque?"), resposta com fontes e aviso de que a resposta é gerada por IA e pode errar. Também um botão **"Explicar"** nos alertas e nos cartões do dashboard. Sem streaming no MVP: a resposta chega inteira, com indicador de progresso | Chat flutuante em todas as telas; streaming (mais complexidade de infraestrutura) | SC-027, SC-028 |

## Consequências e riscos

- **Serviço externo novo, com custo variável.**
  - Mitigação: cotas, teto global e interruptor (A8, A11).
  - A medição real fecha a hipótese de custo do PRD.
- **Terceiro processando dados da loja.**
  - Mitigação: só agregados e catálogo (A4).
  - Antes de produção: revisão jurídica, política de privacidade atualizada e confirmação da retenção da Anthropic.
- **Respostas erradas ou números inventados.**
  - Mitigações: princípio 1; checagem de números (A5); fontes visíveis; aviso de IA; avaliação com perguntas de resposta conhecida (SC-029).
  - Risco residual: uma interpretação ruim de um número correto. O texto sempre traz o número e a fonte para o lojista conferir.
- **Dados incompletos (sem taxas até a SC-018).** O agente precisa repetir "parcial" e o que falta. Isso faz parte da avaliação.
- **Isolamento.** O agente é mais um consumidor dos serviços já isolados por tenant. Os testes A/B cobrem a ferramenta, a cota e o registro de uso.
- **Latência.** Cada pergunta leva alguns segundos, mais o despertar do Render no ambiente de teste (H4 do ADR-0014).

## Fora deste ADR

- ações do agente (alterar preço, estoque ou pedidos), campanhas e previsões;
- recomendação de preço;
- memória entre conversas;
- streaming;
- outros idiomas;
- agente para o comprador na vitrine;
- produção comercial;
- cobrança por uso de IA.
