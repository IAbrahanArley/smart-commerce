# MVP, bloco 2 — Revisão completa da interface (admin)

Data: 2026-10-31. Base: item "Revisão completa da interface" do bloco 2 do [RELEASE_PLAN.md](../product/RELEASE_PLAN.md). A vitrine foi revisada na [SC-048](SC-048-INTEGRATED-REVIEW.md); esta revisão cobre o admin e fecha duas pendências de lá: as dimensões da logo e o aviso de alterações não salvas.

## Fontes e método

- **Diretrizes:** [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md) (Vercel), obtidas em 2026-10-31, mais WCAG 2.2 AA e o [DESIGN.md](../design/DESIGN.md). As regras de "Title Case" não se aplicam: a interface é em português, com caixa de frase.
- **Revisão de código:** por um revisor independente, só de leitura, com o papel de [QA.md](../agents/QA.md). Cobriu as telas fora da [revisão da fase 5](UI-A11Y-REVIEW.md): painel, financeiro, despesas, alertas, assistente, pagamentos, pedido e reembolso, estoque, editor de produto (formulário, fotos, variações, categoria, vitrine), categorias, marcas, conteúdo e página inicial, dados da loja, entrega, parcelamento, lojas e cobrança.
- **Navegador:** Chromium do app, com backend local real (PostgreSQL), conta e loja de teste criadas pelo cadastro, com categorias, marca, dois produtos (simples e com variações) e um banner. As verificações rodaram por script no DOM, em 19 rotas do admin, em 1280 e em 320 CSS px. Cada rota foi checada quanto a:
  - um `h1` e nenhum salto de títulos;
  - todo campo com rótulo e todo botão ou link com nome;
  - `aria-describedby`/`aria-labelledby` apontando para elementos que existem;
  - ids únicos e nenhum `tabindex` positivo;
  - imagens com `alt`, `width` e `height`;
  - alvos de pelo menos 24 px;
  - rolagem horizontal em 320 px (equivalente a 400% de zoom), também com espaçamento de texto ampliado (1.4.12: altura de linha 1,5; letras 0,12em; palavras 0,16em; parágrafos 2em).

## Achados e disposição

| Arquivo | Achado | Disposição |
| --- | --- | --- |
| `store-profile`, `store-fulfillment`, `store-content`, `home-content`, `product-storefront`, `product-category`, `store-categories`, `store-brands` | Formulários sem aviso ao sair com alterações não salvas (pendência da SC-048). Nas seções da página inicial, a ordem e as remoções só valiam localmente e se perdiam ao navegar | **Corrigido:** hook `useUnsavedChanges` aplicado a todos. Ele também substitui as quatro cópias que já existiam (produto, variações, estoque, despesas) e não pergunta em âncora da mesma página, nova aba ou download. No navegador: pergunta uma vez, cancelar mantém a página e o link "Ir para o conteúdo" não pergunta |
| `store-content.tsx` | Trocar a página institucional no select apagava o texto em edição sem aviso | **Corrigido:** diálogo "Trocar de página?"; ao cancelar, texto e página continuam |
| `product-category.tsx` | O select salvava ao mudar e ficava desabilitado com o foco nele; no Chrome para Windows, cada seta no select fechado gravava | **Corrigido:** botão "Salvar categoria", select sempre habilitado (DESIGN.md atualizado) |
| `store-categories.tsx` | A hierarquia só aparecia pelo recuo visual; o leitor de tela ouvia uma lista plana | **Corrigido:** texto oculto com o caminho, por exemplo "Roupas, dentro de Feminino" |
| `product-variants.tsx`, `product-images.tsx` | Erro genérico, sem campo marcado nem foco no campo | **Corrigido:** erro ao lado de cada campo, `aria-invalid` e `aria-describedby`, e foco no primeiro inválido |
| `stores.tsx` | Erros de nome pelo balão nativo, passageiro; nomes das lojas em `h3` logo após o `h1` | **Corrigido:** erros persistentes por campo (inclusive o identificador) e nomes em `h2` |
| `store-expenses.tsx` | O foco do erro seguia a ordem das checagens, não a do formulário; `aria-current` num `strong` | **Corrigido** |
| `store-profile.tsx` e vitrine (`store-frame.tsx`) | Logo sem `width`/`height` (pendência da SC-048) | **Corrigido:** o backend já guardava as dimensões e agora as devolve no perfil do admin e no perfil público (`logoWidth`, `logoHeight`); as duas telas reservam o espaço. Testes: `StoreProfileIT` e smoke da vitrine |
| `home-content.tsx` | Botões de envio de imagem com o mesmo nome em todos os banners; período sem dizer o fuso | **Corrigido:** o nome inclui o banner ("…do banner “Coleção de verão”"); ajuda "Início e fim seguem o horário deste computador" |
| `order-refund.tsx` | Radios em linha, com área de toque pequena | **Corrigido:** `check-field` (44 px) |
| `variant-builder.tsx` | `window.confirm` ao gerar as combinações de novo | **Corrigido:** diálogo do design system |
| `store-advisor.tsx` | Enter envia e Shift+Enter quebra a linha, sem aviso | **Corrigido:** dito na ajuda do campo |
| `store-dashboard.tsx`, `order-detail.tsx` | Contagens sem `Intl` | **Corrigido** |
| `store-installments.tsx` | Sem "Consultar novamente" após falha | **Corrigido** |
| `billing-notice.tsx` | `role="status"` num aviso de quase todas as páginas, anunciado a cada navegação | **Corrigido:** aviso estático |
| `store-alerts.tsx` (navegador) | Link "Ver produtos" com menos de 24 px | **Corrigido:** altura mínima de 24 px |
| `confirm-dialog.tsx` (navegador) | Diálogo fechado com `aria-labelledby` para um título que não existe | **Corrigido:** as referências só existem com o diálogo aberto (o fechado já ficava fora da árvore de acessibilidade) |
| `variant-builder.tsx` | Erro das opções num parágrafo único, sem marcar o campo | **Aceito:** a regra vale para o conjunto das opções (nomes distintos, até 50 combinações), não para um campo só |
| `session-guard.tsx` | Troca o conteúdo por "Verificando sua sessão" a cada mudança de endereço | **Não é achado:** só acontece quando o endereço muda, isto é, na navegação; é o portão de sessão por rota, decisão de segurança |
| Busca do cabeçalho | Texto truncado em 320 px com espaçamento ampliado | **Aceito**, como na [fase 5](UI-A11Y-REVIEW.md) |

## Navegador: resultado

- **Estrutura:** em todas as 19 rotas (lojas, painel, dados, entrega, conteúdo, categorias, marcas, pagamentos, produtos, novo produto, editor simples e com variações, estoque, pedidos, financeiro, despesas, assistente, conta e plano), há um `h1`, nenhum salto de nível e nenhum campo sem rótulo, botão sem nome, id repetido ou `tabindex` positivo.
- **320 px e espaçamento de texto:** nenhuma rolagem horizontal em nenhuma rota.
- **Fluxos conferidos:**
  - erros de criar loja, de variação e de foto, com foco no primeiro campo;
  - aviso de saída nos formulários, que pergunta e mantém a página;
  - diálogo de troca de página, com foco no "Cancelar";
  - categoria salva pelo botão, com mensagem "Categoria “Feminino › Roupas” salva.".
- **Contraste:** nenhum token mudou, então valem os valores da [fase 5](UI-A11Y-REVIEW.md) e da [SC-048](SC-048-INTEGRATED-REVIEW.md).

## Verificação

| Verificação | Resultado |
| --- | --- |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados |
| Vitrine: `lint`, `typecheck`, `build`, `test:smoke` | aprovados; o smoke agora confere `width="240" height="80"` na logo |
| Backend `verify` completo | aprovado: formatação, 115 testes de unidade e 196 de integração (1 ignorado, como antes), BUILD SUCCESS. `StoreProfileIT` confere as dimensões da logo no perfil do admin e no público; `StorefrontIsolationIT` fixa os dois campos novos na projeção pública. Uma rodada anterior falhou porque o Docker estava parado; rodou de novo depois de reiniciado |

## Limitações

- **Teclado real:** o painel do navegador estava oculto, então as teclas não puderam ser enviadas. Foco, ordem e diálogos foram conferidos pelo DOM (`activeElement`) e pelos eventos.
- **Leitor de tela real:** não houve teste com NVDA ou VoiceOver; a conferência foi pela árvore de acessibilidade e pelo DOM.
- **Zoom real de 200%:** não foi aplicado; o reflow foi medido por emulação de largura.
- **Ferramentas:** Axe e Lighthouse seguem sem instalação, conforme o AGENTS.md.
- **Botão Voltar:** o aviso de saída cobre recarregar, fechar e links. O Voltar do navegador e navegações por código (`router.push`) não passam por ele.
- **Fora da conta de teste:** detalhe e reembolso de pedido não tinham dados (exigem pedido pago). Foram revisados só no código.
