# Sprint 6 — Agente consultivo, alertas e explicabilidade (planejamento)

Data: 2026-10-23. **A1–A12 do [ADR-0016](../architecture/adr/0016-consultative-agent.md) aprovadas em 2026-10-23.** Cada tarefa ainda exige autorização própria.

**Objetivo:**
- o lojista recebe **alertas** objetivos sobre estoque, margem, pedidos e pagamentos;
- pode **perguntar em português** sobre os números da loja e receber respostas curtas, com as **fontes** de cada número.

O agente só lê: não altera pedidos, valores nem estoque, e não inventa números. Tudo que ele cita vem de serviços determinísticos do backend.

**Dependências:**
- A validação real da Sprint 4 e a SC-018 continuam pendentes ([SPRINT_04.md](SPRINT_04.md), [SPRINT_05.md](SPRINT_05.md)). Elas não bloqueiam esta sprint: o agente mostra "parcial" quando faltar a taxa.
- A SC-023 (revisão integrada da Sprint 5) deve ser concluída antes da SC-026, porque o agente vai repetir os números do relatório e do dashboard.

## Gates de entrada

| Tarefa | Requer |
| --- | --- |
| SC-024 | A6 aprovada |
| SC-025 | A3, A4, A8, A9 aprovadas |
| SC-026 | SC-023 e SC-025; A1, A2, A10, A11 aprovadas; **aprovação da dependência** `com.anthropic` (SDK Java); o responsável cria a chave no console da Anthropic e a cadastra só no ambiente |
| SC-027 | SC-026; A5, A7, A12 |
| SC-028 | SC-024, SC-027 |
| SC-029 | SC-027; orçamento aprovado para as execuções de avaliação (cada uma tem custo real) |
| SC-030 | SC-024 a SC-029 |

## SC-024 — Alertas determinísticos (módulo `advisor`, sem IA)

**Estado (2026-10-23):** autorizada e implementada ([validação](../quality/SC-024-VALIDATION.md)).

- Serviço de alertas por loja, calculado na hora a partir dos serviços existentes. Cada alerta tem código, gravidade (informativo, atenção, crítico), dado de apoio e link para a tela do admin. Os alertas:
  - estoque baixo e itens sem giro (limites do F8);
  - margem do período "parcial" (com o motivo) ou negativa;
  - pedidos pendentes a menos de 10 min de expirar;
  - conexão do Mercado Pago expirada ou revogada;
  - pagamentos com taxa indisponível.
- **Admin:** faixa de alertas na visão geral da loja e lista completa, sem dados de compradores.

**Aceite:** com dados conhecidos, os alertas certos aparecem e somem quando a causa some; nada depende de IA.

**Testes:**
- cada regra nos limites;
- loja sem dados;
- A/B (alertas de A nunca aparecem para B);
- smoke do admin.

## SC-025 — Fundação do assistente, sem chamar modelo

**Estado (2026-10-23):** autorizada e implementada ([validação](../quality/SC-025-VALIDATION.md)).

- **Módulo `advisor`:** configuração `ADVISOR_ENABLED` (desligado por padrão), teto mensal global e cota por loja (A8).
- **Migração aditiva:** tabela `advisor_usage`, só com metadados e sem texto (A9), e expiração em 90 dias pela rotina existente.
- **Ferramentas só leitura (A3, A4)**, como serviços Java com DTOs próprios:
  - `relatorio_periodo`;
  - `indicadores_dashboard`;
  - `alertas`;
  - `estoque_produtos`;
  - `resumo_pedidos`.
- **Períodos:** de uma lista fechada.
- **Tenant:** sempre o da sessão.

**Aceite:** as ferramentas devolvem os mesmos números do relatório e do dashboard, sem nenhum campo pessoal; a cota bloqueia ao atingir o limite.

**Testes:**
- igualdade de números com SC-021 e SC-022;
- verificação de que nenhum DTO tem nome, e-mail, telefone ou endereço de comprador;
- cota e teto concorrentes;
- A/B.

## SC-026 — Integração com o modelo

- **Dependência:** SDK Java oficial da Anthropic (`com.anthropic`), sujeito à aprovação do gate.
- **Modelo (A2):** `claude-opus-5-5` com `effort: low`.
- **Ferramentas:** com `strict: true` e `tool_choice: auto`.
- **Limites:** no máximo 6 chamadas de ferramenta por pergunta, `max_tokens` limitado e timeout.
- **Cache de prompt:** cobre ferramentas e instruções.
- **Instruções de sistema em português (A10):** dados do lojista são dados, não ordens; nunca estimar; repetir "parcial" e o que falta.
- **Tratamento de falhas:**
  - **recusa** (`stop_reason: refusal`): mensagem genérica;
  - **erro ou limite do provedor:** "indisponível";
  - **cota atingida:** mensagem própria.
- **Segurança e uso:** chave e conteúdo nunca em log; uso registrado em `advisor_usage`.

**Aceite:** com o interruptor ligado e a chave definida, uma pergunta é respondida usando as ferramentas; desligado ou sem chave, a página informa "indisponível".

**Testes** (sem chamar a API real, com servidor falso em loopback):
- laço de ferramentas;
- limite de chamadas;
- recusa e erros;
- **injeção de prompt** por nome de produto;
- **A/B:** a pergunta da loja A só aciona ferramentas com o tenant A;
- logs sem chave nem conteúdo.

## SC-027 — Página "Assistente" e explicabilidade

- **Página no admin (A12):**
  - pergunta e sugestões prontas;
  - resposta com **fontes** (ferramenta, período, métrica) e aviso de IA;
  - até 5 trocas por conversa (A7).
- **Resposta estruturada com checagem de números (A5):** todo número citado precisa aparecer nos resultados das ferramentas daquela pergunta; se não aparecer, a resposta é recusada.

**Aceite:** respostas mostram fontes; uma resposta com número sem fonte nunca chega à tela.

**Testes:**
- checagem de números (formatos brasileiros, percentuais, arredondamento);
- smoke do admin;
- acessibilidade (WCAG 2.2 AA): foco, leitor de tela, região de status.

## SC-028 — "Explicar" alertas e indicadores

- Botão **"Explicar"** nos alertas (SC-024) e nos cartões do dashboard (SC-022), que abre o assistente com a pergunta e o contexto daquele item.
- Sem o assistente ligado, o botão não aparece.

**Aceite:** a explicação cita o mesmo número do cartão e a sua fonte.

## SC-029 — Avaliação e custo medido

- **Conjunto de perguntas:** cerca de 30 perguntas com resposta conhecida, sobre uma loja sintética com números calculados à mão. Cobre:
  - mês completo e parcial;
  - estoque;
  - pedidos;
  - perguntas fora do escopo ("mude o preço…");
  - tentativas de injeção.
- **Critérios:**
  - números corretos e com fonte;
  - "parcial" quando falta dado;
  - recusa de ações;
  - nenhum dado pessoal.
- **Medição:** tokens e custo por pergunta. A decisão final de modelo e esforço (A2) e da cota (A8) fica registrada no ADR.

**Aceite:** relatório da avaliação com a taxa de acerto, os erros classificados e o custo médio. Em números e fontes, a meta é **100%**, porque a checagem do A5 bloqueia o que não tem fonte.

## SC-030 — Revisão integrada

- **Checklists:** [SECURITY.md](../agents/SECURITY.md) e [QA.md](../agents/QA.md), com foco em:
  - isolamento por tenant;
  - LGPD (dados enviados ao provedor, retenção);
  - injeção de prompt;
  - custo;
  - acessibilidade.
- **Documentação:** atualização de ARCHITECTURE, DATA_MODEL e do status do ADR-0016.

## Definition of Done

- **Testes executados:** unidade, integração PostgreSQL, ArchUnit e smoke. As chamadas de modelo são testadas com servidor falso; a API real só entra na SC-029, com orçamento aprovado.
- **Alertas:** funcionam com o assistente desligado.
- **Proteção de dados:**
  - nenhum dado pessoal de comprador enviado ao provedor;
  - nenhum texto de conversa guardado;
  - nenhuma chave em log, chat ou Git.
- **Base:** migrações aditivas e documentação atualizada.
- **Revisão:** multi-tenant e aprovação humana.

## Fora da sprint

- ações do agente (preço, estoque, pedidos, campanhas);
- previsões;
- memória entre conversas;
- streaming;
- agente na vitrine;
- produção comercial;
- cobrança por uso de IA.
