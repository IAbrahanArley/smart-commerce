# SC-043 — Aparência e conteúdo da loja: validação

Data: 2026-10-27. Base: SC-043 da [SPRINT_08.md](../product/SPRINT_08.md) e as decisões V1, V8 e V13 do [ADR-0018](../architecture/adr/0018-storefront-v1.md). Inclui as **categorias adicionais por produto**, pendência da SC-041. A branch parte da SC-044/045. Os desvios em relação ao texto da sprint estão no ADR, em "Implementação e desvios registrados".

## O que mudou

- **Categorias adicionais (V37):**
  - no admin, o cartão de categoria do produto ganhou "Também aparece em", com até 10 categorias;
  - na vitrine, o produto aparece no filtro e nas contagens de cada uma delas, contado uma vez só;
  - a principal continua definindo o caminho e os relacionados.
- **Empresa e páginas (V38):** nova tela **"Aparência e conteúdo"** no admin, só para o OWNER. Ela reúne:
  - faixa de aviso;
  - razão social e CNPJ, com os dígitos verificadores conferidos no admin e no backend;
  - telefone e endereço;
  - Facebook, TikTok e YouTube;
  - cinco páginas institucionais, com o mesmo editor e o mesmo sanitizador da descrição de produto.

  Na vitrine, a faixa aparece no topo, os dados ficam no rodapé e as páginas ficam em "Ajuda" e em `/lojas/{loja}/paginas/{página}`.
- **Home (V39):**
  - **Banners:** até 5. Cada um tem descrição obrigatória, título opcional, link só interno (categoria, produto ou busca), período opcional, imagem principal e imagem de celular. A ordem se muda com "Subir" e "Descer".
  - **Seções:** até 8, de tipos fechados: lançamentos, mais vendidos, destaques ou uma categoria.
  - **Destaque:** marcado por produto no cartão "Vitrine".
  - **Vitrine:** carrossel com anterior, próximo e pausa. Ele troca sozinho a cada 7 s, mas não com movimento reduzido nem com o ponteiro ou o foco dentro dele. Depois vêm as seções, cada uma com até 8 produtos e "Ver todos"; sem nenhuma configurada, a home mostra "Lançamentos".
- **API:**
  - rotas do OWNER em `/api/tenants/{t}/content/...`: empresa, páginas, banners com ordem e imagens, e seções;
  - o catálogo público devolve `company`, `pages`, `banners` e `sections` e aceita o filtro `destaque=1`;
  - rotas públicas novas: `GET /api/public/stores/{loja}/pages/{página}` e `GET /api/public/stores/{loja}/banners/{id}/images/{desktop|mobile}`.

## Segurança e isolamento

- **Tenant:**
  - toda escrita e leitura do admin passa por `lockOwnedStore`: o tenant vem da sessão, e o papel tem que ser OWNER;
  - banner, categoria ou loja de outro tenant dá o mesmo 404 que um inexistente;
  - as FKs compostas com `tenant_id` recusam categoria de outro tenant em seção e em categoria adicional.
- **Público:** só a loja publicada e só o que está ativo. Banner sem imagem ou fora do período não aparece na lista, e a imagem dele responde 404.
- **Conteúdo não confiável:**
  - HTML das páginas limpo no backend (7 tags, sem atributos) e conferido de novo na vitrine;
  - links de banner só internos: o alvo é um endereço (`[a-z0-9-]`) ou um texto de busca, e a vitrine monta a URL;
  - redes gravadas como nome de usuário, nunca como URL;
  - a vitrine descarta qualquer campo fora do formato.
- **Upload:**
  - multipart só nas rotas listadas no `IdentityRequestFilter`, com CSRF no cabeçalho;
  - imagem reencodada pelo normalizador, sem metadados, com até 5 MiB e pelo menos 300 px no menor lado;
  - as respostas de imagem levam `nosniff` e `no-store`.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `HomeContentIT` (2) | aprovado. Cobre: <ul><li>limite de 5 (409 `BANNER_LIMIT`)</li><li>descrição obrigatória, link externo e período invertido recusados (400)</li><li>ordem completa obrigatória, reordenar e remover</li><li>upload principal e de celular, variante inválida, arquivo que não é imagem</li><li>outro tenant com 404 em listar, editar, remover, imagem e upload</li><li>público só depois de publicar, só com imagem e dentro do período, imagem de outra loja com 404</li><li>período encerrado esconde o banner e a imagem</li><li>seções: categoria obrigatória só no tipo categoria, categoria de outro tenant com 404, seção de categoria oculta omitida</li><li>destaque pelo cartão "Vitrine" e filtro `destaque=1` (valor inválido dá 400)</li></ul> |
| `StoreContentIT` (2), `CompanyInfoTest` (3) | aprovados: CNPJ inválido recusado; script, `on*` e `javascript:` removidos; página vazia apagada; outro tenant com 404; público só da loja publicada |
| `CategoryIT`, `PublicCatalogIT`, `CatalogQueryTest` | aprovados com as categorias adicionais: filtro e contagem sem duplicar |
| `ArchitectureTest` | aprovado (sem JDBC em controllers e serviços) |
| Backend `verify` completo | aprovado: formatação, 115 testes de unidade e 189 de integração (1 ignorado, como antes), BUILD SUCCESS |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados; helpers de banner, seção, período, ordem e mensagens no smoke da Sprint 8 |
| Vitrine: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre: <ul><li>banner com imagem principal e de celular, descrição e link para a categoria</li><li>banner com link externo descartado</li><li>seções na ordem, com "Ver todos" dos destaques</li><li>seção que falha omitida sem derrubar a home</li><li>proxy da imagem com 404 para id inválido</li><li>parser da home nos testes de unidade</li></ul> |
| Navegador (backend sintético, 1280 e 375 px) | <ul><li>carrossel com "1 / 2", anterior e próximo</li><li>"Pausar" virando "Reproduzir"</li><li>`aria-live` desligado durante a troca automática e "polite" com ela parada</li><li>só um slide visível, rotulado "N de M"</li><li>seções na ordem configurada</li><li>sem erros da aplicação no console</li></ul> |

Corrigido durante a verificação:
- **Banner muito alto:** com imagem quadrada, ocupava a tela inteira; agora tem altura máxima de `min(70vh, 36rem)`.
- **Movimento reduzido:** o botão de pausa aparecia mesmo sem troca automática; agora some.

## Limitações e pendências

- Sem teste de navegador com backend real e sessão autenticada no admin; o admin foi verificado por `lint`, `typecheck`, `build` e smoke. A revisão completa de acessibilidade (Axe, leitor de tela, zoom) é da SC-048.
- O período usa o fuso do navegador do lojista, não um fuso fixo de São Paulo; o teste "período no fuso de São Paulo" e o "A/B" da sprint não foram feitos.
- Banners e seções vêm em toda resposta do catálogo, e cada seção faz uma consulta a mais na home; o cache entra na SC-047.
- Antes do deploy, continua pendente confirmar que a extensão `unaccent` (V35) pode ser criada no PostgreSQL hospedado.
