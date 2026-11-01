# Modelo de domínio preliminar (não é schema final)
- User(id, email, auth_subject, status)
- Tenant(id, legal_name, status)
- TenantMembership(id, tenant_id, user_id, role)
- Store(id, tenant_id, slug, display_name, status)
- Product(id, tenant_id, store_id, title, description, status)
- ProductVariant(id, tenant_id, product_id, sku, price, cost, currency)
- InventoryBalance(id, tenant_id, variant_id, on_hand, reserved)
- InventoryMovement(id, tenant_id, variant_id, type, quantity, reference_id)
- Customer(id, tenant_id, name, email, phone)
- Order(id, tenant_id, store_id, customer_id, status, currency, totals)
- OrderItem(id, tenant_id, order_id, variant_id, quantity, price_snapshot, cost_snapshot)
- PaymentConnection(id, tenant_id, provider, encrypted_credentials_reference, status)
- Payment(id, tenant_id, order_id, provider, external_id, status, amount)
- PaymentEvent(id, tenant_id, provider, event_id, payload_reference, processed_at)
- Expense(id, tenant_id, category, amount, due_date, paid_at)
- FinancialEntry(id, tenant_id, source_type, source_id, type, amount, occurred_at, settled_at)
- AuditEvent(id, tenant_id, actor_id, action, resource_id, occurred_at)

Decisões pendentes: chaves, constraints, índices, normalização, política de exclusão e retenção. Não usar este documento como migração pronta.

## Subconjunto implementado na SC-002 Etapa 1

V2 cria somente `identity_user` e `identity_local_credential`, conforme [IDENTITY_FOUNDATION.md](IDENTITY_FOUNDATION.md). Usuário global: UUID, e-mail original/chave única sensível à caixa local, status, auth_version e datas; credencial separada: PK/FK user_id, hash versionado e datas. Na Etapa 1 não havia auth_subject externo ou tabelas empresariais; a evolução autorizada está descrita abaixo.

Conta pendente não recebe credencial pelo repositório; a entrega funcional posterior implementou prova de e-mail seguida de ativação explícita. V1 permanece intacta. Modelo global não constitui garantia de autorização empresarial ou isolamento A/B. Contrato de comparação e limites IDNA/Unicode constam no documento específico, sem aplicar políticas futuras de retenção nesta etapa.

## SC-003 — Empresa e loja

V5 adiciona tenant (empresa), tenant_membership (vínculo do usuário global com OWNER) e store (loja com tenant_id, slug global único, nome e status DRAFT). A unicidade de store.tenant_id limita a uma loja por empresa. Criação conjunta transacional; consultas exigem identidade e membership, sem contexto confiado ao browser. Nomes são autodeclarados, sem dados fiscais. Sem novos domínios comerciais. Contrato e critérios: [ADR-0006](adr/0006-tenant-store-onboarding.md).

## Catálogo básico autorizado

V6 cria catalog_product (UUID, tenant_id, store_id, name, sku, price, cost opcional, currency BRL, version e datas). SKU único por tenant, com comparação sensível à caixa. Dinheiro em NUMERIC sem typmod com CHECK de escala e faixa; preço positivo, custo não negativo ou NULL. FK composta store_id/tenant_id impede referências cruzadas. Versão otimista evita sobrescrita. Contrato: [ADR-0007](adr/0007-basic-catalog.md).

## Estoque básico autorizado

V7 acrescenta inventory_balance (produto, tenant, quantidade inteira não negativa) e inventory_movement (sequência, UUID público, tenant/produto/ator/requestId, tipo, delta, saldo após, motivo e instante). FKs compostas preservam tenant; requestId é único por tenant. Atualização de saldo e ledger é transacional, com bloqueio de saldo negativo e repetição idempotente. Sem reservas nesta entrega. [ADR-0009](adr/0009-basic-inventory.md).

## Vitrine pública autorizada

V8 expande store.status para DRAFT/PUBLISHED e adiciona catalog_product.published=false. Publicação da loja e seleção individual são necessárias, com autorização no admin e DTOs públicos restritos no novo módulo storefront. A aplicação storefront-web consulta somente a API pública, sem compartilhar autenticação com admin. [ADR-0010](adr/0010-public-storefront.md).

## Fotos, descrição e variantes

V9 acrescenta descrição HTML sanitizada e tipo SIMPLE/VARIABLE; cria catalog_variant com SKU/preço/custo/opções e unicidade de combinação. A variante padrão preserva o UUID do produto simples, e as referências de estoque passam a apontar para a unidade vendável sem reescrever saldos ou movimentos. V10 cria catalog_product_image com FK composta produto/tenant, bytes JPEG/PNG normalizados, dimensões, descrição alternativa e ordem. Não há armazenamento em caminhos fornecidos pelo cliente. [ADR-0011](adr/0011-catalog-media-description-variants.md).

## Reservas e entrega (Sprint 3)

V11 cria inventory_reservation (tenant, variante, pedido, quantidade, estado ACTIVE/CONSUMED/RELEASED/EXPIRED, expires_at, closed_at), com FK composta variante/tenant, uma reserva por pedido/variante e índice parcial das ativas. Reservas não alteram inventory_balance nem o ledger. A FK composta de order_id para sales_order será criada na SC-008, que introduz o pedido. As funções inventory_reserved_quantity e inventory_available_quantity concentram a fórmula de disponibilidade. V12 cria store_fulfillment 1:1 com a loja (FK composta loja/tenant): retirada com instruções e frete fixo em NUMERIC sem arredondamento (zero = grátis), área atendida e versão otimista. [ADR-0012](adr/0012-stock-reservation-cart-orders.md).

## Pedidos (SC-008)

V13 cria `sales_order` (tenant, loja, número por loja via `sales_order_counter`, estado PENDING_PAYMENT/EXPIRED/CANCELLED, totais em NUMERIC sem arredondamento com total = subtotal + frete, entrega escolhida e texto exibido, dados mínimos do comprador do D2, endereço só para frete fixo, prazo da reserva, `request_id` único por tenant com impressão digital e hash do token) e `sales_order_item` (snapshot de nome, opções, SKU, preço e custo opcional, quantidade e total da linha), com FKs compostas por tenant para loja, pedido e variante. A FK composta `inventory_reservation(order_id, tenant_id) → sales_order` foi criada como NOT VALID: vale para toda reserva nova, sem rejeitar reservas anteriores aos pedidos. `order_rate_limit` guarda contadores de tentativas com chave HMAC efêmera, sem IP em claro. Não há tabela Customer. [ADR-0012](adr/0012-stock-reservation-cart-orders.md).

V14 indexa `sales_order_item(variant_id, tenant_id)` para a FK RESTRICT. V15 (SC-010) acrescenta a `sales_order` `expired_at`, `cancelled_at`, `cancelled_by` (FK para `identity_user`), `cancel_reason` (3–300 caracteres) e `anonymized_at`, com estas CHECKs:
- dados de cancelamento existem exatamente em pedidos `CANCELLED`;
- os campos do comprador só ficam nulos, todos de uma vez, em pedido `EXPIRED`/`CANCELLED` anonimizado;
- a regra do endereço (V13) vale enquanto o pedido guarda dados pessoais.

Também cria índices para os lotes de expiração e anonimização e para a lista filtrada do admin. A anonimização substitui a impressão digital do `requestId` por zeros, porque ela deriva dos dados do comprador. Nenhum dado é alterado pela migração.

V16 (SC-011) valida a FK composta `inventory_reservation(order_id, tenant_id) → sales_order` quando todas as reservas têm pedido do mesmo tenant. Se um banco local ainda tiver reservas de antes dos pedidos, a FK continua `NOT VALID` (vale para linhas novas), a migração emite um aviso e nenhuma linha é apagada. Na SC-011 a anonimização passou a substituir também `cancel_reason` por um texto fixo, mantendo as CHECKs da V15.

## Pagamentos (Sprint 4)

V17 (SC-012) cria `payment_oauth_state` (tentativas de OAuth com o `state` só em SHA-256 e o verificador PKCE cifrado) e `payment_account` (uma conta Mercado Pago por loja, com tokens AES-256-GCM que só existem enquanto a conta está `CONNECTED`). Detalhes em [SC-012-VALIDATION.md](../quality/SC-012-VALIDATION.md).

V18 (SC-013) cria `payment`, 1:1 com o pedido (`UNIQUE (order_id)` e FK composta pedido/tenant):
- estado `CREATING` (linha reservada antes de chamar o provedor) ou `CREATED` (order do provedor criada);
- valor em NUMERIC sem arredondamento e moeda `BRL`;
- em `CREATED`, e só nele (CHECK): conta do vendedor usada, id da order no provedor (único por provedor), status do provedor, `checkout_url` HTTPS e vencimento da order.

Não guarda CPF, cartão nem e-mail do pagador (ADR-0013 P11). [ADR-0013](adr/0013-mercado-pago-checkout-pro.md).

V19 (SC-014), aditiva:
- **`sales_order`:** estado `PAID`, `paid_at` e `paid_without_stock` (P4). As CHECKs de cancelamento e expiração passam a admitir `PAID` depois desses estados, mantendo os dados como histórico. A anonimização (D2) continua só em `EXPIRED`/`CANCELLED`.
- **`payment`:** estado `APPROVED` (com `approved_at` e `paid_amount = amount`), `provider_status_detail` e `checked_at`.
- **`payment_event`:** notificações do provedor, únicas por `(provider, event_key)`, só com dados de roteamento (nunca o corpo), tenant resolvido pelo pagamento guardado, estado `PENDING`/`PROCESSED`/`IGNORED`/`FAILED`, tentativas e próximo horário.
- **`inventory_movement` (P7):** `actor_kind` USER/SYSTEM (`actor_id` obrigatório só para USER), tipos `SALE` (negativo) e `SALE_REVERSAL` (positivo, SC-016) e `order_id` com FK composta para o pedido, obrigatório exatamente nesses tipos e só com ator de sistema.

As CHECKs antigas sem nome explícito são removidas pela definição, e a migração falha se não encontrar exatamente uma.

## Financeiro (Sprint 5)

V22 (SC-019), aditiva: `finance_entry`, livro só de acréscimo ([ADR-0015](adr/0015-finance-dashboard.md) F7):
- **tipos:** `SALE`, `REFUND`, `FEE`, `COGS`, `COGS_REVERSAL` e `EXPENSE`;
- **valores:** com sinal conferido por CHECK, em BRL;
- **datas:** `competence_at` e `settled_at`;
- **origem:** pedido, por FK composta com o tenant;
- **custo:** `cost_incomplete` para CMV de itens sem custo;
- **chave:** única por `(tenant_id, entry_key)`, derivada do fato.

A migração cria os lançamentos dos pedidos já pagos ou reembolsados, com as mesmas chaves do código.

V23 (SC-020), aditiva:
- **`expense`:** categoria de lista fixa, tipo fixa ou variável, descrição, valor positivo, competência e pagamento como datas de São Paulo, `request_id` único por tenant, autor e cancelamento (quem, quando e motivo, juntos). Nunca é apagada.
- **`finance_entry`:** ganha `expense_id` (FK composta) e o tipo `EXPENSE_REVERSAL` (positivo), com CHECK que liga os tipos de despesa à coluna.

V24 (SC-025), aditiva: `advisor_usage`, uma linha por pergunta ao assistente, **só metadados**:
- **o que guarda:** loja, usuário, horário, resultado (`PENDING`, `ANSWERED`, `REFUSED`, `UNGROUNDED`, `PROVIDER_ERROR`, `CANCELLED`), modelo, tokens, quantidade e nomes das ferramentas usadas e custo estimado;
- **o que nunca guarda:** o texto da pergunta ou da resposta;
- **ciclo de vida:** `finished_at` existe exatamente para as linhas fora de `PENDING`; uma rotina cancela as pendentes presas e apaga as linhas com mais de 90 dias ([ADR-0016](adr/0016-consultative-agent.md) A8, A9).

V26 (mudança da P1 do ADR-0013, 2026-10-24), aditiva: `payment.provider_payment_id`, o id do pagamento aprovado no Mercado Pago (só dígitos, único por provedor), usado no reembolso. Com preferências, `payment.provider_order_id` guarda o id da preferência e `payment_event.provider_order_id` o id do pagamento notificado (tópico `payment`).

V28 (bloco 1a do MVP, 2026-10-26), aditiva: `identity_password_reset`, provas de redefinição de senha no mesmo formato de `identity_email_verification`: só o SHA-256 do token, uso único, validade de 30 minutos e exclusão em cascata com o usuário.
V29 (bloco 1b do MVP, 2026-10-26), aditiva: `store_profile`, uma linha opcional por loja com contatos públicos (e-mail, WhatsApp em E.164 e usuário do Instagram) e o logo (bytes JPEG/PNG normalizados no banco, como as fotos do ADR-0011), com versão otimista. O nome continua em `store.name`. Depende da V28 (bloco 1a).

V30 (bloco 1c do MVP, 2026-10-26), aditiva: `catalog_category` (nome de 1 a 60 caracteres e `name_key` minúsculo, único por tenant; FK composta para a loja) e `catalog_product.category_id`, opcional, com FK composta `(category_id, tenant_id)`, de modo que um produto nunca aponta para categoria de outro tenant. Uma categoria por produto, sem hierarquia. Remover a categoria limpa `category_id` dos produtos antes; nenhum produto é apagado. Depende da V29 (bloco 1b).

V31 (SC-031, ADR-0017, 2026-10-27), aditiva: `billing_account`, uma linha por proprietário (`identity_user`). Guarda o status gravado (`TRIAL`, `ACTIVE`, `PAST_DUE`, `SUSPENDED` ou `CANCELED`), o plano e o ciclo (nulos no teste grátis), `trial_ends_at`, `current_period_end` e `past_due_since`, e os identificadores da Stripe, únicos e preenchidos na SC-032. O teste vencido (`TRIAL_EXPIRED`) e a suspensão depois da carência são calculados pelo tempo, sem rotina agendada. A conta nasce no primeiro uso, com 14 dias de teste. Os limites valem para todas as empresas do proprietário.

V32 (SC-032, ADR-0017 B6, 2026-10-27), aditiva: `billing_event`, com o id de cada evento de webhook da Stripe já processado, o tipo e a data de recebimento. Serve só para idempotência: o corpo do evento não é guardado, porque a conta é conciliada pela API. Não tem dado pessoal.

V33 (SC-041, [ADR-0018](adr/0018-storefront-v1.md) V1, 2026-10-27), aditiva: `catalog_category` vira árvore de até 3 níveis. Ganha `parent_id` (FK composta `(parent_id, tenant_id)`, nunca a própria categoria), `slug` (letras minúsculas, números e hífens, único por loja), `position` (ordem entre irmãs) e `published` (ocultar a categoria na vitrine sem ocultar os produtos). A unicidade do nome passa a ser entre irmãs (`tenant_id`, pai, `name_key`). As categorias existentes viram raiz, visíveis, em ordem alfabética, com slug derivado do nome (acentos removidos; colisões ganham o início do id). Profundidade e ciclos são conferidos pela aplicação com todas as categorias do tenant bloqueadas (`FOR UPDATE`). Depende da V32.

V34 (SC-041, [ADR-0018](adr/0018-storefront-v1.md) V2, V3 e V5, 2026-10-27), aditiva: `catalog_brand` (nome de 1 a 60 caracteres, único por tenant sem diferenciar maiúsculas, e `slug` único por loja, ambos com FK composta para a loja) e `catalog_product.brand_id`, opcional, com FK composta `(brand_id, tenant_id)`. `catalog_product.slug` é o endereço do produto na vitrine, único por loja; os produtos existentes recebem o slug derivado do nome, cortado em 60 caracteres, e colisões ganham o início do id. A coluna aceita nulo só para linhas gravadas fora da aplicação, que sempre a preenche na criação. `catalog_product_slug_history` guarda os endereços anteriores (loja, slug, produto, data da troca) para redirecionar links antigos (SC-045); um endereço reassumido sai do histórico. `catalog_variant.compare_at_price`, opcional e positivo, é o preço "de" só para exibição: a vitrine o mostra quando é maior que o preço, e o valor cobrado continua sendo o preço. Depende da V33.

V35 (SC-042, ADR-0018 V6 e V7, 2026-10-27), aditiva: extensão `unaccent` (contrib confiável: o dono do banco a cria sem superusuário), função `sc_unaccent(text)` marcada `IMMUTABLE` (fixa o dicionário, forma documentada de indexar `unaccent`), índice GIN `to_tsvector('portuguese', sc_unaccent(name))` em `catalog_product` para a busca da vitrine e índice `(product_id, tenant_id)` em `sales_order_item` para "mais vendidos". Depende da V34.

V36 (SC-042, ADR-0018 V4, 2026-10-27), aditiva: `store.max_installments`, opcional, de 1 a 12. Nulo mantém o comportamento anterior: a preferência do Mercado Pago não leva limite e a vitrine não promete parcelas. Depende da V35.

V37 (SC-043, ADR-0018 V1, 2026-10-27), aditiva: `catalog_product_category` liga um produto às categorias **adicionais**, além da principal em `catalog_product.category_id` (que segue definindo o caminho e os relacionados). Chave `(product_id, category_id)`; FKs compostas com o tenant para os dois lados, com `ON DELETE CASCADE`. A aplicação limita a 10 por produto e remove o vínculo quando a categoria vira a principal. O filtro e as contagens da vitrine consideram as duas, contando cada produto uma vez. Depende da V36.

V38 (SC-043, ADR-0018 V8 e V13, 2026-10-27), aditiva:
- `store_company`, uma linha opcional por loja: faixa de aviso, razão social, CNPJ (14 dígitos, verificadores conferidos na aplicação), telefone (E.164 brasileiro), endereço e Facebook, TikTok e YouTube guardados como nome de usuário, nunca como URL;
- `store_page`, páginas institucionais de tipos fechados (`ABOUT`, `RETURNS`, `PRIVACY`, `TERMS`, `SHIPPING`), com o HTML já limpo pelo sanitizador da descrição de produto. Página sem texto é apagada.

Ambas com FK composta `(store_id, tenant_id)`. Depende da V37.

V39 (SC-043, ADR-0018 V8, 2026-10-27), aditiva:
- `store_banner`: posição, texto alternativo obrigatório, título opcional, link interno (`NONE`, `CATEGORY`, `PRODUCT` ou `SEARCH`, com o alvo só quando há link), período opcional (`starts_at` antes de `ends_at`) e duas imagens normalizadas (principal e celular, JPEG/PNG até 5 MiB). A aplicação limita a 5 por loja. A vitrine só mostra banner com imagem principal e dentro do período;
- `store_home_section`: seções da home em ordem (`NEW`, `BEST`, `FEATURED`, `CATEGORY`), com título; `category_id` só nas de categoria, por FK composta com o tenant, e apagar a categoria apaga a seção;
- `catalog_product.featured`, `false` por padrão: produtos da seção "Destaques".

Depende da V38.

V40 (SC-049, ADR-0019 C1, 2026-11-01), aditiva:
- `tenant_membership.role` aceita `OWNER` e `STAFF`; um índice único parcial garante um só `OWNER` por empresa. Todas as consultas de acesso existentes já exigiam `role='OWNER'`, então o `STAFF` nasce sem acesso a elas.
- `staff_invitation`: um convite por linha, com todo o ciclo de vida (enviado, aceito ou cancelado, acesso revogado). Guarda só o hash SHA-256 do token de uso único, o e-mail convidado (chave de comparação e grafia original, o mínimo para casar com a conta e mostrar ao OWNER), quem convidou, validade de 7 dias, quem aceitou e quem revogou. Um índice único parcial deixa no máximo um convite em aberto por e-mail e empresa.
- **Dado pessoal:** o e-mail do convite fica guardado também depois de aceito, cancelado ou expirado, para o histórico da equipe. A retenção segue a mesma validação jurídica pendente do D2.

V27 (SC-023), aditiva: um trigger em `finance_entry` faz o banco garantir que o livro é só de acréscimo (F7):
- **`DELETE`:** recusado sempre;
- **`UPDATE`:** só para preencher `settled_at` uma única vez, a partir de nulo, que é o caminho da SC-018;
- **`TRUNCATE`:** não é afetado, porque não é operação de linha; os testes o usam em bancos descartáveis.

V21 (SC-016), aditiva:
- **`sales_order`:** estado `REFUNDED` com `refunded_at`. As CHECKs mantêm `paid_at` e o histórico de expiração e cancelamento também em `REFUNDED`.
- **`payment`:** estado `REFUNDED`.
- **`payment_refund`:** um reembolso total por pagamento, com motivo (3–300), `restock`, `requested_by` (FK `identity_user`), `requested_at`, aceite do provedor, último erro e `confirmed_at`, que existe exatamente em `CONFIRMED`.

V20 (SC-015), aditiva: `payment.next_check_at` (próxima consulta da conciliação), `check_attempts` e `last_check_error` (divergência mostrada ao lojista), com índice parcial dos pagamentos `CREATED`. Taxa e líquido não são guardados, porque a Orders API não os informava; com a API de pagamentos (V26) ficam disponíveis para a SC-018.
