# ADR-0012 — Reserva de estoque, carrinho e pedidos pendentes

Status: **adotado** em 2026-10-17 para D1–D7, conforme as recomendações da seção 8, aprovadas pelo responsável. Proposto na mesma data; o plano incremental está em [SPRINT_03.md](../../product/SPRINT_03.md). **Implementado na Sprint 3 (SC-005 a SC-010) e revisado de forma integrada na SC-011** em 2026-10-19 ([modelo de ameaças](../../security/SC-011-CHECKOUT-THREAT-MODEL.md), [validação](../../quality/SC-011-VALIDATION.md)). Os riscos residuais R2 (inferência pela cotação) e R6 (números sequenciais) foram **aceitos** em 2026-10-20 (ver "Riscos residuais R2 e R6 da SC-011").

## Aprovação

Em 2026-10-17 o responsável aprovou: D1 reserva de 30 minutos, valor único da plataforma; D2 dados mínimos do comprador (nome, e-mail, telefone, endereço só para frete fixo, sem CPF), snapshot no pedido e anonimização de pedidos não pagos após 90 dias, com validação jurídica obrigatória antes de produção; D3 retirada e frete fixo único por loja, sem CEP; D4 limites de quantidade e limites atômicos no PostgreSQL por IP/e-mail, sem CAPTCHA; D5 carrinho em cookie da vitrine sem estado no servidor e cotação sem reserva; D6 checkout habilitado somente por configuração em DEV/local até a Sprint 4; D7 ajuste negativo abaixo do reservado bloqueado com `409 RESERVED_STOCK`. Também autorizou iniciar SC-005, SC-006 e SC-007. SC-008 a SC-011 dependem dessas entregas e de autorização própria. Hipóteses jurídicas continuam pendentes de validação antes de produção. A aprovação não autoriza dependências novas, serviços pagos, integrações reais, deploy ou push.

### Decisões complementares para SC-008 e SC-009 (2026-10-18)

O responsável autorizou a SC-008 e aprovou:

- **D4, IP confiável:** o servidor do storefront-web envia o IP do visitante em `X-SC-Client-IP` com `X-SC-Client-IP-Timestamp` e `X-SC-Client-IP-Signature` = HMAC-SHA256 de um segredo compartilhado (variável de ambiente nas duas aplicações, sem valor versionado, mínimo de 32 caracteres), aceito por até 60 segundos. Sem segredo configurado ou com assinatura inválida, o backend usa o endereço da conexão. Os contadores guardam somente HMAC do IP com chave efêmera gerada na inicialização, sem IP em claro; reiniciar o backend zera as janelas.
- **D4, valores:** até 20 linhas e 1–10 unidades por linha; até 5 pedidos pendentes não expirados por e-mail por loja; até 20 tentativas de checkout por IP por hora.
- **D2, aviso de privacidade do checkout (SC-009):** "Seus dados (nome, e-mail, telefone e, se escolher entrega, o endereço) são usados apenas pela {nome da loja} para processar e entregar este pedido e para falar com você sobre ele. Eles não são compartilhados com outras lojas. Se o pedido não for pago, seus dados pessoais são anonimizados em até 90 dias. Pagamento ainda não está disponível nesta etapa." A política completa da loja e a validação jurídica continuam pendentes antes de produção.

### Ajustes da revisão do PR #8 (2026-10-18)

Aplicação das regras acima, sem mudar valores aprovados:

- **Ordem do checkout:** a admissão por IP roda e confirma numa transação própria **antes** da transação do pedido, e não aninhada nela. Cada checkout segura no máximo uma conexão por vez, para que checkouts simultâneos não esgotem o pool esperando uma segunda.
- **Retentativas não gastam tentativas:** quando já existe pedido para o `requestId`, o backend responde replay (mesma impressão digital) ou `REQUEST_CONFLICT` antes de cobrar a admissão. Nada é gravado e nenhum dado é revelado sem a impressão digital idêntica. A transação do pedido repete essa checagem sob o lock da chave.
- **`REQUEST_CONFLICT` prova que o pedido existe:** clientes não devem gerar nova chave automaticamente nesse caso, porque isso duplicaria o pedido. Um novo pedido só nasce de ação explícita do comprador.
- **Segredo obrigatório fora de DEV:** o backend não inicia com checkout habilitado, segredo de IP vazio e perfil diferente de `dev`. Sem o segredo, todos os compradores de todas as lojas cairiam num único balde por IP.
- **Várias instâncias:** os contadores por IP usam chave efêmera por processo. Com N instâncias do backend, o teto efetivo é 20 × N tentativas por hora. Aceito até a implantação com múltiplas instâncias, quando a chave passa a vir de configuração compartilhada.
- **FK `inventory_reservation → sales_order` NOT VALID:** vale para toda linha nova ou alterada. A validação (`VALIDATE CONSTRAINT`) fica pendente para migração própria antes da primeira implantação fora de DEV, depois de confirmar que não há reservas órfãs.
- **Loja do item:** a FK composta garante o mesmo tenant entre item, pedido e variante. Que a variante pertence à loja do pedido é garantido pelo serviço, que revalida cada variante pelo slug, e não por constraint. Aceito como está; o reforço no banco fica para quando houver variantes compartilhadas ou escrita fora do checkout.

### Ajustes da SC-011 (2026-10-19)

Aplicação das regras aprovadas, sem mudar valores:

- **D4:** o limite por IP agrupa IPv6 por /64 e conta IPv4 mapeado como IPv4, para que uma única máquina não troque de endereço dentro do próprio bloco.
- **D2:** a anonimização também substitui o motivo de cancelamento escrito pela loja, que pode citar o comprador. A rotina diária processa a fila até esvaziar, com limite de 10 minutos por execução, e o backend avisa na inicialização se o checkout estiver ligado com as rotinas desligadas.
- **FK reserva → pedido:** validada pela V16 quando o banco não tem reservas sem pedido. Em bancos locais antigos que ainda tenham essas reservas, ela fica `NOT VALID` com um aviso, sem apagar dados.

### D6 revisado para produção (bloco 2b do MVP, 2026-10-26)

Aprovado pelo responsável: fora de `dev` e do ambiente de teste hospedado, o checkout **só liga com pagamento real**. O backend recusa iniciar com `orders.checkout.enabled=true` sem:
- a integração do Mercado Pago configurada;
- o segredo do webhook;
- `MERCADO_PAGO_TEST_TOKEN=false`.

Também continua exigindo o segredo do IP assinado. A vitrine só mostra o checkout em build de produção com `STOREFRONT_CHECKOUT_ENABLED=true` e `DEPLOYMENT_ENVIRONMENT=production` (ou `hosted-test`). Uma loja sem conta conectada continua recusando pedidos novos (`PAYMENT_UNAVAILABLE`). Checkout sem pagamento em produção continua proibido.

### Exceção ao D6 no ambiente de teste hospedado (2026-10-19)

O [ADR-0014](0014-hosted-test-environment.md) (H2) permite checkout sem pagamento **somente** no ambiente de teste hospedado (`DEPLOYMENT_ENVIRONMENT=hosted-test`), com dados sintéticos e aviso permanente. O backend recusa iniciar com checkout fora de `dev` sem esse valor. A produção comercial continua proibida de aceitar pedido sem pagamento.

### Decisões da SC-010 (2026-10-18)

O responsável autorizou a SC-010 e aprovou:

- **Rotina de expiração:** roda no próprio backend a cada 1 minuto, em lotes de 100 com `FOR UPDATE SKIP LOCKED`. Marca como `EXPIRED` os pedidos `PENDING_PAYMENT` vencidos e libera as reservas deles. A troca de estado é condicional (só sai de `PENDING_PAYMENT`), para que várias instâncias nunca liberem duas vezes. O disponível já não depende da rotina: reserva vencida deixa de contar pelo relógio (SC-005).
- **Anonimização (D2):** 90 dias após a criação, pedidos `EXPIRED` ou `CANCELLED` perdem nome, e-mail, chave de e-mail, telefone e endereço completo. A impressão digital do `requestId` também é substituída, porque é derivada desses dados. Ficam número, itens, valores, tipo e texto da entrega e datas, com `anonymized_at` registrado. Uma rotina diária faz isso, em lotes com `SKIP LOCKED`. A operação é irreversível.
- **Cancelamento pelo lojista:** somente o OWNER da loja, e só de pedido `PENDING_PAYMENT` ainda no prazo. Motivo obrigatório, de 3 a 300 caracteres numa linha, visível só no admin. Registra quem cancelou e quando, e libera as reservas na mesma transação. Repetir o cancelamento de um pedido cancelado devolve o mesmo resultado, sem novo efeito. Pedido expirado não pode ser cancelado (`409 ORDER_EXPIRED`). O comprador vê apenas "Pedido cancelado".
- **Admin:** item "Pedidos" no menu da loja. Lista por cursor (número decrescente, 20 por página) com filtro por situação efetiva, em que pendente vencido conta como expirado. O detalhe mostra itens com SKU e custo do snapshot, entrega, valores e dados do comprador, exceto quando anonimizados. Horários em America/Sao_Paulo. Pedido de outra loja, ou sem vínculo OWNER, responde 404 uniforme.

## Contexto

- [BUSINESS_RULES](../../product/BUSINESS_RULES.md): pedido nasce `PENDING_PAYMENT`, estoque é reservado com expiração, pagamento confirmado gera baixa, cancelamento/expiração libera reserva; disponível = físico − reservado, nunca negativo; snapshot de preço/custo/frete/desconto; dados do comprador não são compartilhados entre lojas. O tempo de reserva deve ser definido antes da implementação.
- [ADR-0009](0009-basic-inventory.md) e [ADR-0011](0011-catalog-media-description-variants.md): `inventory_balance` guarda somente o saldo físico inteiro por unidade vendável (`product_id` referencia `catalog_variant(id, tenant_id)`); ledger imutável com `ENTRY`/`ADJUSTMENT`, `actor_id` obrigatório, `requestId` único por tenant e repetição idempotente. Não há coluna nem tabela de reserva.
- [ADR-0010](0010-public-storefront.md): a cadeia pública só permite GET, é stateless e não usa cookies administrativos; storefront-web chama a API pública a partir do servidor, sem encaminhar credenciais. Disponibilidade pública é hoje `saldo físico > 0`, e o ADR exige que o checkout valide preço/disponibilidade e implemente reservas no backend.
- [ADR-0002](0002-tenant-isolation.md): tenant derivado de contexto confiável; testes negativos A/B obrigatórios. [ADR-0003](0003-payment-ownership.md): pagamentos vão para a conta Mercado Pago do lojista, assunto da Sprint 4.
- Comprador não tem identidade no sistema. PRD exclui integração de frete nacional; inclui "frete fixo ou retirada".

## Decisão proposta (resumo)

1. **Carrinho sem reserva e sem estado no servidor**: itens (variante + quantidade) ficam num cookie da vitrine, por loja; o backend apenas cota o carrinho, sem efeito colateral.
2. **Reserva somente na criação do pedido**: `POST` público de checkout cria, na mesma transação, o pedido `PENDING_PAYMENT`, os itens com snapshot e uma reserva por variante com `expires_at`.
3. **Disponibilidade calculada** como `on_hand − Σ reservas ACTIVE não expiradas`, sob lock do saldo; a expiração vale pela própria consulta e uma rotina agendada só materializa o estado (`EXPIRED`) e libera auditavelmente.
4. **Frete por regra fixa da loja ou retirada**, com snapshot no pedido.
5. **Idempotência** por `requestId` do checkout, com impressão digital do payload, no mesmo padrão do ADR-0009.
6. **Checkout habilitado somente em DEV/local até a Sprint 4** (pedido sem meio de pagamento não pode ser oferecido a compradores reais).

## 1. Reserva de estoque por variante

**Modelo.** Nova tabela `inventory_reservation` no módulo inventory: `id`, `tenant_id`, `variant_id`, `order_id`, `quantity > 0`, `status` (`ACTIVE`, `CONSUMED`, `RELEASED`, `EXPIRED`), `expires_at`, `created_at`, `closed_at`. FKs compostas `(variant_id, tenant_id) → catalog_variant` e `(order_id, tenant_id) → order`; uma reserva por `(order_id, variant_id)`; índice parcial `(tenant_id, variant_id) WHERE status = 'ACTIVE'`. Reserva **não** grava `inventory_movement`, pois o saldo físico não muda; o ledger continua registrando apenas fatos físicos. O consumo na Sprint 4 gerará movimento de saída.

**Cálculo.** Ativa = `status = 'ACTIVE' AND expires_at > agora`. Disponível = `on_hand − Σ ativas`, nunca negativo. Variante sem `inventory_balance` tem disponível zero.

**Alternativas de representação:**

| Opção | Prós | Contras |
| --- | --- | --- |
| A. Tabela de reservas + soma na consulta (**recomendada**) | Fonte única; expiração correta mesmo se a rotina atrasar; auditável por pedido; sem contador para divergir | Soma por variante a cada leitura/escrita; exige índice parcial e locks bem ordenados |
| B. Coluna `reserved` em `inventory_balance` + tabela | Leitura O(1) | Contador só é liberado quando a rotina roda; reservas expiradas continuam bloqueando estoque; risco de divergência contador × linhas |
| C. Reserva como movimento no ledger | Um único histórico | Mistura fato físico com intenção; ledger imutável cresce com ruído; quebra o significado de `balance_after` |

O volume esperado (pequenos varejistas, poucas reservas ativas por variante) torna a soma barata; reavaliar se métricas mostrarem contenção.

**Concorrência.** Criação de pedido: advisory lock por tenant/requestId (como ADR-0009) → `SELECT … FOR UPDATE` dos `inventory_balance` das variantes **em ordem de `variant_id`** (evita deadlock entre carrinhos com os mesmos itens) → recalcula disponível → grava pedido, itens e reservas. Ajuste negativo do lojista (ADR-0009) passa a validar contra o disponível sob o mesmo lock (ver D7). Liberação/expiração e consumo usam o mesmo lock de saldo.

**Relógio.** O prazo é calculado pelo backend com `Clock` UTC injetável (testável), gravado como `TIMESTAMPTZ`. O cliente nunca informa `expires_at`.

**Expiração.** Expiração lógica imediata pela condição `expires_at > agora`; rotina `@Scheduled` em lotes com `FOR UPDATE SKIP LOCKED` marca reservas `EXPIRED` e o pedido `EXPIRED`, de forma idempotente. A correção não depende da rotina; a rotina garante estado legível e libera o pedido para relatórios. Uma única instância no MVP; `SKIP LOCKED` já tolera duas. Sem fila externa, sem novo serviço.

### D1 — Prazo da reserva (requer aprovação)

| Opção | Trade-offs |
| --- | --- |
| 15 min | Menor bloqueio de estoque e menor impacto de abuso; pode expirar antes de Pix/cartão com 3DS ou de quem troca de dispositivo para pagar |
| **30 min (recomendada)** | Equilíbrio para cartão e Pix; janela de abuso ainda curta |
| 60 min | Mais folga para o comprador; dobra o tempo em que bots ou desistentes travam estoque escasso |
| 24–72 h | Necessária se boleto ou pagamento em dinheiro forem aceitos; estoque escasso fica travado por dias; aumenta pagamentos "tardios" a tratar |

**Recomendação:** 30 minutos, valor único da plataforma (configuração do backend, não do lojista) no MVP; prazo por loja fica para depois de dados reais. Consequência para a Sprint 4: aceitar apenas meios que confirmem dentro do prazo (cartão e Pix com expiração menor que o prazo restante da reserva) e excluir boleto/lotérica do MVP — **a confirmar na documentação oficial atual do Mercado Pago** antes de implementar. Pagamento aprovado após expiração é tratado na Sprint 4 (re-reservar se houver estoque, senão sinalizar para decisão humana de reembolso); nenhum reembolso automático.

## 2. Carrinho

| Opção | Prós | Contras |
| --- | --- | --- |
| **A. Cookie da vitrine, sem estado no servidor (recomendada)** | Zero dado pessoal e zero crescimento de banco por visitantes anônimos; nenhum vetor de "estocar carrinhos"; SSR consegue renderizar o carrinho; dispensa nova sessão pública | Não sincroniza entre dispositivos; sem dados de carrinho abandonado (fora do MVP) |
| B. Carrinho anônimo no PostgreSQL com token opaco | Persistência entre abas, base para carrinho abandonado | Nova tabela e limpeza; token público é credencial a proteger; abuso de escrita sem rate limiting |
| C. Carrinho identificado (conta do comprador) | Histórico e reuso de dados | Exige identidade de comprador, fora do MVP; amplia LGPD |
| D. Reservar ao adicionar ao carrinho | Garantia de estoque cedo | Bloqueio por visitantes anônimos, inclusive bots; contraria a regra "pedido nasce PENDING_PAYMENT com reserva" |

**Proposta A:** cookie `HttpOnly`, `SameSite=Lax`, `Secure` fora de DEV, escopado ao host da vitrine e ao caminho `/lojas/{slug}`, contendo apenas `[{variantId, quantity}]` (limite proposto: 20 linhas, 1–10 unidades por linha, tamanho < 4 KiB). Não é assinado porque **não é confiável**: todo uso passa pela cotação/checkout no backend. Mutações do carrinho acontecem em route handlers/server actions do storefront-web (mesma origem, verificação de origem do Next); o browser continua sem chamar a origem do backend diretamente, preservando o ADR-0010.

**Cotação:** `POST /api/public/stores/{slug}/cart/quote` recebe as linhas e devolve, por linha, nome, opções, preço unitário, subtotal e estado (`AVAILABLE`, `INSUFFICIENT`, `UNAVAILABLE`), além de subtotal geral e opções de entrega com valores. Sem reservas, sem gravação. Não expõe quantidade exata, custo, SKU ou IDs internos além dos já públicos. Itens de outra loja, despublicados ou inexistentes aparecem como `UNAVAILABLE` sem distinguir causa.

## 3. Pedido `PENDING_PAYMENT` com snapshot

Novo módulo `orders` (camadas do ADR-0008). Tabelas propostas:

- `sales_order`: `id`, `tenant_id`, `store_id`, `number` (sequencial por loja, legível), `status`, `currency = 'BRL'`, `subtotal_amount`, `shipping_amount`, `discount_amount = 0`, `total_amount`, `fulfillment_type` (`PICKUP` | `FIXED_SHIPPING`), snapshot da regra de entrega (valor e textos), dados do comprador (D2), `reservation_expires_at`, `request_id`, `request_fingerprint`, `access_token_hash`, `created_at`, `updated_at`, `version`. Unicidades `(tenant_id, request_id)`, `(store_id, number)`; FK composta `(store_id, tenant_id)`.
- `sales_order_item`: `id`, `tenant_id`, `order_id`, `variant_id`, `product_id`, `product_name_snapshot`, `variant_options_snapshot`, `sku_snapshot`, `unit_price_snapshot`, `unit_cost_snapshot` (nulo quando custo ausente → margem indisponível, nunca presumida), `quantity`, `line_total`. FKs compostas por tenant.

Dinheiro em `NUMERIC` com os mesmos CHECKs de escala/faixa do catálogo e `BigDecimal` no Java; preço × quantidade inteira é exato, sem arredondamento. Totais sempre calculados no backend.

**Preço confirmado.** O checkout envia `expectedTotal` exibido ao comprador. Se o total recalculado divergir (preço ou frete alterado), retorna `409 PRICE_CHANGED` com a nova cotação; nada é gravado e o comprador confirma de novo. O backend nunca aceita preço do cliente.

**Estados nesta etapa:** `PENDING_PAYMENT → EXPIRED` (prazo) e `PENDING_PAYMENT → CANCELLED` (lojista no admin, com motivo; libera reservas). Transições por CAS de `version`/status, idempotentes. `PAID`, separação, envio, entrega e devolução pertencem a etapas seguintes. **Não** haverá "marcar como pago manualmente" (impacto financeiro, fora do escopo aprovado).

**Acompanhamento pelo comprador.** Resposta do checkout traz token de acesso aleatório de 256 bits, mostrado/guardado uma vez; banco guarda somente o hash. A vitrine grava o token em cookie `HttpOnly` por pedido e mostra a página de confirmação/status. `GET /api/public/stores/{slug}/orders/{orderId}` exige o token (header enviado pelo servidor do storefront-web) e devolve somente status, itens, totais e entrega; token errado, pedido de outra loja ou inexistente → 404 uniforme. Envio de e-mail ao comprador não faz parte (não há provedor aprovado).

## 4. Frete

### D3 — Política de entrega (requer aprovação)

| Opção | Trade-offs |
| --- | --- |
| Só retirada | Mais simples, sem endereço (menos dados pessoais); exclui lojas que entregam |
| **Retirada + frete fixo único por loja (recomendada)** | Cobre o PRD com uma regra; lojista define valor ≥ 0 (zero = grátis) e texto de área atendida; sem cálculo por CEP |
| Fixo + grátis acima de valor | Útil comercialmente; nova regra financeira a testar; pode entrar depois sem migração destrutiva |
| Tabela por faixa de CEP/região | Mais justa; cadastro trabalhoso e validação de CEP; adiar |
| Integração com transportadora/Correios | Explicitamente fora do MVP no PRD |

Configuração da loja (módulo tenant): `pickup_enabled` + instruções/endereço de retirada (texto, até 500 caracteres), `fixed_shipping_enabled` + `fixed_shipping_amount` (BRL) + descrição da área atendida. Pelo menos uma opção ativa para aceitar pedidos. A área atendida é informativa: o sistema não valida cobertura por CEP, e a tela deixa isso explícito ao comprador. Snapshot dos valores e textos no pedido.

## 5. Dados do comprador e LGPD

### D2 — Dados coletados e retenção (requer aprovação; validação jurídica recomendada)

| Opção | Trade-offs |
| --- | --- |
| **Mínimo (recomendada):** nome, e-mail, telefone; endereço só em `FIXED_SHIPPING`; sem CPF, data de nascimento ou conta | Atende contato e entrega; CPF pode ser exigido pelo provedor na Sprint 4 — preferir coletá-lo no próprio checkout do Mercado Pago |
| Mínimo + CPF | Facilita nota/pagamento futuro; dado sensível ao contexto de fraude, sem emissão fiscal no MVP |
| Somente e-mail | Menor exposição; dificulta entrega e contato do lojista |

Propostas complementares:

- Dados gravados como snapshot no pedido; **sem** tabela `Customer` deduplicada nesta etapa (a do DATA_MODEL fica adiada), evitando perfil consolidado do comprador. Nada é compartilhado entre lojas, nem por e-mail igual.
- Base legal presumida: execução de contrato a pedido do titular; lojista como controlador e plataforma como operadora — **hipótese a validar juridicamente** e refletir nos termos antes de produção. Checkout mostra aviso de privacidade e link para política da loja (texto a aprovar).
- Retenção proposta: pedidos `EXPIRED`/`CANCELLED` sem pagamento têm dados pessoais anonimizados após 90 dias por rotina; pedidos pagos seguem prazo legal/fiscal a definir (Sprint 4/5). Prazos são propostas, não aconselhamento jurídico.
- Logs, métricas e mensagens de erro nunca contêm nome, e-mail, telefone, endereço ou token. Acesso administrativo apenas por OWNER do tenant (ADR-0006). Criptografia por coluna não proposta no MVP; reavaliar com a arquitetura de deploy.

## 6. Idempotência

- Checkout exige `requestId` UUID gerado pela vitrine por tentativa de compra e preservado em resultado ambíguo (mesmo comportamento do ADR-0009). Único por tenant.
- `request_fingerprint` = SHA-256 do payload canônico (linhas ordenadas, entrega, comprador, `expectedTotal`). Mesma chave + mesma impressão → mesmo pedido, `200` e `replayed=true`, sem nova reserva; chave com outro payload → `409 REQUEST_CONFLICT`. A resposta repetida não reemite o token (já entregue); a vitrine o mantém em cookie.
- Chave só é consumida em transação confirmada; `INSUFFICIENT_STOCK` ou `PRICE_CHANGED` não consomem a chave.
- Expiração, cancelamento e liberação são idempotentes por estado (`ACTIVE` → final apenas uma vez). Base para a Sprint 4: transição `PENDING_PAYMENT → PAID` e consumo da reserva idempotentes por evento de pagamento.

## 7. Isolamento de tenant

- Caminho público: o tenant é resolvido **pelo servidor** a partir do slug de loja `PUBLISHED`; cada variante enviada é revalidada como pertencente a essa loja/tenant, publicada e de produto publicado. IDs do cliente são seletores, nunca autorização. FKs compostas impedem item, reserva ou pedido apontando para outro tenant.
- Caminho administrativo: `GET /api/tenants/{tenantId}/orders` (paginação por cursor), detalhe e `POST …/orders/{orderId}/cancel` exigem identidade ativa/verificada e membership OWNER, com 404 uniforme para ausência ou acesso cruzado, CSRF e origem como hoje.
- Rotina de expiração percorre todos os tenants, mas cada atualização mantém `tenant_id` da própria linha e não mistura dados.
- Cadeia pública ganha `POST` apenas para cotação e checkout, ainda stateless, sem cookies do backend, `Content-Type: application/json` estrito e corpo limitado (proposta: 16 KiB). CSRF não se aplica por não haver autoridade ambiente no backend; a proteção de origem fica nas ações do storefront-web.

### D4 — Proteção contra abuso do checkout público (requer aprovação)

Pedidos anônimos podem travar estoque escasso por todo o prazo. Opções: (a) apenas limites de quantidade (10/linha, 20 linhas) e prazo curto; (b) **(a) + limites atômicos no PostgreSQL** por IP e por e-mail normalizado (ex.: 5 pedidos pendentes por e-mail por loja, 20 checkouts/IP/hora), no padrão D5 da identidade; (c) CAPTCHA/serviço externo. **Recomendação: (b)**, sem serviço externo; (c) somente com evidência de abuso e aprovação. Exige decidir como o storefront-web encaminha o IP do visitante de forma confiável (header definido apenas pelo servidor da vitrine e aceito apenas dessa origem); IP é dado pessoal e entra na retenção de D2 (proposta: contadores de curta duração, sem log de IP).

Quantidade insuficiente responde sem revelar o saldo exato; o limite por linha reduz a inferência por tentativas, mas não a elimina — risco residual aceito apenas mediante aprovação.

## 8. Decisões que exigem aprovação humana

| ID | Decisão | Recomendação | Bloqueia |
| --- | --- | --- | --- |
| D1 | Prazo da reserva | 30 min, único na plataforma; Sprint 4 restringe meios de pagamento ao prazo | SC-005, SC-008 |
| D2 | Dados do comprador, base legal e retenção | Nome, e-mail, telefone, endereço só na entrega; sem CPF; snapshot no pedido; anonimizar não pagos em 90 dias; validação jurídica antes de produção | SC-008, SC-009 |
| D3 | Política de frete | Retirada + frete fixo único por loja; sem CEP | SC-006 |
| D4 | Anti-abuso do checkout | Limites de quantidade + limites atômicos no PostgreSQL por IP/e-mail | SC-008 |
| D5 | Carrinho | Cookie da vitrine sem estado no servidor, cotação sem reserva | SC-007 |
| D6 | Exposição do checkout antes de pagamentos | Habilitado apenas por configuração em DEV/local até a Sprint 4; produção nunca aceita pedido sem meio de pagamento | SC-009 |
| D7 | Ajuste negativo do lojista abaixo do reservado | Bloquear com `409 RESERVED_STOCK`, informando o reservado ao lojista; alternativa é permitir e marcar pedidos como em risco | SC-005 |

Cada aprovação deve ser registrada neste ADR (status passa a "adotado" somente para o que foi aprovado), sem inferir aprovação das demais.

### Riscos residuais R2 e R6 da SC-011 (2026-10-20)

O responsável aprovou em 2026-10-20 a recomendação de **aceitar** os dois riscos residuais da [SC-011](../../security/SC-011-CHECKOUT-THREAT-MODEL.md), com revisão antes da produção comercial:

- **R2, inferência do saldo pela cotação: aceito.**
  - **O que vaza:** a cotação honesta por linha (D5) revela o saldo exato quando ele é de 10 unidades ou menos.
  - **Por que não limitar a taxa:** o limite mitigaria pouco. Uma busca binária entre 1 e 10 descobre o saldo com cerca de 4 cotações, menos do que um comprador legítimo faz ao montar o carrinho. O próprio checkout, limitado pelo D4, também revela disponibilidade.
  - **Custo de limitar:** exigiria ligar o módulo da vitrine ao verificador de IP de pedidos e assinar o IP em toda cotação.
  - **Gatilho de revisão:** lojista que precise esconder o saldo exato (por exemplo, "últimas unidades" sem número). A alternativa preferida é então uma cotação que só informe "disponível/indisponível" para a quantidade pedida, e não um limite de taxa.
- **R6, número sequencial do pedido: aceito.**
  - **O que vaza:** o número por loja é visível ao comprador e revela o volume aproximado de pedidos.
  - **Por que aceitar:** é o padrão do varejo e facilita o atendimento e a conferência pelo lojista.
  - **Gatilho de revisão:** antes da produção comercial, se algum lojista pedir. A alternativa é exibir ao comprador um código não sequencial e manter o número só no admin.

## 9. Fora desta etapa (Sprint 4 — Mercado Pago e além)

- OAuth da conta do lojista, credenciais cifradas por tenant, Checkout Pro a partir do pedido `PENDING_PAYMENT`, restrição de meios de pagamento compatível com D1, expiração da preferência alinhada à reserva.
- Webhook autenticado, idempotente, confirmado por consulta ao provedor; entidades `Payment`/`PaymentEvent`; transição `PAID` e consumo da reserva com baixa no ledger. O ledger exigirá novo tipo de movimento (ex.: `SALE`) e decisão sobre ator de sistema, pois `inventory_movement.actor_id` hoje é obrigatório e referencia `identity_user`.
- Pagamento aprovado após expiração, reembolso controlado (sempre com aprovação humana), conciliação mínima, notificações ao comprador por e-mail, CPF via provedor se exigido.
- Fora do MVP ou posterior: descontos/cupons, frete grátis por valor, frete por CEP/transportadora, conta do comprador, carrinho abandonado, edição de pedido, separação/envio/entrega, devoluções, tabela `Customer`, prazo de reserva por loja, RLS.

## Riscos

- Locks de saldo serializam compras simultâneas da mesma variante (aceitável no volume alvo; medir em teste de concorrência).
- Expiração lógica exige que **toda** leitura de disponibilidade use a mesma função de repositório; leitura direta de `on_hand` em vitrine/admin seria regressão — coberta por testes e revisão.
- Cookie de carrinho manipulado é esperado e inofensivo apenas se o backend revalidar tudo; testes devem cobrir IDs alheios e quantidades fora do limite.
- Expor checkout sem pagamento em produção enganaria compradores reais (mitigado por D6).
- Dados pessoais entram no sistema pela primeira vez a partir de compradores não autenticados; sem D2 aprovado, SC-008 não começa.
- SEC-003 permanece aberta e inalterada; este ADR não aceita risco nem autoriza dependências, deploy, commit/push além dos documentos.
