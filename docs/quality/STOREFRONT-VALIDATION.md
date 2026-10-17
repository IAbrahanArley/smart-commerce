# Validação local — Vitrine pública de consulta

Data: 2026-10-17. Escopo e contratos: [ADR-0010](../architecture/adr/0010-public-storefront.md). Sem commit, push ou CI remota desta entrega.

## Entrega e segurança

Publicação explícita de loja e de cada produto, com V8 aditiva e produtos ocultos por padrão. A API anônima oferece somente nome/slug da loja e ID/nome/preço/moeda/disponibilidade do produto, com páginas de 20 itens; não oferece custo, SKU, saldo exato, empresa ou identidades. Loja/produto oculto, ID cruzado e inexistência produzem 404 uniforme. Publicação administrativa exige identidade/vínculo, CSRF/origem e versão atual do produto.

`storefront-web` é aplicação Next.js separada, somente de consulta. SSR chama caminhos públicos com origem configurada no servidor, `no-store`, `credentials: omit`, timeout e rejeição de redirects; não encaminha cookies, Authorization ou headers do visitante. Não há proxy para API administrativa nem storage de sessão, recursos remotos, analytics, carrinho ou checkout. Em DEV, navegar na vitrine por `localhost:3001` e no admin por `127.0.0.1:3000`; portas sozinhas não isolam cookies.

## Verificações executadas

- Backend: Maven verify/Spotless com Java 21, PostgreSQL 18.6/Testcontainers; BUILD SUCCESS em 10:55:02 -03:00. 71 testes aprovados: 26 unitários/arquiteturais e 45 integrações, sem falhas, erros ou ignorados. Log local ignorado: `backend/target/storefront-verify.log`. A suíte inclui publicação/ocultação, campos públicos, sessão/CSRF/origem, IDs cruzados, concorrência e paginação.
- Vitrine: `npm install --package-lock-only --ignore-scripts`, `npm ci --ignore-scripts --no-audit --no-fund`, `npm run lint`, `npm run typecheck`, `npm run build` e `npm run test:smoke` executados e aprovados. Lockfile derivado do admin: nenhuma diferença nas versões das dependências já presentes. Next.js 16.4.1, React 19.3.1, TypeScript 5.9.3; sem biblioteca funcional nova. npm reportou ESLint 9 fora de suporte e o smoke emitiu aviso benigno `MODULE_TYPELESS_PACKAGE_JSON`, sem falha.
- Smoke da vitrine: aplicação de produção em 3002 com upstream sintético efêmero. Verifica home neutra, catálogo/detalhe SSR, preço exato inclusive limite de 17 dígitos, paginação, vazio, indisponibilidade, ausência/rotas administrativas, headers de privacidade, projeção de campos públicos e ausência de Cookie/Authorization no upstream mesmo quando enviados pelo visitante. Não altera banco nem usa conta real.
- Rotas ausentes fixas retornaram HTTP 404. Em páginas SSR com loading/streaming, Next pode iniciar HTTP 200 antes de resolver `notFound`; o smoke verifica a mesma página de ausência para loja/produto indisponível. Não confundir esse comportamento da página com o 404 uniforme da API.
- Nenhuma referência a `STOREFRONT_API_ORIGIN`, origem interna local ou caminhos de busca da API pública foi encontrada nos bundles estáticos do browser.
- Admin: lint, typecheck, build e smoke aprovados, incluindo os controles de publicação. `actionlint` aprovou o workflow com o novo job storefront. `git diff --check`, verificação de whitespace em arquivos novos e 48 links locais passaram. Gitleaks não encontrou segredos no conjunto local de alterações (inventário e vitrine); comparação silenciosa com credenciais locais também passou. Nenhum arquivo staged, commit ou push.

## UI/UX e limitações das skills

Identidade aprovada preservada: Arial, azul profundo, superfícies claras e raios 4/8px. Tokens `--store-*` separados dos `--sc-*` da plataforma; sem infraestrutura de temas ou CSS arbitrário. Revisão React: renderização no servidor, sem duplicar busca no cliente, sem efeitos/estado global de requisição. Web Interface Guidelines consultadas em 2026-10-17: https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md. Links nativos, nomes acessíveis, skip link, foco visível, alvos de 44px e estados vazio/erro foram revisados em código.

`design-taste-frontend` foi consultada e executada com `--keep palette,type`. Direção sorteada: contact-sheet, seed 613615704; adaptação limitada à lista tipográfica de dados reais. Lint da skill: 11 arquivos, zero FAIL/WARN. Inspector executado em fixture sintética isolada (3003), larguras 1440/768/390/320; capturas desktop/tablet/mobile foram abertas. Resultado editorial: **3 FAIL** (`few-pictures`, `focal-point`, `still-page`) e **1 WARN** (`no-footage`). Não há aprovação integral da skill. Fotos/vídeos, animações obrigatórias e tipografia promocional conflitam com o escopo e design aprovados; não foram adicionados para satisfazer cotas. Não foi apontado overflow horizontal pelo inspector. Em 320, o próprio script usa altura de 200px e gera folhas de captura; revisão de navegador real complementa essa limitação.

Axe, leitor de tela e avaliação WCAG completa não executados; nenhuma declaração de conformidade integral. Não foram instalados Playwright/Axe. `awesome-design-md` não foi aplicada: não houve nova direção visual ou mudança de tokens a pesquisar.

## Navegador com API real

Coordenador confirmou publicação explícita do produto e da loja pela interface administrativa. A vitrine real em `localhost:3001` exibiu o catálogo da loja de demonstração, preço de R$ 54,90 e disponibilidade. Consulta HTTP pública com cookie administrativo malformado permaneceu anônima, retornou 200, sem Set-Cookie e com no-store.

Navegação lista → detalhe e retorno confirmados no navegador real. Selecionar produto com loja oculta manteve a página indisponível; publicar a loja liberou a consulta. Ocultar o produto tornou seu detalhe indisponível na próxima atualização; republicá-lo restaurou a lista. A fixture sintética ficou publicada para demonstração local. Nenhuma loja real foi publicada externamente.

Capturas reais de lista e detalhe revisadas em 320 CSS px, sem rolagem horizontal (clientWidth e scrollWidth iguais a 320); lista desktop revisada em 1440px. Viewport restaurado após a inspeção. Evidência local ignorada: `.tools/storefront-desktop.png`. Não há SKU, custo, saldo exato nem controles de compra nessas páginas.

## Pendências

Contribuições: backend implementou publicação e projeções JPA; frontend integrou controles administrativos; arquitetura/frontend construiu a aplicação pública e revisou SSR, design e skills; segurança revisou isolamento, campos permitidos, sessões, CSRF e concorrência sem bloqueador identificado. Coordenador executou integração PostgreSQL, testes de isolamento, fluxo real no navegador e verificações finais. A revisão de QA foi conduzida pelo coordenador conforme checklist, com as limitações de acessibilidade acima.

Execução remota dos jobs backend/admin/storefront após autorização de publicação; eventual exigência do novo check na proteção da branch depende de configuração remota própria. Antes de disponibilização externa: hosts separados e HTTPS, além das pendências de produção já registradas. Carrinho, reservas, pedidos e pagamentos não iniciados.

SEC-003 permanece aberta e inalterada, sem aceite formal ou automático de risco. A vitrine reutiliza a cadeia de desenvolvimento existente; verificações verdes não encerram o achado. Benchmark de senhas preservado, sem repetição.

## Refinamento visual — 2026-10-17

Autorizado pelo responsável após a entrega funcional. Cabeçalho branco, abertura azul com nome real da loja, preço e disponibilidade em destaque, detalhe em duas áreas e rodapé explicativo. Componentes locais `Arrow` e `Availability`; sem novas dependências, imagens fictícias ou alteração de contrato/API. Paleta, Arial e raios existentes preservados; composição registrada no DESIGN.md. A comparação com a versão anterior confirmou que manifest, lockfile, parser monetário e cliente público SSR permanecem idênticos.

Skills: `design-taste-frontend` com direção split-stage, seed 2021451411 e palette/type preservados; composição adaptada ao catálogo tipográfico sem fotografias cadastradas. `awesome-design-md`: índice e análise IBM consultados somente para hierarquia e superfícies, na revisão fixada já registrada no DESIGN.md; nenhuma identidade de marca copiada. `web-design-guidelines`: fonte oficial consultada novamente em 2026-10-17. Revisão React preservou Server Components, busca única no servidor e ausência de dependências novas.

Frontend implementou sete arquivos de interface; QA independente revisou semântica, foco, quebra de nomes/preços, navegação honesta e separação SSR, sem bloqueadores de código. Coordenador conferiu lista/detalhe em desktop e 320px, navegação por Tab/Enter e skip link levando foco a `conteudo`. Na primeira inspeção encontrou texto corrido do rodapé a 14px e overflow de 15px causado por `html min-width:320px` com scrollbar clássico; ambos corrigidos. Nomes de 120 caracteres, preço máximo e ampliação manual de texto não foram exercitados no navegador nesta revisão: quebra defensiva foi revisada em código, sem alegar cobertura manual desses extremos.

Após as duas correções, lint, typecheck, build e smoke da vitrine passaram novamente. Reteste real do detalhe com viewport de 320px e scrollbar: largura útil 305px e scrollWidth 305px, sem overflow; texto corrido do rodapé confirmado em 16px. Capturas finais de desktop 1440, tablet 768 e celular 390 foram abertas e revisadas; viewport restaurado no navegador. Logs e capturas são artefatos locais ignorados.

Taste lint: 13 arquivos, zero FAIL/WARN. Inspector final: 3 FAIL editoriais (`few-pictures`, `focal-point`, `still-page`) e 1 WARN (`no-footage`), permanecendo as limitações já justificadas de fotos, tipografia aprovada e animações. O achado adicional `body-size` da primeira rodada foi corrigido. Não há aprovação integral do gate da skill; crítica visual final registrada localmente, nota ponderada 7,2 com teto editorial 6,0 aplicado. Não foram incluídos recursos fictícios para satisfazer a métrica. `git diff --check`, whitespace/48 links locais e Gitleaks passaram no conjunto local de alterações, sem correspondência com credenciais locais.

Arquivos desta revisão: `storefront-web/src/app/globals.css`, `layout.tsx`, `lojas/[slug]/page.tsx`, `lojas/[slug]/produtos/[productId]/page.tsx`, `src/components/store-header.tsx`, `arrow.tsx`, `availability.tsx`, este relatório e `docs/design/DESIGN.md`.

Sem alteração de backend, autenticação, publicação ou SEC-003 nesta revisão visual; testes de backend não repetidos. CI remota, Axe, leitor de tela e avaliação WCAG completa continuam pendentes. Sem commit ou push.
