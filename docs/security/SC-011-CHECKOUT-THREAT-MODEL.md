# SC-011 — Modelo de ameaças do fluxo de compra (Sprint 3)

Data: 2026-10-19. Escopo: vitrine pública, carrinho, cotação, checkout `PENDING_PAYMENT`, acompanhamento do comprador, pedidos no admin e rotinas de expiração e anonimização, como estão na `main` em `5c1acd3` mais as mudanças da SC-011. Base: [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md), [SECURITY.md](../agents/SECURITY.md) e as validações [SC-005](../quality/SC-005-VALIDATION.md) a [SC-010](../quality/SC-010-VALIDATION.md). Não há pagamento: o checkout só existe por configuração em DEV (D6).

## Ativos, atores e fronteiras

**Ativos:**
- estoque disponível (físico − reservas ativas);
- integridade de pedidos, valores e snapshots;
- dados pessoais do comprador (D2);
- token de acompanhamento do pedido;
- sessão e CSRF do lojista;
- segredo de assinatura do IP.

**Atores:**
- visitante anônimo;
- comprador com o token do próprio pedido;
- lojista OWNER de uma loja;
- OWNER de outra loja;
- atacante com cookie ou cabeçalho adulterado;
- operador do servidor.

**Fronteiras:**
1. navegador → servidor Next da vitrine (server actions, cookies HttpOnly);
2. vitrine → backend pela cadeia pública, sem sessão e com cabeçalhos em lista explícita;
3. admin-web → backend pela cadeia autenticada, com sessão, CSRF e verificação de origem;
4. backend → PostgreSQL, com o tenant sempre derivado da loja publicada ou do vínculo OWNER.

## Ameaças, controles e evidências

| # | Ameaça | Controles | Evidência |
| --- | --- | --- | --- |
| T1 | **Retenção abusiva de estoque**: visitante cria pedidos só para reservar unidades | Reserva de 30 min (D1); até 5 pendentes por e-mail e loja e 20 tentativas por IP por hora (D4); 20 linhas × 10 unidades por pedido; IP só conta assinado e atrás de proxy declarado; **IPv6 agrupado por /64 e IPv4 mapeado contado como IPv4** (SC-011, `ClientIpBucket`); cancelamento pelo lojista (SC-010) e expiração automática | `OrderCheckoutIT` (limites, concorrência, IPv6 de um /64 compartilha o limite); `ClientIpBucketTest`; `OrderLifecycleIT` (cancelamento libera, rotina) |
| T2 | **Enumeração de pedidos**: descobrir ou ler pedidos alheios | IDs UUID aleatórios; leitura do comprador exige token de 256 bits, guardado só como SHA-256 e comparado em tempo constante; 404 uniforme para outra loja, token errado ou inexistente; admin exige vínculo OWNER na própria query | `OrderCheckoutIT.buyerReadsRequireTheTokenAndTheOrdersOwnStore`; `OrderLifecycleIT.ownersNeverSeeOrChangeAnotherTenantsOrders` |
| T3 | **Adulteração de cookie**: carrinho com preço, loja ou quantidade falsos; token de outro pedido | Carrinho só leva variante e quantidade, saneado (JSON estrito, UUID, 1–10, até 20 linhas, até 4 KiB) e sempre recotado no backend; cookie por loja (`Path`); cookie do pedido HttpOnly, `Path` do próprio pedido e token validado no backend | smoke da vitrine (cookies adulterados, outra loja, `Path`); `CartQuoteIT` |
| T4 | **Inferência de saldo exato** | Vitrine mostra só disponível/indisponível; a cotação responde só `AVAILABLE`/`INSUFFICIENT`/`UNAVAILABLE`, sem quantidade | `StorefrontIsolationIT`, `CartQuoteIT` (projeção pública) |
| T5 | **IDOR e CSRF no admin** | Tenant da rota só vale com vínculo OWNER e identidade ativa; cancelamento por POST com CSRF e origem; lista explícita de rotas com `denyAll` no final; IDs de rota do admin precisam ser UUID antes de montar caminhos da API (SC-011); checkout por server action recusa origem externa | `OrderLifecycleIT` (403 sem CSRF, 401 anônimo, 404 sem vínculo); smoke do admin (ID inválido); smoke da vitrine (post real de outra origem não cria pedido nem cookie) |
| T6 | **Falsificação do IP do limite** | `X-SC-Client-IP` só aceito com HMAC do segredo compartilhado e janela de 60 s; vitrine só confia em `X-Forwarded-For` atrás de proxy declarado; backend não sobe fora do `dev` com checkout ligado e sem segredo | `OrderDomainTest`; `OrderCheckoutIT`; smoke (IP forjado não repassado); `CheckoutSafetyCheckTest` |
| T7 | **Duplicação e replay** | `requestId` com impressão digital; replay devolve o mesmo pedido sem token novo; `REQUEST_CONFLICT` não gera chave nova na vitrine | `OrderCheckoutIT`; smoke (resposta perdida, conflito) |
| T8 | **Vazamento de dados pessoais** | Respostas `no-store`; erros genéricos; logs sem e-mail, telefone, IP ou token; anonimização em 90 dias, incluindo a impressão digital e o motivo de cancelamento livre (SC-011); a rotina diária esvazia a fila (até 10 minutos por execução) | `OrderCheckoutIT` (saída capturada); `OrderLifecycleIT` (anonimização, motivo substituído); `OrderMaintenanceJobTest` |
| T9 | **Corridas de estado** (cancelamento × expiração × checkout) | Ordem de travas pedido → estoque; trocas de estado condicionais; `SKIP LOCKED` nas rotinas; expiração sem travar estoque | `OrderLifecycleIT` (corridas, quatro rotinas em paralelo) |
| T10 | **Integridade referencial entre tenants** | FKs compostas por tenant em loja, pedido, item, variante e reserva; FK reserva → pedido validada na V16 quando o banco não tem reservas órfãs | `SalesOrderMigrationIT` (V16), `OrderLifecycleIT` (CHECKs) |

## Riscos residuais (decisão humana)

- **R1, retenção de estoque por vários e-mails** (T1). Com e-mails distintos (o e-mail não é verificado e só é normalizado para minúsculas), um IP ou um /64 faz até 20 tentativas por hora, cada uma com até 200 unidades reservadas por 30 minutos, ou seja, cerca de 4.000 unidades retidas. Atrás de proxy não declarado, todos os visitantes compartilham o limite por IP do servidor da vitrine: um único cliente pode bloquear o checkout de todas as lojas. Antes de produção, é obrigatório um proxy confiável que sobrescreva `X-Forwarded-For`, mais `STOREFRONT_TRUST_FORWARDED_FOR=true` e o segredo nas duas aplicações; o backend não tem como verificar a configuração da vitrine. Mitigação atual: expiração, cancelamento pelo lojista e checkout só em DEV. Antes de produção, a Sprint 4 deve exigir o início do pagamento para manter a reserva, ou adotar desafio anti-robô e limite por loja, com aprovação.
- **R2, inferência de saldo até 10 unidades** (T4). A cotação aceita de 1 a 10 unidades por linha e não tem limite de chamadas: perguntando quantidades crescentes, dá para descobrir o saldo exato quando ele é 10 ou menos (acima disso, só que há 10 ou mais). Isso decorre do D5, que dá uma cotação honesta por linha, e um concorrente pode acompanhar as vendas da loja. O ADR-0012 só aceita esse risco "mediante aprovação", que ainda não foi registrada, e não menciona a cotação. **Pendente de decisão humana**: aceitar ou limitar a taxa da cotação.
- **R6, volume de pedidos.** O número do pedido é sequencial por loja e aparece para o comprador, então revela o volume de pedidos da loja. É uma decisão de produto do ADR; **pendente de confirmação** do aceite.
- **R3, várias instâncias**: o limite por IP vira 20 × N tentativas e as rotinas rodam em todas as instâncias. Isso é seguro por causa do `SKIP LOCKED`. Já registrado no ADR-0012.
- **R4, SEC-003** segue aberta: são as 5 entradas altas só na cadeia de lint de cada frontend, sem correção publicada (`braces` ≤ 3.0.3); a produção está limpa. Ver [SEC-003-FOLLOW-UP.md](SEC-003-FOLLOW-UP.md).
- **R5, jurídico**: política de privacidade completa e validação jurídica do D2 antes de produção.
