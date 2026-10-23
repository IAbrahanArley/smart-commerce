# SC-025 — Fundação do assistente (sem chamar modelo): validação

Data: 2026-10-23. Base: [ADR-0016](../architecture/adr/0016-consultative-agent.md) (A3, A4, A8, A9, A11) e [SPRINT_06.md](../product/SPRINT_06.md).

Nenhum modelo de IA é chamado nesta tarefa e nenhuma dependência nova entrou. A integração com a API fica para a SC-026.

## O que foi entregue

**Interruptor e limites (A8, A11):**
- `ADVISOR_ENABLED` vem desligado por padrão.
- A cota mensal por loja (`ADVISOR_MONTHLY_QUOTA`, padrão 100) segue o mês civil de São Paulo.
- O teto mensal da plataforma (`ADVISOR_GLOBAL_MONTHLY_CAP_USD`, padrão 20,00) bloqueia todas as lojas quando o custo estimado do mês o atinge.

**Reserva de cota (`AdvisorQuotaService.start`):**
- confere o interruptor, o vínculo OWNER, o teto e a cota;
- grava uma linha `PENDING` sob *advisory locks* do PostgreSQL, sempre na mesma ordem (global, depois loja), para que perguntas simultâneas nunca ultrapassem o limite nem travem entre si;
- `finish` fecha a linha uma única vez, com o que a pergunta consumiu;
- falha do provedor (`PROVIDER_ERROR`) e pergunta cancelada não gastam cota.

**Registro de uso (A9):**
- **Migração `V24__advisor_usage.sql`** (aditiva). A tabela guarda **só metadados**:
  - loja, usuário e horário;
  - resultado;
  - modelo;
  - tokens de entrada, cache e saída;
  - quantidade e nomes das ferramentas usadas;
  - custo estimado.
- **Nunca guarda o texto** da pergunta nem da resposta, e um teste confere que a tabela não tem coluna para isso.
- **Rotina horária de limpeza:**
  - perguntas `PENDING` presas por mais de 10 min (processo que caiu) passam a `CANCELLED`;
  - linhas com mais de 90 dias são apagadas.

**Ferramentas só leitura (A3, A4):** `AdvisorTool` e `AdvisorToolService`.

| Ferramenta | Argumentos (listas fechadas) | Fonte dos números |
| --- | --- | --- |
| `relatorio_periodo` | `periodo` | relatório financeiro (SC-021) |
| `indicadores_dashboard` | `periodo` | dashboard (SC-022) |
| `alertas` | — | alertas (SC-024) |
| `estoque_produtos` | `filtro` (ESTOQUE_BAIXO, SEM_GIRO), `limite` opcional (1 a 20) | listas de estoque (F8) |
| `resumo_pedidos` | `periodo` | contagem de pedidos por situação, nova em `OrderAdminService.countsByStatus` |

**Regras das ferramentas:**
- **Esquema JSON estrito:** objeto fechado (`additionalProperties: false`), enums e intervalo. É o formato que a SC-026 vai enviar com `strict: true`.
- **Loja e usuário:** vêm sempre de quem chama (a sessão). Nenhum argumento aceita loja, usuário ou pedido, e qualquer argumento extra é recusado.
- **Resultados:** DTOs próprios (`ToolResults`), só com agregados e catálogo. Não há id de loja, usuário, pedido ou pagamento, nem SKU, custo por item ou dado de comprador. Valores parciais continuam marcados (`complete=false` e `missing`).

## Testes

| Teste | Cobertura |
| --- | --- |
| `AdvisorToolTest` (3, unitário) | <ul><li>esquemas fechados</li><li>nenhum argumento de loja, usuário ou pedido</li><li>argumentos válidos e inválidos: obrigatório ausente, valor fora da lista, tipo errado, `tenant` extra, limite 0, 21 e texto, argumento em ferramenta sem argumentos</li><li>ferramenta desconhecida</li><li>validação do registro de uso</li></ul> |
| `AdvisorFoundationIT` (6, PostgreSQL) | <ul><li>**números:** as ferramentas devolvem os mesmos números do relatório e do dashboard, inclusive o estado parcial</li><li>pedidos por situação e estoque baixo</li><li>**privacidade:** o resultado serializado de todas as ferramentas sem e-mail, nome e telefone do comprador, sem SKU, ids, nem campo de custo, mesmo com nome de produto contendo uma tentativa de instrução</li><li>**isolamento:** o dono B recebe `MISSING` em todas as ferramentas da loja A</li><li>**cota:** bloqueia na 4ª pergunta; erro do provedor devolve a pergunta; fechar duas vezes ou com outra loja não muda nada; cotas separadas por loja</li><li>**concorrência:** 10 perguntas simultâneas com cota 3 resultam em exatamente 3</li><li>**teto global e interruptor:** o teto bloqueia outras lojas, e o interruptor desligado responde `DISABLED`</li><li>**limpeza:** pendente preso vira cancelado, e o registro com 91 dias é apagado</li></ul> |
| `ArchitectureTest` | camadas do módulo novo |

## Comandos executados (2026-10-23)

- **Backend:** `mvnw verify` completo, com **86 testes unitários e 146 de integração, todos passando**. Isso inclui `AdvisorToolTest` (3), `AdvisorFoundationIT` (6) e `AlertIT` (4).
- **Admin:** `lint`, `typecheck`, `build` e `test:smoke`, todos passando.
