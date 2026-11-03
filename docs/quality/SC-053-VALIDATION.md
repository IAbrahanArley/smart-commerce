# SC-053 — Venda presencial

Data: 2026-11-03. Quinta tarefa da Sprint 7 ([SPRINT_07.md](../product/SPRINT_07.md)). As decisões C6–C8 e a implementação estão no [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md).

**Decisões do responsável (2026-11-03):**
- o desconto do funcionário fica limitado a um percentual definido pelo dono;
- cartão fica a receber, com a data prevista pelo prazo configurado;
- o Pix entra numa conta padrão escolhida pelo dono.

## Critérios de aceite

- A venda na loja e a vitrine disputam o mesmo estoque, sem vender o que não existe e respeitando as reservas da vitrine.
- O relatório do período soma as duas.
- A margem marca a taxa "informada pelo lojista".
- O funcionário registra; só o dono cancela e configura.
- Quem é de outra loja não vê nem altera nada.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `CounterSaleIT` (Postgres real) | 2 aprovados.<br>**Configurações:** só o dono altera; o funcionário recebe 404; Pix numa gaveta dá 409.<br>**Busca:** encontra pelo nome ou pelo SKU e mostra o disponível, sem custo; menos de 2 caracteres dá 400.<br>**Dinheiro:** 45,00 com 10% de desconto (o limite) entra na gaveta da sessão aberta; 10,01% dá 409. O estoque cai, o livro grava venda liquidada e CMV incompleto (item sem custo), e repetir o pedido devolve a mesma venda.<br>**Pix:** entra na conta bancária.<br>**Débito:** fica a receber, com taxa de 0,20 e previsão para o dia seguinte. A taxa não volta para o funcionário, só para o dono.<br>**Crédito sem taxa:** aparece como "taxa indisponível".<br>**Relatório:** receita de 70,50, taxas informadas de 0,20 e "caixa do período" de 50,50 (só dinheiro e Pix; o custo não movimenta dinheiro).<br>**Estoque:** produto esgotado e o último item reservado pela vitrine dão 409, e nada é gravado.<br>**Cancelamento:** o funcionário recebe 404. O dono cancela com devolução ao estoque: a gaveta e o estoque voltam, e o livro zera.<br>**Outra loja:** 404.<br>**Concorrência:** duas vendas simultâneas do último item vendem uma vez só.<br>**Validação:** forma desconhecida, dinheiro sem caixa, item repetido e quantidade zero dão 400 |
| `StaffIT` (matriz de papéis) | aprovado. Só `GET /counter-sales` foi liberado; o restante das rotas recusa o funcionário ou exige corpo válido |
| `InventoryReservationIT`, `CashIT`, `FinanceReportIT` | aprovados |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração), depois das correções da revisão | 121 unitários e 221 de integração. A única falha foi a asserção que ainda esperava o CMV liquidado. Ela foi ajustada à correção, e o `CounterSaleIT` passou de novo (formatação incluída) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O novo `smoke-counter.mjs` cobre subtotal, desconto, limite do funcionário igual ao do backend, percentuais e prazos |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório):
- **Menu do funcionário:** "Visão geral", "Vendas na loja" e "Caixa".
- **Busca:** "camis" mostra as duas camisetas com opções, SKU, preço e disponível; a que está sem estoque não pode ser adicionada.
- **Desconto:** com 2 unidades (R$ 119,80), um desconto de R$ 15,00 mostra o erro do limite de 10% e põe o foco no campo. Com R$ 10,00, a venda é registrada.
- **Lista:** a venda aparece na lista, e o funcionário não vê "Cancelar".
- **Pagamento:** escolher "Cartão de débito" mostra que a venda fica a receber.

## Achados e disposição

| Achado | Disposição |
| --- | --- |
| O tipo `COUNTER_SALE_REVERSAL` não cabia na coluna `inventory_movement.type` (16 caracteres): o cancelamento dava 503 | **Corrigido antes da entrega:** passou a se chamar `COUNTER_RETURN`; o teste de cancelamento cobre |
| A venda à vista sem pedido online contava como "taxa indisponível" e deixava a margem parcial | **Corrigido:** só cartão sem taxa configurada conta |
| Revisão (média): o CMV da venda à vista era liquidado e reduzia o "caixa do período" | **Corrigido:** custo não liquida (como nos pedidos online); testado (50,50) |
| Revisão (média): uma gaveta podia ser a conta do Pix e geraria falta falsa no fechamento às cegas | **Corrigido:** só conta bancária, no backend e na tela; testado |
| Revisão (baixa): custo total enorme daria 503 | **Corrigido:** limitado como o subtotal |
| Revisão (baixa): o funcionário via a taxa do cartão | **Corrigido:** a taxa é dado de margem e só vai para o dono; testado |
| Revisão (baixa): cancelar uma venda em dinheiro com o caixa fechado tira o valor da gaveta fora de sessão | **Aceito e registrado:** o dinheiro sai da gaveta de fato. A próxima abertura conta o que sobrou, e o histórico mostra o cancelamento |

## Limitações

- A taxa do cartão não é estornada no cancelamento.
- O recebimento do cartão (baixa na conta) chega com contas a receber (SC-055).
- O livro de vendas (SC-056) vai concentrar a lista e o registro; por enquanto eles ficam na página "Vendas na loja".
- Leitor de tela, Axe e Lighthouse não foram executados, como nas revisões anteriores.
