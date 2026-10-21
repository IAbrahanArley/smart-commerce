# SC-017 — Modelo de ameaças dos pagamentos (Sprint 4)

Data: 2026-10-21. Escopo: conexão Mercado Pago por OAuth (SC-012), Checkout Pro (SC-013), webhook, confirmação e baixa de estoque (SC-014), conciliação (SC-015) e reembolso (SC-016), como estão no branch `feat/sc-017-review`, a partir de `4440138`, com as correções desta revisão. Base: [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md), [SECURITY.md](../agents/SECURITY.md) e as validações [SC-012](../quality/SC-012-VALIDATION.md) a [SC-016](../quality/SC-016-VALIDATION.md). Tudo em sandbox: produção exige aprovação separada (P10).

Revisão feita por um revisor independente só leitura (subagente) sobre o diff `95a1520~1..HEAD`, consolidada e verificada no código pelo coordenador. É análise estática apoiada nos testes automatizados; não houve teste de invasão nem chamada ao Mercado Pago real.

## Ativos, atores e fronteiras

**Ativos:**
- tokens OAuth de cada vendedor;
- segredo do aplicativo e do webhook;
- integridade do estado dos pedidos (pago ou reembolsado só com confirmação do provedor);
- estoque;
- dinheiro do comprador (cobrança, reembolso);
- dados pessoais.

**Atores:**
- visitante anônimo, que alcança o webhook e o início do pagamento;
- comprador com o token do próprio pedido;
- OWNER de outra loja;
- quem controla uma conta Mercado Pago ou captura tráfego;
- resposta adulterada ou inesperada do provedor;
- operador do servidor.

**Fronteiras:**
1. admin → backend: sessão, CSRF e vínculo OWNER;
2. vitrine → backend: cadeia pública, token do pedido em cabeçalho;
3. Mercado Pago → backend: webhook público, assinado por HMAC;
4. backend → Mercado Pago: OAuth, Orders API e reembolso, só para URLs configuradas;
5. backend → PostgreSQL: tenant sempre derivado do vínculo, do pedido ou do pagamento guardado.

## Ameaças, controles e evidências

| # | Ameaça | Controles | Evidência |
| --- | --- | --- | --- |
| P1 | **Confusão de tenant ou IDOR**: ler, pagar, confirmar ou reembolsar pedido de outra loja; usar o token de outra loja | OWNER conferido no SQL da conexão, do admin e do reembolso; retorno do OAuth com a loja do `state` guardado e ligado ao usuário; início do pagamento por slug, pedido e hash do token; webhook e conciliação com o tenant do `payment` guardado; todo SQL filtrado por `tenant_id`; um pagamento por pedido e uma order por provedor; `ACCOUNT_CHANGED` quando a loja troca de conta | `PaymentConnectionIT`, `CheckoutPaymentIT` (A/B), `PaymentConfirmationIT` (notificação de B lida com o token de B; conta trocada; reembolso por outro OWNER) |
| P2 | **Forja e replay do webhook** | HMAC-SHA256 em tempo constante sobre `data.id`, `x-request-id` e `ts`; `ts` com tolerância de 10 min; chave do evento só com valores assinados; corpo de até 16 KB, com parse estrito e id assinado obrigatório; o webhook nunca confirma nada sozinho, só a leitura da order no provedor | `WebhookSignatureTest`, `PaymentConfirmationIT` (assinaturas forjadas, antigas, de outro segredo, corpo trocado) |
| P3 | **Negação de serviço no webhook** | 429 depois de 300 falhas de assinatura em 10 s, sem nunca bloquear uma entrega assinada; corpo limitado | `WebhookRateLimitTest`; `PaymentConfirmationIT` (enxurrada não impede entrega válida) |
| P4 | **OAuth**: CSRF de login, vínculo à conta do atacante, reuso de `state`, redirecionamento aberto | `state` aleatório de 256 bits guardado só com hash, de uso único, válido por 10 min e ligado a usuário e loja; **consumido mesmo quando o vínculo foi perdido** (correção desta revisão); PKCE S256 com o verificador cifrado; destino fixo de retorno; renovação sob lock da linha; `invalid_grant` leva a `REVOKED` e apaga os tokens | `PaymentConnectionIT` (incluindo o caso novo de vínculo perdido) |
| P5 | **SSRF e destinos manipulados** | URLs externas só por configuração (HTTPS, ou HTTP só em loopback, sem credenciais, query ou fragmento); ids do provedor com padrão estrito antes de entrar no caminho; redirecionamentos nunca seguidos; `checkout_url` só em HTTPS `*.mercadopago.com(.xx)`, conferido no backend e na vitrine; o admin só navega para `auth.mercadopago.com/authorization` | `CheckoutPaymentIT` (respostas com outro domínio), smoke da vitrine e do admin |
| P6 | **Vazamento de token ou segredo** | AES-256-GCM por tenant e propósito; `toString` redigido em todos os records sensíveis; logs só com ids e tipos de erro; nenhuma resposta ou prop do Next com token; o token do comprador fica no servidor da vitrine | testes de captura de saída em `PaymentConnectionIT`, `CheckoutPaymentIT` e `PaymentConfirmationIT` |
| P7 | **Integridade do dinheiro**: cobrança, baixa ou reembolso em dobro; pago ou reembolsado sem confirmação | Chaves de idempotência fixas por pedido (checkout e reembolso); `UNIQUE` por pedido e por pagamento; transições condicionais sob o lock do pagamento e depois do pedido; movimentos `SALE` e `SALE_REVERSAL` com chave única por pedido e variante; pago só com `processed`/`accredited` e total, referência e conta conferidos; reembolso só por ação do OWNER e confirmado pela order lida; `cannot_refund_order` e `idempotency_key_already_used` agora são falha, não aceite (correção desta revisão) | `PaymentConfirmationIT` (corridas de webhook, conciliação e expiração; guardas do reembolso; caso novo `CANNOT`) |
| P8 | **Pagamento travado ou perdido**: a loja desconecta ou troca de conta antes da confirmação | A conciliação continua por 30 dias quando o erro é de conexão (`NOT_CONNECTED`, `REVOKED`, `ACCOUNT_CHANGED`), e a reconexão recoloca na fila os pagamentos pendentes da loja (correção desta revisão); a divergência aparece no admin; o diálogo de desconexão avisa | `PaymentConfirmationIT` (caso novo de conexão perdida) |
| P9 | **Dados pessoais** | O Mercado Pago recebe só títulos, preços, quantidades, frete e o UUID do pedido; nenhum dado do comprador; tabelas novas só com o id numérico do vendedor; a página de retorno ignora a query | `CheckoutPaymentIT` (corpo sem `payer`) |
| P10 | **Corridas e deadlocks** | Ordem de locks: pagamento, depois pedido, depois saldos por variante; chamadas ao provedor fora de transação, exceto a renovação de token (ver R10); rotinas com `SKIP LOCKED` | `PaymentConfirmationIT` (rotinas, webhook e expiração em paralelo) |

## Achados da revisão e disposição

| Achado | Sev. | Disposição |
| --- | --- | --- |
| Pagamento travado quando a conta é desconectada, revogada ou trocada | média | **corrigido:** horizonte de 30 dias para erros de conexão e fila de novo na reconexão |
| Reembolso registrado como aceito com `cannot_refund_order` ou `idempotency_key_already_used`, e consultado sem limite | média | **corrigido:** as duas respostas viram falha; consulta do reembolso limitada a 30 dias |
| `state` do OAuth reutilizável se o vínculo some no meio | baixa | **corrigido:** o `state` é consumido antes e o resultado é recusado sem desfazer o consumo |
| Desconectar não revoga a autorização no Mercado Pago | baixa | **mitigado:** o diálogo orienta revogar no painel; a documentação consultada não descreve endpoint de revogação (R11) |
| Busca do dono do webhook sem diferenciar maiúsculas, ficando com a primeira linha | baixa | **corrigido:** só encaminha quando há exatamente uma correspondência |
| Renovação de token com lock de linha durante a chamada HTTP | baixa | **risco aceito proposto (R10)** |
| Início do pagamento sem limite de taxa | baixa | **risco aceito proposto (R12)** |

## Riscos residuais (decisão humana)

- **R10, renovação sob lock.** `PaymentConnectionService.lockedCredentials` renova o token dentro da transação que trava a conta (até 20 s com o provedor lento). Nesse tempo, inícios de pagamento e confirmações daquela loja esperam e ocupam conexões do pool. A rotina diária renova com 30 dias de antecedência, então isso só ocorre se ela falhar repetidamente. Mover a renovação para fora do lock (com versão) pode ser feito antes de produção.
- **R11, revogação.** Ao desconectar, os tokens são apagados aqui, mas a autorização continua válida no Mercado Pago até o vendedor revogá-la no painel. Um token vazado antes continuaria funcionando por até 180 dias.
- **R12, limite no início do pagamento.** Exige o token de 256 bits do pedido, e só chama o provedor enquanto o pagamento está `CREATING`. Um limite por pedido e IP, como no checkout, pode ser acrescentado antes de produção.
- **R1 da SC-011** (retenção de estoque por vários e-mails) continua: o pagamento agora começa logo depois do pedido, mas a reserva ainda não depende dele.
- **Reembolso fora do sistema.** Um reembolso feito direto no painel do Mercado Pago não é detectado; o pedido continua `PAID`.
- **Motivo livre.** O motivo de cancelamento ou reembolso é texto livre do lojista (até 300 caracteres) e pode conter dados pessoais. Só o OWNER vê, e ele não é enviado ao provedor.
- **Validação real pendente.** As hipóteses H3 a H12 dependem do sandbox real ([SPRINT_04.md](../product/SPRINT_04.md), lembrete).
