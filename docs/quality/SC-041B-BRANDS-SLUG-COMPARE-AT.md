# SC-041 (parte 2) — Marcas, endereço do produto e preço "de": validação

Data: 2026-10-27. Base: SC-041 da [SPRINT_08.md](../product/SPRINT_08.md) e decisões V2, V3 e V5 do [ADR-0018](../architecture/adr/0018-storefront-v1.md). Completa a SC-041 junto com a [parte 1](SC-041A-CATEGORY-TREE.md). A exibição na vitrine (preço riscado, filtro por marca, URL legível e redirecionamento) fica para a SC-042 e a SC-045.

## Comportamento

**Banco (V34, aditiva):**
- `catalog_brand` por loja: nome único sem diferenciar maiúsculas e endereço (`slug`) único.
- `catalog_product.brand_id`, com FK composta, que impede usar a marca de outro tenant.
- `catalog_product.slug`, único por loja. Os produtos existentes recebem o endereço derivado do nome, cortado em 60 caracteres; colisões ganham o início do id.
- `catalog_product_slug_history` guarda os endereços anteriores para redirecionar links antigos.
- `catalog_variant.compare_at_price`, o preço "de", só para exibição.

**Marcas (OWNER, CSRF nas escritas):**
- Rotas `GET`/`POST /api/tenants/{id}/brands`, `PUT`/`DELETE /api/tenants/{id}/brands/{brandId}`.
- Nome com a mesma regra das categorias (1 a 60 caracteres, uma linha). Renomear não muda o endereço.
- Até 200 marcas por loja.
- Remover limpa a marca dos produtos, que continuam no catálogo.
- Marca de outra loja: 404 uniforme.

**Produto:**
- O endereço é gerado do nome ao criar o produto ("Tênis de Corrida" vira `tenis-de-corrida`; repetido, `tenis-de-corrida-2`). Salvar o formulário do produto nunca muda o endereço.
- Cartão "Vitrine" no editor, salvo por `PUT /api/tenants/{id}/products/{productId}/storefront`, separado do formulário principal. Por isso não altera a versão do produto nem entra em conflito com edições não salvas.
  - **Marca:** escolher ou "Sem marca".
  - **Endereço:** editável. O antigo vai para o histórico; reassumir um endereço antigo tira ele do histórico. Endereço de outro produto: 409 `SLUG_CONFLICT`.
  - **Preço "de":** por variante, opcional. Precisa ser maior que o preço atual (400 `COMPARE_AT_NOT_ABOVE`). Em branco, limpa.
- Se o preço do produto subir depois e passar do preço "de", o valor fica guardado, mas a vitrine não o mostra (regra da SC-042).

**Admin:**
- Nova tela "Marcas" no menu da loja (criar, renomear, remover com confirmação).
- Cartão "Vitrine" no editor do produto: marca, endereço e um preço "de" por variante, mostrando o preço atual. A validação no navegador tem mensagem por campo, `aria-invalid` e foco no primeiro erro.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `ProductStorefrontIT` (3, PostgreSQL real) | <ul><li>marcas: criação com endereço sem acento, nome repetido sem diferenciar maiúsculas, nome vazio, renomear sem mudar o endereço, remoção limpando os produtos, CSRF</li><li>outra loja: 404 ao listar, renomear, remover, usar a marca e editar o cartão</li><li>endereço gerado na criação, colisão com sufixo, conflito, formato inválido, histórico ao trocar e ao reassumir, formulário do produto sem mexer no endereço</li><li>preço "de" igual ao preço recusado, valor inválido, salvo com 2 casas, variante de outro produto 404, limpeza com nulo</li></ul> |
| `CategoryIT`, `CategoryTreeTest`, `CatalogIsolationIT` | sem regressão |
| Migração V34 sobre dados existentes | PostgreSQL descartável (mesma imagem dos testes), com "Tênis de Corrida", "tenis de corrida!", "★★★" e um nome de 120 caracteres: `tenis-de-corrida`, `tenis-de-corrida-b0000000`, `produto` e um endereço de 60 caracteres sem hífen no fim |
| `mvnw verify` completo (JDK 21, 2026-10-27) | formatação, 107 unitários e 175 de integração passando (BUILD SUCCESS). O 1 ignorado é a avaliação paga da SC-029, opcional. Depois de ajustar o texto da mensagem do preço "de", `ProductStorefrontIT` e a formatação rodaram de novo e passaram |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre a lista de marcas, a validação do preço "de" (vazio, igual, zero, texto e valor alto sem perda de precisão), as mensagens, a rota `/marcas` e o item novo do menu |
| Navegador (API sintética local, 1024 px e 375 px) | <ul><li>cartão "Vitrine" no editor com a lista de marcas</li><li>preço "de" igual ao atual: mensagem, `aria-invalid` e foco no campo</li><li>endereço ocupado: alerta</li><li>salvamento válido com endereço em maiúsculas normalizado</li><li>tela "Marcas" com item ativo no menu</li><li>375 px sem rolagem horizontal</li></ul> |

Corrigido durante a verificação:
- **Migração:** um nome de produto longo (até 120 caracteres) estourava a coluna de 80 antes do corte. O corte agora é feito na mesma instrução, na V34 e também na V33, que ainda não foi aplicada fora dos testes.
- **Texto:** a mensagem do preço "de" passou a usar as mesmas aspas do rótulo (“de”).

## Limitações

- O painel do navegador estava oculto: os cliques foram feitos pelo DOM e a verificação usou o texto e os atributos da página, sem captura de tela.
- Axe, leitor de tela real e zoom de 200% ficam para a revisão integrada (SC-048).
- O histórico de endereços não tem limpeza automática; a regra de 90 dias será aplicada na leitura, na SC-045.
