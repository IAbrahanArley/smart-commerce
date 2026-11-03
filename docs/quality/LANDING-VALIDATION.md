# Landing e demonstrações — validação local

## Rodada 3 — conteúdo com o escopo do MVP

Data: 2026-11-03. Só conteúdo, dentro da direção visual já aprovada (tokens `--lp-*`, tipografia e componentes existentes; sem dependência nova).

- **Decisão do responsável (2026-11-03):** a landing descreve o escopo de entrega do MVP como disponível, incluindo o que ainda não está no `main`: o restante da Sprint 7 (crediário, contas a receber, livro de vendas, fluxo de caixa projetado — SC-055 a SC-057), a Sprint 9 (Mercado Livre e Shopee), a Sprint 10 (clientes da loja) e a conta do comprador na vitrine. Uma primeira versão com a seção "Em breve" ("Em preparação"/"Em estudo") foi descartada por essa decisão.
- **Condição:** a landing não pode ir para produção antes de esses recursos estarem entregues; o que não entrar no MVP sai da página antes da publicação. As Sprints 9 e 10 ainda são propostas sem aprovação das decisões M1–M12 e R2–R15, e a Shopee depende de cadastro de parceiro com prazo desconhecido.
- **Conteúdo:** hero e recursos citam estoque único para vitrine, balcão e marketplaces; vitrine V1 com conta do comprador; "Mercado Livre e Shopee"; "Balcão e caixa" (contagem cega, sangria, suprimento, venda manual, CSV e crediário). A seção `#operacao` tem nove cartões: financeiro, contas a pagar e a receber, livro de vendas, fluxo de caixa, painel, alertas, clientes, assistente (em liberação gradual) e equipe.
- **Verificado:** `npm run lint`, `typecheck`, `build` e `test:smoke` (com asserção para o escopo do MVP); a primeira versão foi vista no navegador em 653 px e 1440 px sem rolagem horizontal; a grade de nove cartões usa as mesmas regras responsivas.
- **Limites:** a revisão `web-design-guidelines` não foi executada nesta rodada.

## Rodada 2 — direção visual exclusiva da landing

Data: 2026-10-18. O responsável autorizou aplicar a direção da Pacto somente à home `/`, após análise REA da referência. Esta rodada substitui o tratamento visual da home descrito na rodada inicial abaixo, preservando as demonstrações e a plataforma operacional.

### Mudanças e isolamento

- `landing.css`: tokens `--lp-*`, Unbounded/Mona Sans locais, hero escuro com luzes desenhadas em CSS, título em degradê, CTA em cápsula, cabeçalho flutuante e entradas suaves. Todos os seletores estão sob `.landing-page`, exceto o ajuste do skip link condicionado a `body:has(.landing-page)`; fontes/keyframes têm nomes exclusivos.
- `presentation-shell.tsx` e layout público: classe exclusiva quando pathname é `/`; listener passivo de scroll e IntersectionObserver com cleanup. Não altera `:root`, `--sc-*`, globals, CSS base, proxy ou fronteiras de API/tenant.
- `page.tsx`: composição da home com prévia de catálogo separada, títulos mais curtos e revelações. Conteúdo planejado e exemplos continuam explicitamente identificados.
- `presentation-nav.tsx`: remonta estado do menu por pathname, preservando Escape/foco e evitando menu aberto após navegação.
- Fontes abertas hospedadas em `public/fonts/landing`, com licenças OFL. `fontkit` já disponível confirmou Unbounded variável (`wght` 200–900); URLs originais 400/800 forneciam bytes idênticos e foram consolidados em um recurso, faixa CSS utilizada 400–800. Nenhuma dependência instalada.
- Smoke acrescenta presença do wrapper na home e ausência nas demos, galeria e rotas operacionais. `DESIGN.md` v1.3 registra a exceção e seus limites.

Admin, autenticação, galeria, demonstrações e `storefront-web` conservam seus padrões. Prévia `[data-ui]` dentro da home mantém Arial e os tokens operacionais. A landing não consulta API, contexto de tenant, sessão ou dados privados; não houve mudança de backend/banco. Revisão de segurança proporcional: nenhuma fronteira multi-tenant foi alterada, e não foram copiados scripts, analytics, imagens, métricas ou marca da referência.

### Checks executados

No `admin-web`: `npm run lint`, `npm run typecheck`, `npm run build` e `npm run test:smoke`: **aprovados**. `git diff --check`: **aprovado**. Dependências existentes reutilizadas; manifest/lockfile preservados. Build final inclui os ajustes de fontes, títulos e responsividade. O smoke verifica a isolação SSR e continua cobrindo rotas de autenticação, conta, lojas, catálogo, estoque, entrega, headers e 404.

O primeiro build foi bloqueado por EPERM ao criar segmento de prerenderização; reexecução fora do sandbox passou. O primeiro smoke sofreu restrição de conexão de loopback; reexecução fora do sandbox passou. Aviso preexistente `MODULE_TYPELESS_PACKAGE_JSON` permanece. Backend e storefront não foram revalidados por não haver alterações.

### Navegador e inspeção visual

Build de produção servido em `http://127.0.0.1:3002`, sem substituir o servidor de desenvolvimento previamente existente na porta 3000. O servidor antigo mostrou moldura operacional em cache; foi excluído das evidências finais. Inspector da skill executado com Chrome instalado em 1440×900, 768×1024 e 390×844; capturas e métricas finais salvas no artefato local `landing-final/report.json`. Zero overflow horizontal, zero erros de console, fontes Mona Sans/Unbounded carregadas e títulos de no máximo duas linhas nessas larguras.

Reflow adicional em navegador embutido a 320×844: cabeçalho com marca/menu na mesma linha, sem overflow horizontal e ações disponíveis. O inspector, quando recebeu 320px, usou altura padrão inadequada de 200px; suas conclusões de ausência de hero e excesso de altura de preview nesse tamanho foram descartadas e substituídas por essa verificação a 844px. A revisão levou à correção do cabeçalho, títulos finais e rótulos de pelo menos 13px.

Interações verificadas no navegador:

- Home → demo admin → home: classe `.landing-page` removida/reativada; demo em Arial com header estático.
- Alternância para Loja: `aria-pressed=true`, descrição e link atualizados; link leva à demo da loja, em Arial e sem tema da landing.
- Demo da loja → galeria → home: estilos e fontes operacionais preservados nas rotas intermediárias.
- Menu abre/fecha com Escape e retorna foco ao botão.
- Primeiro Tab alcança skip link visível acima do header (`z-index:100`); Enter move foco para `#conteudo`.
- REA em 390×844 com `reduced_motion: reduce`: abertura e PageDown concluídos, conteúdo presente. Evidência `ev_3b82b6cf82a4cb80460055befefea8d974d0f918434f4190f5ee6a0ffc187831`. CSS desliga animações/transições nessa preferência. Entradas não dependem de estado escondido pelo observer; sem observer o conteúdo permanece visível. Não foi executado teste específico com JavaScript desabilitado.

Contraste sRGB calculado: branco/CTA `#3264EF` **4,99:1**; texto secundário `#B4BFD4`/fundo `#050A16` **10,69:1**; texto `#16243B`/claro `#F5F7FC` **14,52:1**; link `#204AA5`/claro **7,58:1**; foco `#90C9FF`/escuro **11,29:1**. Não se presume conformidade de toda a página por esses pares.

### Skills, revisão e decisões editoriais

Aplicadas `design-taste-frontend`, `web-design-guidelines`, `awesome-design-md` e checklist Vercel `react-best-practices`. Context7 confirmou o wrapper cliente com `usePathname`, preservando conteúdo de servidor. Fonte primária de direção: inspeção REA de `sistemapacto.com.br` em 2026-10-18. Índice awesome sem Pacto; referência Linear consultada apenas como apoio de hierarquia marketing/prévia, sem copiar marca ou paleta. A direção já escolhida pelo usuário prevaleceu sobre novo sorteio, proibições editoriais de degradê/vidro e cotas de fotos/vídeos.

O scanner estático editorial foi executado: alertas de sombras, degradê, contornos e divisores são decisões explícitas da referência aprovada; não são erros do ESLint. O inspector final com exceções documentadas encerrou com **0 FAIL e 4 WARN**. Exceções: `gradient`, `gradient-text`, `shadow`, `hairline`, `outlined-box`, `eyebrow`, `mixed-font-headline`, `button-fx` correspondem ao pedido; `emoji` é a marca textual de seleção preexistente na prévia; `few-pictures` preserva interfaces HTML demonstrativas em vez de inventar fotografias. Avisos restantes: cards das previews, Arial da interface além das duas fontes de marketing, escalas de texto da interface e ausência deliberada de vídeo. Não há mídia proprietária ou testemunhos disponíveis que justifiquem introduzir vídeo/stock.

Crítica visual final do coordenador, como avaliação subjetiva da skill: design 8/10, usabilidade 8/10, criatividade 6/10, conteúdo 8/10; ponderado **7,6/10**. Ideia aplicada: catálogo, estoque e vitrine da mesma loja formam uma apresentação conectada, com prévias concretas. Interação central preservada: alternar Admin/Loja. Limite criativo: direção de referência intencional, sem nova identidade própria; a falta de fotografias de produto reais continua explícita. Sem sorteio/seed ou redesenho aleatório nesta rodada.

Revisão independente read-only por subagente reviewer, modelo/esforço herdados: sem achados bloqueadores no código; duplicação de fonte corrigida e confirmada pelo coordenador. Revisão das capturas finais desktop/mobile e encerramento: sem achados visuais relevantes. Coordenador conferiu diff e checks, preservando o trabalho preexistente no checkout.

Diretrizes atuais obtidas em 2026-10-18: [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md). Achados resolvidos: fonte duplicada em `landing.css`, texto pequeno e quebra do header em 320px. Convenções do português e direção aprovada prevalecem sobre preferências editoriais genéricas. Não foram instalados Playwright/Axe nem declarada conformidade WCAG. Leitor de tela, zoom manual 200%/400%, espaçamento de texto e fluxos com backend permanecem fora desta verificação.

## Rodada 1 — apresentação e demonstrações iniciais

Data: 2026-10-18. Escopo: briefing anexado pelo responsável, aplicado ao checkout existente. A home anterior era uma entrada administrativa; não havia galeria nem as rotas de demonstração citadas no briefing. Foram criadas amostras locais preservando os componentes e as telas reais.

## Entrega

- `/`: apresentação com recursos atuais, passos, alternância de prévias Admin/Loja, recursos planejados e chamadas para demonstrações.
- `/admin/produtos`: catálogo sintético, filtro de disponibilidade, navegação operacional separada e itens sem tela identificados como “Em preparação”. Todos os links de produtos levam ao mesmo produto ilustrativo, conforme aviso na página.
- `/loja/produto`: produto sintético sem fotografia, rádios de tamanho e carrinho efêmero por tamanho. Sem pedido, reserva, cálculo financeiro, pagamento ou persistência.
- `/componentes`: nova galeria de fundamentos, ações, estados e campo demonstrativo. Reutiliza `PageHeader`, `StatusBadge`, classes de formulário, botões e foco existentes.
- Grupos `(platform)` e `(presentation)` separam molduras sem mudar URLs. Páginas de autenticação, conta e lojas movidas integralmente; componentes operacionais preservados. A vitrine real continua em `storefront-web`.

Não houve alteração de backend, banco, autenticação, proxy, headers, autorização multi-tenant ou dependências. A apresentação e as demos não importam clientes de API, credenciais, dados privados ou contexto de tenant. Estado e disponibilidade são somente ilustrativos. Nenhum deploy foi realizado.

## Checks executados

No `admin-web`: `npm run lint`, `npm run typecheck`, `npm run build`, `npm run test:smoke`. Aprovados. `git diff --check` aprovado. Dependências existentes reutilizadas; manifests e lockfile preservados.

O sandbox inicialmente impediu HTTP de loopback, fazendo o smoke expirar, e uma rodada final do build encontrou EPERM ao criar segmento de prerenderização. Reexecuções fora do sandbox passaram. O aviso preexistente `MODULE_TYPELESS_PACKAGE_JSON` do smoke continua sem falhar a suíte.

O smoke verifica título novo, h1 único, ausência da moldura operacional na landing, acesso direto às três novas rotas, main/h1 únicos e headers. Mantém os checks de autenticação, conta, lojas, produtos, estoque, entrega e 404. Backend e vitrine não foram revalidados por não terem mudanças.

## Navegador

Build de produção em servidor de loopback e navegador embutido do Codex, sem instalar ferramentas ou bibliotecas:

- Landing revisada em 320, 390, 768 e 1440 CSS px, sem overflow horizontal observado; conteúdos públicos e demos de loja/componentes também conferidos a 320px.
- Menu público abre, fecha com Escape e devolve foco ao botão. Link “Recursos” chega à seção e o próximo Tab segue para “Ver o produto na loja”, sem reiniciar pelo hero.
- Alternância Admin/Loja muda prévia, descrição, estado pressionado e destino real.
- Filtro do admin reduz quatro produtos a três e remove o produto sem estoque.
- Menu da loja demonstrada abre e fecha com Escape, mantendo foco no botão.
- Seleção do tamanho G, inclusão no carrinho e esvaziamento conferidos. O resumo conserva o tamanho escolhido e informa ausência de checkout/pagamento.
- Navegação por links entre landing, admin, loja e componentes conferida. Nenhum envio de formulário ou chamada de negócio faz parte desses controles.

Não há animações ou transições na apresentação; conteúdo e controles não dependem de movimento. Inspeção de código verifica foco herdado, labels, caption/cabeçalhos da tabela, regiões, idioma e hierarquia. Não foi executada avaliação completa com leitor de tela, zoom 200%/400%, espaçamento de texto ou Axe; não se declara conformidade WCAG.

Contraste calculado pela fórmula de luminância sRGB: texto `#172B3A` sobre ação `#00ABE4` = 5,51:1; sobre apoio `#E9F1FA` = 12,77:1; azul profundo `#174E73` sobre branco = 8,85:1. Esses pares não substituem avaliação da página inteira.

## Revisões e fontes

Revisão independente read-only por subagente reviewer (modelo/esforço herdados da sessão; sem override). Comparou as páginas movidas com HEAD e revisou o diff: achado em `presentation-nav.tsx` sobre retorno de foco indevido nos links corrigido; destinos receberam `tabIndex=-1`, retorno ao botão reservado a Escape. Nenhum achado acionável restante.

Skills aplicadas: `design-taste-frontend`, `web-design-guidelines` e checklist `react-best-practices`. Sites consultada somente para fluxo local; sem registro, publicação ou integração externa. Referência visual de marca externa não foi necessária.

Diretrizes atuais obtidas em 2026-10-18: [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md). Idioma, sentence case e identidade aprovados prevalecem sobre convenções editoriais genéricas. Revisão de semântica, foco, controles nativos e links sem achados pendentes no escopo inspecionado.

Direção sorteada: `type-only-statement`, preservando paleta e família. Briefing prevalece sobre sugestões incompatíveis de cores, fotografias, animação e título reduzido. Interfaces HTML sintéticas identificadas substituem fotografias. Scanner estático editorial executado: alertas de bordas e marcas de seleção nas demonstrações foram mantidos por pertencerem à interface e reforçarem seleção além da cor. Não são falhas do ESLint. O inspector CDP da skill não foi executado; inspeção visual e interações usaram o navegador suportado do ambiente. As skills não integram build/CI.

## Limites

As demos não reproduzem cadastro/edição, upload de fotos, ajustes de estoque ou configuração de entrega. Os exemplos não são dados reais nem garantias de disponibilidade. Checkout, pedidos, pagamentos, gestão financeira e IA permanecem planejados. Nenhum contato, cliente, depoimento, plano ou resultado financeiro foi inventado.
