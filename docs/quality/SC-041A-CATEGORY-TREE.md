# SC-041 (parte 1) — Categorias em árvore: validação

Data: 2026-10-27. Base: SC-041 da [SPRINT_08.md](../product/SPRINT_08.md) e decisão V1 do [ADR-0018](../architecture/adr/0018-storefront-v1.md). Esta é a primeira fatia da SC-041. Marcas, endereço do produto e preço "de" vêm nas próximas, na mesma tarefa.

## Comportamento

**Banco (V33, aditiva):**
- `catalog_category` ganha `parent_id`, `slug`, `position` e `published`.
- A FK composta `(parent_id, tenant_id)` impede um pai de outro tenant, e um CHECK impede a categoria de ser pai de si mesma.
- O nome passa a ser único **entre irmãs**, então "Camisetas" pode existir em Feminino e em Masculino.
- O `slug` é único na loja.
- As categorias existentes viram raiz, visíveis, em ordem alfabética. O slug vem do nome, sem acentos; quando dois nomes geram o mesmo slug, o segundo ganha o início do id.

**Regras (serviço, com todas as categorias do tenant bloqueadas):**
- No máximo **3 níveis**. Criar no 4º nível ou mover uma subárvore para onde ela passaria de 3 níveis responde 409 `DEPTH`.
- Mover uma categoria para dentro dela mesma ou de uma filha responde 409 `CYCLE`.
- Remover uma categoria que tem subcategorias responde 409 `HAS_CHILDREN`. Sem subcategorias, a remoção continua como antes: os produtos ficam sem categoria.
- Renomear **não** muda o endereço. O endereço é editado à parte: 409 `SLUG_CONFLICT` quando outra categoria já usa, 400 quando o formato é inválido.
- Ocultar a categoria tira ela do menu e do filtro da vitrine, mas os produtos continuam visíveis. A ocultação exige booleano JSON estrito.
- Limite de 50 categorias por loja mantido.

**API (OWNER, CSRF nas escritas):**
- `POST /categories` aceita `parentId`.
- Novas rotas: `PUT /categories/{id}/slug`, `PUT /categories/{id}/placement` (`parentId` e `position`) e `PUT /categories/{id}/publication`.
- A lista volta em ordem de árvore (pai antes das filhas, irmãs por posição), com `slug`, `parentId`, `depth`, `position` e `published`.
- Categoria ou pai de outra loja: 404 uniforme.

**Vitrine pública:** categorias ocultas saem da lista e o filtro por elas não traz produtos. A navegação em árvore na vitrine fica para a SC-042.

**Admin, `/lojas/[id]/categorias`:**
- Lista recuada por nível, com contagem de produtos, endereço e selo "Oculta na vitrine".
- Ações por categoria: Subir, Descer, Editar, Ocultar/Mostrar e Remover.
- Editar reúne nome, endereço (com aviso de que mudar quebra links compartilhados) e "Dentro de". O seletor só oferece destinos válidos (sem ciclo e sem passar de 3 níveis).
- Criar ganhou o seletor "Dentro de".
- O editor do produto e o filtro da lista de produtos mostram o caminho completo ("Feminino › Roupas › Camisetas").

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `CategoryTreeTest` (6, unitário) | profundidade, altura, ciclo, ordem pai-antes-de-filhas, posicionamento com limite, geração e validação de slug |
| `CategoryIT` (5, PostgreSQL real) | <ul><li>árvore de 3 níveis, 4º recusado, mesmo nome em pais diferentes com slug próprio, nome repetido entre irmãs recusado</li><li>ciclo e profundidade ao mover; mover leva a subárvore</li><li>remoção com subcategorias recusada</li><li>slug: conflito, formato inválido e troca</li><li>outra loja: 404 em criar com pai alheio, mover, ocultar e trocar slug</li><li>categoria oculta fora da vitrine com o produto ainda público; booleano estrito</li><li>testes do bloco 1c continuam passando</li></ul> |
| Migração V33 sobre dados existentes | PostgreSQL descartável (mesma imagem dos testes), com "Óculos & Viseiras", "Calçados", "Calcados!" e "???": slugs `oculos-viseiras`, `calcados`, `calcados-a0000000` e `categoria`, posições 0–3, todas visíveis |
| `mvnw verify` completo (JDK 21, 2026-10-27) | formatação, 107 unitários e 172 de integração passando (BUILD SUCCESS). O 1 ignorado é a avaliação paga da SC-029, opcional |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre o novo formato da lista, o caminho, os destinos válidos ao mover, a validação do endereço e as mensagens novas |
| Navegador (API sintética local, 1024 px e 375 px) | <ul><li>árvore recuada por nível</li><li>"Dentro de" sem o 3º nível na criação e só com raízes ao mover uma categoria que tem filha</li><li>endereço inválido: mensagem, `aria-invalid` e foco no campo</li><li>mover para o topo com troca de endereço, levando a filha junto</li><li>remover com subcategorias: alerta sem abrir o diálogo</li><li>Subir e Mostrar com mensagem de status</li><li>375 px sem rolagem horizontal</li></ul> |

Corrigido durante a verificação no navegador: o selo "Oculta na vitrine" ficava colado ao nome para leitores de tela ("OutletOculta").

## Limitações

- A captura de tela no navegador não foi possível (janela em segundo plano); a verificação usou o DOM e o texto da página.
- Axe, leitor de tela real e zoom de 200% não foram executados; ficam para a revisão integrada (SC-048).
- Mover por arrastar não existe; a ordem muda com Subir e Descer.
