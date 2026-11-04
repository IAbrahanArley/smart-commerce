# Paginação das listas do admin — validação

Data: 2026-11-04. Pedido do responsável: as tabelas do admin sem paginação passam a paginar, com a paginação garantida no backend.

## Auditoria

| Lista | Antes | Depois |
| --- | --- | --- |
| Pedidos | Cursor por número, 20 por página | Sem mudança |
| Histórico de estoque | Cursor por sequência | Sem mudança |
| Produtos | Sem limite; busca, filtros e ordem no navegador | Banco filtra, ordena, conta e pagina (25 por página, por número) |
| Vendas na loja | Só as 50 últimas | Cursor `(created_at, id)`, 50 por página |
| Histórico do caixa | Só os 60 últimos | Cursor `(opened_at, id)`, 50 por página |
| Contas a pagar | Até 500 por mês; filtro de situação no Java | Filtro de situação no SQL; cursor `(due_date, created_at, id)`, 50 por página |
| Extrato da conta | Até 500 movimentos (“escolha um período menor”) | Cursor `(occurred_on, created_at, id)`, 50 por página; saldo após cada movimento continua calculado sobre todo o histórico |
| Marcas, categorias, contas, funcionários | Teto fixo por loja | Sem mudança (listas pequenas por regra) |

## Contrato

- `GET /api/tenants/{t}/products?q=&category=&visibility=&sort=&page=` → `{products, page, pageSize, matching, catalogTotal, publishedTotal}`. `category` aceita UUID ou `none`; `visibility` `all|visible|hidden`; `sort` `default|name|price-asc|price-desc`; `page` de 1 a 99999. A busca ignora maiúsculas e acentos (`sc_unaccent`) e procura no nome, SKU e SKUs das combinações; `%`, `_` e `\` são literais.
- `GET .../counter-sales?before=`, `GET .../cash/sessions?before=`, `GET .../finance/accounts/{id}/statement?before=` e `GET .../expenses?after=` devolvem `nextCursor` (nulo na última página). O cursor é o texto `[dia_]instante_uuid` da última linha da página anterior; formato inválido é 400. `cash/sessions` passou de lista para `{sessions, nextCursor}`; o extrato trocou `truncated` por `nextCursor`.
- Totais (a pagar, pago, vencidas, saldo inicial e final, contagens de produtos) continuam cobrindo todos os registros, não só a página.
- Tenant e papel: as mesmas verificações de antes (OWNER para produtos, caixa, contas a pagar e extrato; membro para vendas na loja). O cursor é só posição: usado em outra loja, lê a lista daquela loja.

## Frontend

- Componente local `ListPager` (`admin-web/src/components/list-pager.tsx`) sob cada lista: “Voltar aos mais recentes”/“Ver … mais antigos” nas listas por cursor; “Página anterior”/“Próxima página” e “Página X de Y” em produtos.
- Produtos: filtros e página ficam na URL (`?busca=&categoria=&vitrine=&ordem=&pagina=`); a busca espera 300 ms de pausa na digitação; mudar um filtro volta à página 1.
- Resumo da loja usa `catalogTotal`/`publishedTotal`; a busca global (Ctrl+K) procura produtos no backend a partir de 2 caracteres, em vez de baixar o catálogo inteiro.

## Evidências

- Backend: testes de integração novos em `CatalogIsolationIT` (filtros, acentos, curinga literal, ordem por preço, páginas, 400), `CounterSaleIT` (52 vendas com empate de instante na borda da página, sem repetir nem pular; cursor em outra loja; 400), `ExpenseIT` (52 contas no mesmo dia, totais sobre todas, situação no SQL, 400), `AccountIT` (52 ajustes no mesmo dia, saldo correto entre páginas, 400) e `CashIT` (formato novo, 400).
- Admin: `npm run lint`, `npm run typecheck`, `npm run build` e `npm run test:smoke` (helpers de filtros, `parseStatement` com `nextCursor`).
- Resultado do `./mvnw.cmd -B -ntp verify` registrado no PR desta mudança.
