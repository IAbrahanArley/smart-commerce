# Revisão de acessibilidade e UX do design system Dashtail (fase 5)

Data: 2026-10-26. Escopo: as mudanças das fases 1 a 4 do design system ([DESIGN.md](../design/DESIGN.md), "Design system Dashtail"):
- admin: moldura, busca global, menu da conta, autenticação, componentes `ui`, gráficos, lista de produtos e pedidos;
- vitrine: card de produto e carregamento.

Objetivo: WCAG 2.2 AA, sem presumir conformidade (AGENTS.md).

## Fontes e método

- **Diretrizes:** [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md) (vercel-labs, `command.md`), obtidas em 2026-10-26 pela skill `web-design-guidelines`, mais os critérios WCAG 2.2 citados abaixo.
- **Revisão de código:** feita nos arquivos do escopo.
- **Navegador:** Chromium do app, com backend sintético local, pelo DOM. Verificações:
  - nomes acessíveis, rótulos, estrutura de títulos e landmarks;
  - teclado na busca (Ctrl+K, setas, Enter, Escape);
  - gaveta móvel e foco;
  - reflow a 320 CSS px, equivalente a zoom de 400% em 1280px (1.4.10);
  - espaçamento de texto (1.4.12);
  - tamanho de alvo (2.5.8).
- **Contraste:** calculado pela fórmula da WCAG para todos os pares de cor dos componentes novos (tabela abaixo).

## Achados e disposição

| Arquivo | Achado | Disposição |
| --- | --- | --- |
| `admin-web/src/components/app-frame.tsx:57` (cabeçalho fixo de 4rem) | O foco poderia ficar escondido sob o cabeçalho fixo (2.4.11) | **Corrigido:** `html { scroll-padding-top: 5rem }` no admin |
| `admin-web/src/components/app-frame.tsx:47` | A sidebar animava `width`, que é propriedade de layout | **Corrigido:** a troca agora é instantânea |
| `admin-web/src/components/ui/dialog.tsx:21` e `global-search.tsx:84` | Gaveta e lista da busca sem contenção de overscroll | **Corrigido:** `overscroll-contain` também na navegação lateral |
| `admin-web/src/components/global-search.tsx:82` e `catalog.tsx:118` | Placeholders sem reticências | **Corrigido:** "Buscar páginas, produtos ou pedidos…" e "Buscar por nome ou SKU…" |
| `admin-web/src/components/global-search.tsx:73` | "Ctrl K" sem espaço inquebrável | **Corrigido** |
| `admin-web/src/components/global-search.tsx:97` | Lista todos os produtos sem limite; um catálogo grande geraria uma lista enorme | **Corrigido:** produtos e pedidos aparecem só a partir de 2 caracteres digitados, até 20 de cada |
| `admin-web/src/components/catalog.tsx:31` | Filtros dos produtos fora da URL: recarregar ou compartilhar perdia o estado | **Corrigido:** `?busca=&categoria=&vitrine=&ordem=`, validados no servidor, com fallback para o padrão e testes no smoke |
| `admin-web/src/components/auth-form.tsx` ("Esqueci minha senha") e `store-detail.tsx:86` | Links isolados com menos de 24px de altura (2.5.8) | **Corrigido:** altura mínima de 24px e 44px, respectivamente |
| Botões do admin | Sem `touch-action: manipulation` e sem destaque de toque intencional | **Corrigido** para `a`, `button`, `select` e `summary`, como já era na vitrine |
| `storefront-web/src/app/globals.css:324` | O card animava `box-shadow` e `border-color` | **Corrigido:** só o `transform` da foto anima e respeita o movimento reduzido. As transições de cor do botão continuam: são curtas e não mexem no layout |
| Links curtos dentro de frases ("Criar conta", "Ver despesas", "Gerenciar categorias") | Menos de 24px de altura | **Aceito:** exceção de alvo em linha de texto da 2.5.8 |
| Texto da busca no cabeçalho | Truncado com reticências em telas estreitas e com espaçamento ampliado | **Aceito:** é um botão com ícone de busca; o atalho e a função continuam claros |
| Lista de produtos | Sem virtualização acima de cerca de 50 linhas | **Aceito no MVP:** os catálogos das lojas iniciais são pequenos. Reavaliar com paginação no backend se algum passar de centenas |
| Títulos e botões | As diretrizes pedem Title Case | **Não se aplica:** em português o padrão é a caixa de frase, como no DESIGN.md |

## Verificações que passaram

- **Reflow a 320 px:** sem rolagem horizontal em `/entrar`, na visão geral, em produtos, no editor de produto e no financeiro.
- **Estrutura de títulos:** um `h1` por página e nenhum salto de nível.
- **Nomes e rótulos:** nenhum botão sem nome e nenhum campo sem rótulo nessas páginas.
- **Busca:** abre com Ctrl+K e põe o foco no campo. As setas e o Enter navegam. Escape fecha a busca.
- **Gaveta móvel:** foco preso, botão "Fechar menu" e fechamento ao navegar.
- **Sidebar recolhida:** cada link mantém o nome (texto `sr-only`) e o tooltip só repete esse nome.
- **Gráficos:** o SVG fica oculto para tecnologia assistiva. A legenda e a tabela ao lado trazem os mesmos números.
- **Carregamento:** `role="status"`, com o contexto para leitor de tela e animação parada com movimento reduzido.
- **Filtros:** a contagem "X de Y produtos" é anunciada (`aria-live`) e as abas de pedidos usam `aria-current`.

## Contraste calculado

| Par | Razão |
| --- | --- |
| texto secundário / card · fundo · hover | 8,33 · 7,82 · 7,25 |
| texto de destaque / hover | 12,41 |
| item atual da navegação (branco / ação) | 8,85 |
| link / card | 8,51 |
| foco / card | 6,50 |
| sucesso / fundo de sucesso | 6,81 |
| erro / fundo de erro | 6,05 |
| borda de controle / card | 4,57 |
| série 2 e deduções do gráfico / card | 5,47 e 4,57 |
| tooltip (branco / texto) | 14,55 |
| texto branco a 80% / painel da marca | 6,32 |

**Bordas e foco:** a borda clara dos cards (1,27) é decorativa. Os controles dentro deles mantêm borda com 3:1 ou mais. O contorno de foco fica fora do elemento, então contrasta com o card ao redor (6,50) e não com o preenchimento do botão.

## Limitações

- **Leitor de tela:** sem teste com NVDA ou VoiceOver reais. A verificação foi feita pelo DOM e pela árvore de acessibilidade.
- **Zoom:** sem zoom real de 200% no navegador. O reflow foi verificado por emulação de largura.
- **Visual:** sem conferência por screenshot, porque a janela do app estava minimizada. O responsável confere no preview dos PRs.
- **Ferramentas:** Playwright e Axe seguem como etapa futura e não foram instalados, conforme o AGENTS.md.
- **Escopo:** telas fora das fases 1 a 4 não foram reauditadas: checkout e pedido da vitrine, e reembolso.
