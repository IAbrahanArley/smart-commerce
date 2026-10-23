# SC-024 — Alertas determinísticos: validação

Data: 2026-10-23. Base: [ADR-0016](../architecture/adr/0016-consultative-agent.md) (A6) e [SPRINT_06.md](../product/SPRINT_06.md).

## O que foi entregue

**Backend:**
- **Módulo novo `advisor`:**
  - `AlertService`: regras puras sobre serviços que já calculam os números e isolam o tenant, sem IA e sem estimativa;
  - `GET /api/tenants/{id}/alerts`: só OWNER, sem cache, e 404 único para loja ausente ou alheia.
- **Pedidos:** `OrderAdminService.expiringSoon` conta os pedidos pendentes cuja reserva termina em até 10 min. A conferência do OWNER fica no próprio serviço.

| Código | Gravidade | Regra | Leva para |
| --- | --- | --- | --- |
| `PAYMENTS_EXPIRED` / `PAYMENTS_REVOKED` | Crítico | conexão Mercado Pago vencida ou retirada | Pagamentos |
| `MARGIN_NEGATIVE` | Crítico | margem do mês **completa** e negativa (com o valor) | Financeiro |
| `PAYMENTS_NOT_CONNECTED` | Atenção | integração configurada e loja sem conta conectada | Pagamentos |
| `COST_MISSING` | Atenção | pedidos pagos no mês sem custo (margem parcial) | Financeiro |
| `ORDERS_EXPIRING` | Atenção | pendentes com reserva terminando em até 10 min | Pedidos pendentes |
| `LOW_STOCK` | Atenção | disponível ≤ 5 (limite do F8, já descontadas as reservas) | Produtos |
| `FEES_UNAVAILABLE` | Informativo | vendas do mês ainda sem a taxa do Mercado Pago | Financeiro |
| `WITHOUT_SALES` | Informativo | com estoque e sem venda em 30 dias | Produtos |

**Regras de leitura:**
- Margem parcial nunca vira "margem negativa". O alerta só aparece quando todos os componentes do mês são conhecidos.
- Sem as credenciais da aplicação Mercado Pago no ambiente, nenhum alerta de pagamento aparece.
- A resposta traz só código, gravidade, contagem ou valor e destino. Nunca leva dados de comprador, SKU, custo ou nome de produto.

**Admin:**
- **Cartão "Alertas" na visão geral da loja:** mostra a lista completa, do mais grave ao menos grave, com a frase de cada alerta e um link para a tela que resolve. A gravidade aparece escrita ("Crítico", "Atenção", "Informativo"), não só pela cor, que usa apenas os tokens existentes.
- **Códigos desconhecidos:** são descartados. Um alerta que esta versão do admin não sabe redigir não aparece.

**Correção encontrada no caminho:** `formatBRL` recusava valores negativos e mostrava "Valor indisponível". Isso afetava a margem negativa na página de financeiro e no dashboard (SC-021, SC-022), além do novo alerta. Agora os valores negativos aparecem como "-R$ 25,00", e "-0.00" aparece como "R$ 0,00".

## Testes

| Teste | Cobertura |
| --- | --- |
| `AlertIT` (PostgreSQL via Testcontainers) | <ul><li>**loja vazia:** só "conecte o Mercado Pago"</li><li>**limites:** estoque 5 alerta e 6 não; reserva a 9 min conta, a 11 min não conta, e vencida não conta</li><li>conexão vencida</li><li>pedido sem custo e venda sem taxa</li><li>ordem por gravidade</li><li>**causa resolvida:** os alertas somem</li><li>**margem:** negativa só com o mês completo (com a taxa conhecida), e com o valor certo (-25,00)</li><li>conexão revogada</li><li>**isolamento:** A/B (404 para o dono de outra loja, id inválido) e 401 sem sessão</li><li>**privacidade:** a resposta sem e-mail, nome e telefone do comprador, nem SKU ou nome de produto</li></ul> |
| `ArchitectureTest`, `BootstrapSecurityTest` | camadas e cadeia de segurança com a rota nova |
| Smoke do admin | <ul><li>códigos, gravidades e destinos desconhecidos descartados</li><li>frases no singular e no plural</li><li>links por destino</li><li>`formatBRL` com valores negativos, zero negativo e milhar</li></ul> |

**Comandos executados (2026-10-23):**
- **Backend:** `mvnw verify` completo, com 86 testes unitários e 146 de integração, todos passando, incluindo os 4 do `AlertIT`.
- **Admin:** `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

**Verificação no navegador**, com uma API sintética local: o cartão "Alertas" mostra a ordem por gravidade, a margem negativa como "-R$ 25,00", os links certos e o código desconhecido descartado.
