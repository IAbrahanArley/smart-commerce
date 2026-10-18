# Validação local: SC-009, checkout e acompanhamento do pedido na vitrine

Data: 2026-10-18. Branch `feat/sc-009-checkout-ui`, empilhada sobre `feat/sc-008-checkout-orders` (`54a0613`, PR #8). Escopo: [SPRINT_03.md](../product/SPRINT_03.md) e [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md) D2, D4 e D6. Somente `storefront-web`; o backend é o da SC-008, sem alterações.

## Entrega

**Flag do checkout (D6).** A variável `STOREFRONT_CHECKOUT_ENABLED` é lida no servidor e só liga o fluxo com o valor exato `true`.
- Desligada: o carrinho não mostra o link "Finalizar pedido", `/lojas/{slug}/checkout` responde 404 e a server action também.
- Ligada: o link aparece quando o carrinho está comprável e a loja tem ao menos uma forma de entrega.
- O backend mantém sua própria trava, ligada só no perfil `dev`.

**Página `/lojas/{slug}/checkout` (SSR, `noindex`).**
- Carrinho vazio redireciona para o carrinho. Carrinho não comprável redireciona para o carrinho com o aviso `revisar`.
- Mostra as opções de entrega da cotação, cada uma com o total somado em centavos (`addMoney`, exato, sem ponto flutuante).
- Resumo lateral com os itens cotados.

**Formulário (`useActionState`, funciona sem JavaScript).**
- Entrega por rádio, com o rótulo e o total no mesmo alvo de clique.
- Campos nome, e-mail e telefone, com `autocomplete`, `inputmode` e exemplos.
- Endereço exigido só no frete fixo. Quando a retirada está marcada, o CSS `:has` esconde o endereço.
- Aviso de privacidade com o texto aprovado no D2 e o nome da loja.
- Botão desabilitado enquanto o envio está em andamento ("Confirmando pedido…").

**Erros.**
- Validação no servidor espelha os limites da SC-008. Caracteres de controle e separadores de linha são rejeitados.
- O resumo de erros recebe foco e usa `role=alert`, com links para cada campo.
- Cada campo com erro recebe `aria-invalid` e `aria-describedby`.
- Erro de entrega fica no `fieldset`, porque `aria-invalid` não é válido em rádio.

**Server action `placeOrder`.**
- O carrinho vem do cookie HttpOnly, nunca do formulário. Origem externa é rejeitada pelo Next.
- Pedido criado: grava o cookie do pedido, limpa o carrinho e redireciona para a página de status.
- `PRICE_CHANGED`, `INSUFFICIENT_STOCK`, `ITEM_UNAVAILABLE` e `FULFILLMENT_UNAVAILABLE`: revalida a página para mostrar a nova cotação e explica o motivo.
- `REQUEST_CONFLICT`: gera nova chave.
- Falha de rede, timeout ou resposta inesperada é tratada como ambígua: mantém o mesmo `requestId`, e a nova tentativa reaproveita o pedido sem duplicar.
- Limite 429: mensagem própria.

**Cookie do pedido `sc-order-{id}`.**
- Guarda o token de 43 caracteres base64url devolvido uma única vez pelo backend.
- HttpOnly, SameSite Lax, Secure fora do desenvolvimento, validade de 30 dias.
- `Path` restrito a `/lojas/{slug}/pedidos/{id}`: não é enviado a outras páginas, outras lojas ou outros pedidos.

**Página `/lojas/{slug}/pedidos/{id}` (SSR, `noindex`).**
- Responde 404 sem um token válido no cookie.
- Mostra o número do pedido, a situação (Aguardando pagamento / Reserva expirada / Pedido cancelado), o prazo da reserva em America/Sao_Paulo com `Intl.DateTimeFormat`, os itens, a entrega e os valores.
- O parser projeta só os campos públicos e confere o `id` devolvido.

**IP do visitante (D4).**
- Assinado com HMAC (`signedClientIp`) somente quando há `STOREFRONT_FORWARDING_SECRET` com pelo menos 32 caracteres e um proxy confiável declarado (`STOREFRONT_TRUST_FORWARDED_FOR=true`, ver achado abaixo).
- Sem os dois, nenhum header de IP é enviado.

**Tokens CSS.** `--store-control-border`, `--store-danger` e `--store-danger-background` repetem os valores já aprovados de `--sc-color-control-border` e `--sc-color-danger*`. A paleta não muda; só as mesmas cores passam a existir também na vitrine.

## Achado de segurança corrigido durante a implementação

O Next repassa sem alteração um `X-Forwarded-For` enviado pelo visitante. A primeira versão assinava o último salto desse header. Com isso, qualquer visitante escolheria o IP que o backend usa no limite por IP: daria para contornar o limite ou esgotar o balde de outra pessoa.

Correção: o header só é considerado quando a implantação declara um proxy na frente que o sobrescreve (`STOREFRONT_TRUST_FORWARDED_FOR=true`). Sem isso, nenhum IP é assinado e o backend limita pelo endereço da conexão. O smoke envia um `X-Forwarded-For` forjado e confirma que nenhum header `X-SC-Client-IP*` chega ao upstream.

## Verificações executadas

Em `storefront-web`, 2026-10-18 por volta de 14:40 -03:00, Node 24.12.0:
- `npm run lint`: aprovado, zero avisos.
- `npm run typecheck`: aprovado.
- `npm run build`: aprovado. Rotas novas: `ƒ /lojas/[slug]/checkout` e `ƒ /lojas/[slug]/pedidos/[orderId]`.
- `SMOKE_PORT=3042 npm run test:smoke`: "Smoke aprovado (checkout ligado)…" e "Smoke aprovado (checkout desligado)…". A porta padrão 3002 estava ocupada por outro processo local.
- `git diff --check`: limpo. Gitleaks não está instalado neste ambiente. Uma busca por padrões de segredo no diff encontrou apenas o segredo e o token sintéticos do smoke, sem valor real.

**Smoke, testes puros:**
- `addMoney`;
- flag do checkout;
- validação de cada campo (endereço só no frete, UF, CEP, telefone, caracteres de controle);
- cookie do pedido (nome, `Path`, atributos);
- `trustedClientIp` e `signedClientIp`, com assinatura igual ao HMAC do backend.

**Smoke, fluxo sem JavaScript com upstream sintético:**
- envio vazio e erros por campo;
- `PRICE_CHANGED` com a página revalidada;
- falha de rede seguida de nova tentativa com o mesmo `requestId`;
- pedido criado com cookie por caminho, carrinho limpo e status lido com `X-Order-Token`;
- status 404 sem cookie, com token inválido, com o cookie do pedido usado no slug de outra loja e com id inválido;
- `X-Forwarded-For` forjado não repassado;
- segundo servidor com a flag desligada: sem link e checkout 404.

## Validação em navegador (painel integrado)

**Ambiente descartável:**
- PostgreSQL em contêiner na porta 55432, com senha aleatória fora do repositório;
- backend da SC-008 no perfil `dev` na porta 8081;
- vitrine `next start` na porta 3021 com `STOREFRONT_CHECKOUT_ENABLED=true`.

Encerrado ao final: contêiner parado e removido, processos finalizados, portas livres, arquivo de senha apagado.

**Fluxo:**
1. Adicionar ao carrinho.
2. Selecionar "Finalizar pedido".
3. Enviar vazio: foco no resumo de erros, com links por campo.
4. Pedido válido com frete fixo leva à página "Pedido nº 1", "Aguardando pagamento", reserva até 15:06 (horário de Brasília), total R$ 112,30.

**Banco:**
- pedido com CEP, UF e telefone normalizados;
- token guardado só como hash, de 64 caracteres;
- reserva `ACTIVE` de 2 unidades por 30 minutos;
- disponível da variante caiu para 3.

**Navegador:**
- nenhum cookie legível por JavaScript (`document.cookie` vazio);
- `localStorage` e `sessionStorage` vazios;
- nenhum dado pessoal na URL;
- carrinho vazio depois do pedido.

**Celular (375×812):** checkout e status sem rolagem horizontal; opções de entrega com totais R$ 49,90 e R$ 62,40 legíveis.

## Revisão de UI ([web-design-guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md), obtida em 2026-10-18)

Arquivos revisados: `checkout-form.tsx`, `checkout/page.tsx`, `pedidos/[orderId]/page.tsx`, `carrinho/page.tsx` e `globals.css`. Sem achados bloqueantes.

**Atendidos:**
- rótulos clicáveis;
- `autocomplete` e `inputmode` corretos;
- foco visível com `:focus-visible`;
- números tabulares nos valores;
- `overflow-wrap` em textos longos;
- reticências tipográficas;
- datas e moeda com `Intl`;
- `translate="no"` no nome da loja;
- `defaultValue` (sem inputs controlados);
- botão desabilitado só durante o envio;
- links reais.

**Disposições:**
- "Title Case" não se aplica: o produto usa caixa de frase em português ([DESIGN.md](../design/DESIGN.md)).
- O primeiro erro não recebe foco direto: o foco vai para o resumo com links para cada campo, para que leitores de tela anunciem todos os erros.
- Sem aviso ao sair com dados não enviados. O formulário é curto e o aviso dependeria de JavaScript; fica para avaliação de UX futura.
- `spellCheck` desligado também em nome e endereço, para evitar correção automática de nomes próprios.

## Isolamento multi-tenant e privacidade

- A loja vem do slug publicado resolvido pelo backend. O formulário não envia loja, preço nem variante: os itens saem do cookie do carrinho da própria loja.
- O cookie do pedido é por caminho de loja e pedido. O backend devolve 404 uniforme para pedido de outra loja ou token errado (coberto em `OrderCheckoutIT`, SC-008); o smoke cobre o mesmo pedido consultado no slug de outra loja e token inválido.
- O token nunca aparece em URL, HTML ou `localStorage`. Os logs do Next não registram dados do comprador.
- Somente `Accept`, `Content-Type`, `X-Order-Token` e os headers assinados de IP vão ao backend. Cookies e `Authorization` do visitante nunca são repassados.

## Limitações e pendências

- **Replay sem cookie:** se o mesmo pedido for reaproveitado num navegador sem o cookie, só o número do pedido é mostrado, porque o backend não devolve o token de novo por segurança.
- **Loja oculta depois do pedido:** a página de status passa a responder 404, porque depende da loja publicada. A consulta do comprador nessa situação fica para a SC-010.
- **Limite por IP sem proxy confiável:** o backend vê só o servidor da vitrine, então na prática há um balde por servidor. O limite por e-mail continua valendo. Em produção, exige proxy que sobrescreva o header e a declaração explícita.
- **Acessibilidade:** sem Axe, leitor de tela ou testes automatizados de navegador (Playwright e Axe ficam para etapa futura). WCAG 2.2 AA é objetivo, não conformidade comprovada.
- **Pagamento e jurídico:** pagamento inexistente (Sprint 4). O checkout continua restrito a DEV. A política de privacidade completa e a validação jurídica ficam pendentes antes de produção.
- **CI remota:** depende do push desta branch, que aguarda autorização.
