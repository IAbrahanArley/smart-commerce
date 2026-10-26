# SC-028 — "Explicar" alertas e indicadores: validação

Data: 2026-10-26. Base: [ADR-0016](../architecture/adr/0016-consultative-agent.md) (A5, A12) e [SPRINT_06.md](../product/SPRINT_06.md). Só admin; nenhuma mudança no backend.

**Estado:** implementada e validada com um backend sintético em loopback. Nenhuma chamada real à Anthropic.

## O que foi entregue

**Botão "Explicar":**
- Fica em cada alerta (SC-024), nos quatro cartões do painel (pedidos pagos, receita, ticket médio e margem) e nas listas de estoque baixo e sem giro. A lista só tem o botão quando tem itens.
- É um link discreto, sem disputar atenção com o número.
- O nome acessível diz o que será explicado, por exemplo "Explicar receita" ou "Explicar o alerta: 1 item com estoque baixo…".

**Visibilidade:** a visão geral lê o status do assistente uma vez.
- Desligado, sem chave ou com erro: nenhum botão aparece.
- A página do assistente aberta direto com `?explicar=`, com o assistente desligado, só mostra "indisponível".

**Pergunta fixa:**
- O link leva `?explicar=TEMA` e, nos cartões, o período do painel (`&periodo=`).
- A página transforma isso numa pergunta pronta, só para temas e períodos conhecidos. Qualquer outro valor abre a página vazia: nada digitado na URL chega ao modelo.

**Uma pergunta por clique:**
- A pergunta é feita uma única vez (`useEffectEvent` com guarda), inclusive com os efeitos duplicados do modo de desenvolvimento.
- Em seguida o tema sai da URL. Recarregar a página não gasta outra pergunta da cota.
- O componente é identificado só pela loja, para que limpar a URL não reinicie a conversa em andamento. Esse bug foi encontrado e corrigido nesta validação.

**Mesmo número e fonte (aceite):** a pergunta usa o mesmo período do cartão. As ferramentas do assistente reaproveitam os serviços do painel e do relatório (`AdvisorFoundationIT` confere os mesmos valores). A checagem de números da SC-027 impede que um número sem fonte chegue à tela. A resposta mostra a fonte, por exemplo "Indicadores do painel · Últimos 7 dias".

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `npm run lint`, `typecheck`, `build`, `test:smoke` | Aprovados. O smoke cobre `explainQuestion` (temas, períodos, alerta desconhecido e texto arbitrário recusados) e `explainHref` (período inválido fora do link), mais a rota com `?explicar=` atrás do controle de sessão |
| Navegador (backend sintético) | <ul><li>7 links com período e nomes acessíveis específicos</li><li>"Explicar receita" pergunta "Explique a receita da loja (últimos 7 dias)…", mostra a resposta com a fonte, leva o foco à resposta e limpa a URL</li><li>uma única pergunta por clique</li><li>recarregar não pergunta de novo</li><li>assistente desligado: sem botões e página "indisponível" sem pergunta</li></ul> |

## Limitações

- **Sem modelo real:** a frase final só será medida na SC-029. O aceite "cita o mesmo número" depende do modelo escolher a ferramenta certa, e a checagem de números garante apenas que nada sem fonte apareça.
- **Acessibilidade manual completa:** leitor de tela e zoom seguem pendentes.
