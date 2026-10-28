# ADR-0010 — Vitrine pública com publicação explícita

Status: adotado em 2026-10-17. O responsável autorizou a vitrine e aprovou publicação individual dos produtos.

## Decisão

- Preservar monólito modular, MVC/serviços/repositórios JPA e aplicações Next.js separadas. Vitrine de consulta em storefront-web; admin mantém configuração e dados operacionais. Sem carrinho, checkout, pedidos, pagamentos, upload de imagens ou temas personalizados nesta entrega.
- V8 expande status da loja para DRAFT/PUBLISHED e acrescenta published=false aos produtos existentes e novos. Loja e produto precisam estar publicados simultaneamente. Nenhum catálogo existente é divulgado automaticamente. Ocultar loja oculta todos os produtos; produtos podem ser selecionados antes de publicar a loja.
- PUT /api/tenants/{tenantId}/publication recebe boolean published estrito, validando OWNER, identidade ativa/verificada e locks. PUT /api/tenants/{tenantId}/products/{productId}/publication exige também versão atual; incrementa versão e preserva CAS. Campos de publicação enviados ao cadastro/edição comum não concedem publicação.
- API pública separada: GET /api/public/stores/{slug}?after={uuid} retorna name/slug/products/nextCursor, com páginas de 20. GET /api/public/stores/{slug}/products/{productId} retorna store(name,slug)/product. Produto público contém apenas id/name/price textual/currency/available boolean. Não expor custo, SKU, tenant/IDs internos da loja, empresa, usuários, saldo exato ou histórico.
- Resolver slug exclusivamente entre lojas publicadas; consultas repetem escopo de loja/produto publicado. Rascunho, produto oculto, ID de outra loja e inexistência retornam 404 uniforme. Sem diretório agregador de lojas ou marketplace.
- Cadeia de segurança pública só permite GET, é stateless e não usa o contexto/cookies administrativos. Mutações públicas são negadas; CSRF permanece obrigatório nas mutações administrativas. A cadeia somente de leitura não instala token/sessão CSRF. Respostas públicas e páginas SSR usam no-store; ocultação vale para novas consultas, sem prometer apagar conteúdo já exibido.
  - **Alterado pelo [ADR-0018](0018-storefront-v1.md) V9 (SC-047, 2026-10-28):** as leituras do backend feitas pela vitrine ficam 60 s em cache por loja, e o backend as invalida quando o lojista muda algo. O HTML continua `no-store`, e as fotos por id ficam em cache por 1 dia.
- SSR busca exclusivamente caminhos públicos em origem configurada pelo servidor, sem cookies, Authorization, headers do visitante, redirects ou proxy para endpoints administrativos. Não incluir origem interna em variáveis NEXT_PUBLIC. Em DEV, admin em 127.0.0.1:3000 e vitrine em localhost:3001 separam hosts dos cookies host-only. Em produção, exigir origens/hosts separados e HTTPS em configuração própria antes de disponibilizar externamente.
- Disponibilidade pública é saldo físico >0 nesta etapa sem reservas; saldo exato permanece privado. Checkout futuro deve validar disponibilidade/preço e implementar reservas no backend, nunca confiar na vitrine.
- Interface usa dados reais, fallback visual aprovado e tokens próprios --store-. Sem fotos, descrições, descontos, avaliações, métricas ou botões de compra fictícios. Personalização das lojas exige aprovação futura; admin não herda estilos dos lojistas.

## Verificação e limites

Testar publicação explícita, ocultação, bool estrito, CAS, sessão/CSRF/origem, ausência de dados privados, IDs cruzados, paginação e leitura anônima mesmo com cookie administrativo inválido/revogado. Smoke SSR prova renderização de catálogo/detalhe, falta de encaminhamento de credenciais e 404. CI ganha job storefront; proteção remota não é alterada nesta tarefa.

V8 é aditiva e preserva migrações anteriores; expansão do CHECK de status permite o novo estado sem remover dados. Ferramentas/frontend reutilizam versões já presentes, sem nova biblioteca funcional. SEC-003 permanece aberta e inalterada; CI verde não aceita esse risco. Sem commit/push ou deploy implícitos.
