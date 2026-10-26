# Bloco 1c do MVP — Categorias de produtos: validação

Data: 2026-10-26. Base: bloco 1c do plano de entrega (`docs/product/RELEASE_PLAN.md`) e item 3 do [PRD.md](../product/PRD.md), que pede organizar o catálogo por categorias.

## Comportamento

**Admin, `/lojas/[id]/categorias`** (link "Categorias" no cabeçalho da lista de produtos e no cartão do editor):
- **Lista:** categorias da loja em ordem alfabética, cada uma com a contagem de produtos.
- **Criar:** nome de 1 a 60 caracteres, em uma linha. Espaços nas pontas e espaços repetidos são removidos. O nome é único na loja sem diferenciar maiúsculas ("Camisetas" e "camisetas" conflitam, com 409 e mensagem legível).
- **Renomear:** feito na própria linha, com as mesmas regras.
- **Remover:** pede confirmação e informa quantos produtos ficam sem categoria. Nenhum produto é apagado.
- **Limite:** até 50 categorias por loja. Ao atingir o limite, o formulário fica desabilitado e a API responde 409 `LIMIT`.
- **Hierarquia:** as categorias são planas, sem subcategorias, e cada produto tem no máximo uma.

**Editor do produto:**
- **Cartão "Categoria":** um select com "Sem categoria" e as categorias da loja. A escolha é salva na hora.
- **Versão:** a escolha não altera a versão do produto, então nunca conflita com edições ainda não salvas no formulário principal.
- **Loja sem categorias:** o cartão mostra um link para criá-las.

**Vitrine:**
- **Links de categoria:** "Todos" e cada categoria aparecem acima dos produtos. Só entram categorias que têm produtos publicados, então categorias vazias e nomes de rascunho não aparecem.
- **Filtro:** `?categoria={id}` filtra a lista. A paginação ("Ver mais produtos" e "Voltar ao início da lista") mantém a categoria.
- **Categoria atual:** marcada com `aria-current="page"`.
- **Categoria sem produtos:** mostra "Nenhum produto em {categoria}".
- **Dados recebidos da API:** a vitrine valida cada categoria (UUID e nome) e confere que a categoria devolvida é a pedida. Um id inválido leva à página não encontrada.

**API:**
- `GET` e `POST /api/tenants/{id}/categories`; `PUT` e `DELETE /api/tenants/{id}/categories/{categoryId}`. Todas devolvem a lista atualizada.
- `PUT /api/tenants/{id}/products/{productId}/category`, com `{ "categoryId": uuid | null }`. Devolve o produto.
- Todas exigem sessão do OWNER, e as escritas exigem CSRF.
- Uma categoria ou um produto de outra loja responde 404, e a FK composta impede no banco a ligação entre tenants.
- **Público:** `GET /api/public/stores/{slug}` ganhou `categories` e `category`, além do parâmetro `categoria`.
- **Banco:** migração V30. Precisa entrar depois da V29 do bloco 1b, então o PR do bloco 1b (#39) deve ser mesclado antes.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `CategoryIT` (3) | <ul><li>CRUD com contagem</li><li>conflito sem diferenciar maiúsculas</li><li>nomes inválidos (vazio, longo, com quebra de linha) dão 400</li><li>outra loja recebe 404 sem alterar nada</li><li>CSRF obrigatório</li><li>atribuição, incluindo categoria de outra loja (404)</li><li>filtro e categorias da vitrine só com produtos publicados</li><li>remoção mantém os produtos sem categoria</li><li>limite de 50</li></ul> |
| `StorefrontIsolationIT`, `CatalogIsolationIT` | sem regressão. A projeção pública passou a incluir `categories: []` e `category: null` |
| `mvnw clean verify` (2026-10-26) | 94 unitários e 161 de integração, todos passando (BUILD SUCCESS). O 1 ignorado é a avaliação paga da SC-029, que é opcional |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre a normalização e a validação do nome, o parser da lista, as mensagens de erro e a rota `/categorias` |
| Vitrine: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre os links de categoria, o `aria-current`, o filtro, a paginação dentro da categoria, o fim da lista e o id inválido |
| Navegador (backend sintético) | <ul><li>conflito de nome com mensagem</li><li>criação com nome normalizado</li><li>renomear em linha com foco no campo</li><li>remoção com diálogo e contagem no plural</li><li>escolha no editor salva com mensagem</li><li>vitrine filtrada com a categoria atual marcada</li><li>sem rolagem horizontal em 375px</li></ul> |

Dois problemas encontrados no navegador foram corrigidos antes do commit:
- a mensagem de sucesso mostrava o nome sem normalizar;
- a confirmação dizia "2 produtos continuará".

## Observações

- A busca textual e a ordenação da vitrine continuam fora do escopo do MVP.
- A categoria é escolhida depois de o produto ser criado, no editor. A tela "Novo produto" não mudou.
