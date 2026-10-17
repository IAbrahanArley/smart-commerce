# Vitrine pública

Aplicação Next.js separada do admin, somente para consulta de produtos publicados. Sem carrinho, checkout, login de comprador ou personalização de tema nesta entrega.

Com Node.js 24 e npm 11, execute nesta pasta:

```powershell
npm ci
npm run dev
```

Abra `http://localhost:3001/lojas/SLUG-DA-LOJA`. O processo escuta somente em `127.0.0.1:3001`. Use `localhost` na navegação pública e `127.0.0.1` no admin local para separar cookies por host; portas, sozinhas, não isolam cookies. Em produção, os hosts e HTTPS exigem configuração específica antes de publicar.

O backend deve estar em execução. `STOREFRONT_API_ORIGIN` configura sua origem apenas no servidor; o padrão local é `http://127.0.0.1:8080`. Não usar prefixo `NEXT_PUBLIC_`. Não há proxy para APIs administrativas, encaminhamento de cookies/Authorization, analytics, fontes remotas nem recursos externos.

Rotas públicas:

- `/`: orientação neutra, sem lista agregada de lojas.
- `/lojas/[slug]`: produtos publicados e paginação por cursor.
- `/lojas/[slug]/produtos/[productId]`: nome, preço e disponibilidade.

A consulta SSR usa somente `/api/public/stores/...`, `no-store`, timeout e rejeição de redirecionamentos. O HTML projeta exclusivamente campos públicos. Disponibilidade é informativa, sem reserva de estoque. Store/produto indisponível retorna a mesma página de ausência; com streaming, Next pode iniciar a resposta 200 antes de resolver `notFound`.

Verificações:

```powershell
npm run lint
npm run typecheck
npm run build
npm run test:smoke
```

O smoke inicia a aplicação de produção em `127.0.0.1:3002` e upstream sintético efêmero. Verifica rotas, paginação, estados, valores e que credenciais do visitante não chegam ao backend. Não requer conta, não altera banco e não substitui a revisão no navegador com a API real. Use `SMOKE_PORT` para alterar a porta isolada.

Tokens `--store-*` usam os valores de fallback do design aprovado, separados de `--sc-*` do admin. Não existe infraestrutura de temas nem CSS arbitrário de lojistas.
