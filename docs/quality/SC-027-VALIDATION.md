# SC-027 — Página "Assistente" e explicabilidade: validação

Data: 2026-10-25. Base: [ADR-0016](../architecture/adr/0016-consultative-agent.md) (A5, A7, A12) e [SPRINT_06.md](../product/SPRINT_06.md).

**Estado:** implementada e validada com o servidor falso da API de mensagens (backend) e com um backend sintético em loopback (admin). Nenhuma chamada real à Anthropic foi feita. O assistente continua **desligado por padrão**.

## Backend

**Resposta estruturada (A5):**
- A resposta final segue `output_config.format` com esquema fechado: `answer` (texto) e `sources` (lista de `tool` e `metric`).
- Uma saída fora do formato vira `UNGROUNDED` e nunca é exibida.

**Checagem de números (`AdvisorGrounding`):** todo número e toda data da resposta precisam aparecer nos resultados das ferramentas **daquela pergunta** ou na própria pergunta. Senão, a resposta é recusada (`UNGROUNDED`, sem texto).
- **Formatos aceitos:**
  - "R$ 1.234,56", "1.234" (milhar) e "150.00" (texto de máquina);
  - percentuais com vírgula;
  - datas dd/mm e dd/mm/aaaa.
- **Arredondamento:** vale o valor da fonte arredondado na precisão mostrada (meio para cima). Por exemplo, 33,33 aceita "33,3%" e "33%", mas não "34%".
- **Sinal:** é ignorado ("reembolsos de R$ 20,00" para -20,00).
- **Fração em percentual:** uma fração vale também como percentual (0,25 aceita "25%").
- **Datas:** um instante conta no dia de São Paulo e no dia em UTC. O dia e o mês de uma data **não** valem como números soltos, porque aterrariam qualquer número pequeno (por exemplo, "R$ 1 milhão"). Só o ano vale sozinho.
- **Número derivado:** uma soma ou diferença que não está nos dados é recusada. As instruções avisam o modelo.

**Fontes:**
- Só ferramentas que **rodaram** na pergunta, com o período em que rodaram. Fontes citadas pelo modelo e não executadas são descartadas.
- A métrica é o texto do modelo, limpo e com até 80 caracteres.
- Se a resposta tem números e o modelo não citou fonte, aparecem as ferramentas que rodaram.

**Conversa curta (A7):**
- A pergunta pode trazer até 4 trocas anteriores respondidas, que o servidor valida como entrada e **não guarda**.
- Uma quinta troca, ou uma troca sem resposta, vira 400 antes do modelo.
- Números de respostas anteriores não servem de fonte: o modelo precisa consultar a ferramenta de novo.

## Admin

**Página `/lojas/[id]/assistente`** (item "Assistente" na navegação da loja):

| Estado | O que mostra |
| --- | --- |
| Carregando | status de consulta |
| Desligado ou sem chave | "Assistente indisponível", com os alertas e relatórios seguindo normais |
| Loja de outro dono | "Loja indisponível" |
| Erro | mensagem e "Tentar novamente" |
| Pronto | aviso fixo de IA ("Respostas geradas por IA… confira"), sugestões prontas, pergunta (até 500 caracteres, com contador e cota restante), conversa e fontes |

**Respostas:**
- `REFUSED` e `UNGROUNDED` aparecem como texto próprio, sem conteúdo do modelo.
- Cada resposta aceita lista as fontes no formato "Ferramenta · Período · métrica".
- Ferramentas desconhecidas e períodos inválidos vindos da API são descartados.

**Limite:** depois de 5 perguntas, o formulário dá lugar a "Conversa completa" e "Nova conversa". A conversa só existe na página.

**Erros:**

| Código | Mensagem |
| --- | --- |
| 429 | cota da loja |
| 503 | indisponível |
| 400 | pergunta inválida |
| 401 | sessão expirada |

**Acessibilidade (objetivo WCAG 2.2 AA):**
- uma região `role="status"` educada anuncia "Consultando…", "Resposta recebida." e "Nova conversa iniciada.";
- o foco vai para cada nova resposta, um `article` rotulado por "Resposta N";
- em "Nova conversa", o foco volta ao campo, mesmo vindo do estado "Conversa completa";
- a pergunta é anunciada como "Você perguntou:";
- o erro fica ligado ao campo por `aria-describedby` e `role="alert"`;
- Enter envia e Shift+Enter quebra linha;
- os botões e o campo usam os tokens e o foco do design system.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `AdvisorGroundingTest` (3) | <ul><li>formatos brasileiros, arredondamento, sinal, percentual de fração, datas e fuso</li><li>recusa de número inventado, arredondamento errado, data inexistente e malformada</li><li>números da pergunta como fonte, número derivado recusado</li></ul> |
| `AdvisorAssistantIT` (6) | <ul><li>fontes com período e descarte de fonte não executada</li><li>esquema do `output_config.format`</li><li>número sem fonte, número "de memória" e saída fora do formato recusados</li><li>fonte padrão quando o modelo não cita nenhuma</li><li>histórico de 2 trocas encaminhado e quinta troca recusada</li><li>mais os casos da SC-026</li></ul> |
| `npm run lint`, `typecheck`, `build`, `test:smoke` (admin) | aprovados. O smoke cobre os helpers (`parseAnswer`, `sourceText`, `historyFor`, mensagens e cota) e a rota protegida |
| Navegador (painel do Claude, backend sintético, largura de 384 px e 1280 px) | <ul><li>sugestão respondida com fontes</li><li>pergunta recusada com texto próprio</li><li>foco na resposta e anúncio no status</li><li>5 perguntas levam a "Conversa completa"</li><li>"Nova conversa" devolve o foco ao campo (corrigido nesta validação)</li><li>sem rolagem horizontal a 384 px</li><li>sem erros no console</li></ul> |
| `mvnw clean verify` (2026-10-25) | 92 testes unitários e 155 de integração, todos passando |

## Limitações

- **Sem modelo real:** a qualidade das respostas e a taxa de recusa pela checagem de números só serão medidas com a chave no ambiente hospedado e na SC-029.
- **Checagem conservadora:** respostas com cálculos próprios do modelo (diferenças, somas) são recusadas de propósito. Se isso for frequente, a alternativa é uma ferramenta determinística de comparação, a decidir na SC-029.
- **Acessibilidade:** leitor de tela real, zoom a 200% e 400% e espaçamento de texto seguem pendentes, como nas sprints anteriores. Não há alegação de conformidade completa.
- **"Explicar" nos alertas e no dashboard** é a SC-028.
