# Smart Commerce — Design system

Versão: 1.4 · Atualizada em 2026-10-20. Direção escolhida pelo responsável: **profissional e confiável, com azul profundo, fundo claro e visual simples, evoluindo o padrão existente**. Este é o padrão da plataforma para as entregas autorizadas, com a exceção exclusiva da landing `/` descrita na governança abaixo.

Este documento é a fonte oficial das decisões de design do Smart Commerce, subordinada ao escopo do [PRD](../product/PRD.md), à [Sprint 1](../product/SPRINT_01.md), à [arquitetura](../architecture/ARCHITECTURE.md) e aos ADRs aplicáveis. Decisões funcionais e de segurança da autenticação pertencem ao [ADR-0005](../architecture/adr/0005-identity-authentication.md), cujas D1–D6 foram aprovadas separadamente. Este documento não amplia esse escopo.

## Identidade oficial da plataforma

A plataforma atende pequenos varejistas brasileiros. A direção é uma interface operacional legível, com hierarquia clara, ações específicas e feedback previsível em português brasileiro. Não inventar métricas, depoimentos ou dados financeiros para preencher uma interface.

O bootstrap do admin já utiliza fundo cinza claro, superfícies brancas, texto azul escuro, ação azul e foco castanho. Essa identidade é preservada. Arial/Helvetica/sans-serif continua a família inicial; não há adoção de fonte externa, logotipo novo, tema escuro ou identidade de marca de referência.

Os valores abaixo são a referência oficial. Um subconjunto já existe como variáveis em `admin-web/src/app/globals.css`; os demais entram somente quando houver consumidor. A prancha [IDENTITY.html](IDENTITY.html) ilustra este documento e não substitui seus contratos. Pares de contraste devem ser verificados no contexto real; paleta isolada não certifica acessibilidade.

## Assinatura, personalidade e linguagem

- **Nome:** Smart Commerce, com espaço e iniciais maiúsculas. Preservar a grafia em títulos, cabeçalho, mensagens e documentação. Não usar SmartCommerce ou SC como marca visível ao usuário; identificadores técnicos existentes não precisam ser renomeados.
- **Assinatura atual:** nome em texto, Arial/Helvetica/sans-serif, peso 700, azul da plataforma sobre superfície clara. Quando for link, preservar a identificação de navegação e seu foco. Não há símbolo ou logotipo gráfico aprovado.
- **Personalidade:** confiável, clara e próxima do pequeno varejista. A interface transmite organização por hierarquia, alinhamento e feedback previsível; a tecnologia aparece na utilidade das funções.
- **Linguagem:** português brasileiro, frases curtas, termos familiares e ações específicas. Preferir “Entrar”, “Criar conta”, “Salvar alterações” e “Sair da conta”. Erros explicam como continuar; mensagens sensíveis continuam genéricas conforme o ADR.
- **Conteúdo:** não prometer lucro, segurança absoluta, certificações ou resultados automáticos. Não exibir métricas fictícias como dados reais. Estados vazios explicam a ausência de conteúdo e a próxima ação disponível.

## Composição por superfície

| Superfície | Padrão |
| --- | --- |
| Autenticação | Fundo claro, painel branco de uma coluna com largura próxima de 28rem, título direto e ação principal evidente. Sem conteúdo promocional competindo com o formulário. |
| Admin | Superfícies claras, títulos e ações consistentes, dados alinhados e densidade legível. Azul destaca ações e informação; espaço e bordas organizam grupos. Telas de uma loja e da conta usam a moldura com navegação lateral, limitada a funções existentes. Não antecipar itens de menu ou dashboards sem funcionalidade. |
| Páginas institucionais futuras | Preservam a assinatura e os tokens; composição própria depende do escopo aprovado e da skill adequada. |
| Vitrine | Base padrão V1 aprovada em 2026-10-27 (seção "Vitrine padrão V1"). Temas dos lojistas têm contrato separado; nome, cores e conteúdo da loja não alteram admin ou autenticação. |

Manter os raios de 4px/8px e a geometria simples existentes. Gradientes decorativos, vidro translúcido, sombras intensas, botões em cápsula, fontes externas, tema escuro e novas marcas gráficas não fazem parte deste padrão. Eventual adoção exige proposta e aprovação, sem instalação automática de biblioteca.

## Tokens semânticos de cor

Componentes devem consumir papéis semânticos, não valores hexadecimais dispersos. Primitivas de cor, quando necessárias, ficam separadas do significado. Prefixo `--sc-` pertence à plataforma.

| Token semântico de referência | Valor | Papel |
| --- | --- | --- |
| `--sc-color-background` | `#F6F8FA` | Fundo principal. |
| `--sc-color-surface` | `#FFFFFF` | Formulários e superfícies. |
| `--sc-color-text` | `#172B3A` | Texto principal. |
| `--sc-color-text-secondary` | `#365165` | Texto secundário legível. |
| `--sc-color-action` | `#174E73` | Ação principal. |
| `--sc-color-action-hover` | `#113C59` | Hover da ação principal. |
| `--sc-color-action-pressed` | `#0B2D44` | Ação pressionada. |
| `--sc-color-on-action` | `#FFFFFF` | Texto sobre ação principal. |
| `--sc-color-link` | `#124F82` | Links com identificação além da cor. |
| `--sc-color-focus` | `#924B00` | Indicador de foco em superfícies claras. |
| `--sc-color-divider` | `#CCD5DD` | Separação decorativa; não identifica sozinho um controle. |
| `--sc-color-control-border` | `#657888` | Limite perceptível de campos e controles. |
| `--sc-color-disabled-background` | `#E2E8F0` | Fundo de controle desabilitado. |
| `--sc-color-disabled-text` | `#365165` | Rótulo legível de controle desabilitado. |
| `--sc-color-selected-background` | `#EAF0F5` | Seleção, acompanhada de indicador semântico/visual adicional. |
| `--sc-color-success` / `--sc-color-success-background` | `#166534` / `#F0FDF4` | Resultado positivo com texto explícito. |
| `--sc-color-warning` / `--sc-color-warning-background` | `#854D0E` / `#FFFBEB` | Atenção e próximo passo. |
| `--sc-color-danger` / `--sc-color-danger-background` | `#B42318` / `#FEF3F2` | Erro ou ação destrutiva identificada por texto. |
| `--sc-color-info` / `--sc-color-info-background` | `#174E73` / `#EAF0F5` | Informação contextual. |

Não usar divisor decorativo como única borda de campo. O foco deve contrastar com as superfícies adjacentes; o token inicial não é universal para fundos escuros ou personalizados. Não reduzir opacidade do componente inteiro sem conferir a legibilidade de seus estados.

## Tipografia, espaçamento e geometria

| Categoria | Decisão inicial |
| --- | --- |
| Família | Arial, Helvetica, sans-serif; sem download externo. |
| Escala | Notas 14px; corpo/campos 16px; destaque 18px; títulos 20, 24 e 32px, implementados em unidades relativas. |
| Entrelinha | Corpo aproximadamente 1,5; títulos aproximadamente 1,2, sem corte ao ampliar texto. |
| Pesos | Regular 400 e destaque 700; hierarquia não depende apenas do peso. |
| Dados numéricos | Algarismos tabulares em colunas/comparações; apresentação por Intl com moeda/fuso explícitos conforme contrato, sem cálculos financeiros em ponto flutuante no browser. |
| Espaçamento | Escala de 4, 8, 12, 16, 24, 32, 48 e 64px, representada por tokens de espaçamento. |
| Bordas | 1px para controles/divisores; função determina cor e contraste. |
| Raios | 4px em controles; 8px em superfícies; outros raios exigem necessidade documentada. Exceções documentadas: círculo do avatar de iniciais e dos marcadores (selo, etapas, linha do tempo) e contador do carrinho arredondado. |
| Foco | Contorno inicial de 3px e afastamento de 3–4px, visível sem ser encoberto. |
| Elevação | Cards e superfícies usam a sombra sutil `--sc-shadow-surface`/`--store-shadow-surface` junto da borda de 1px. Sobreposições (menu de conta, diálogo) usam `--sc-shadow-overlay`. Sem sombras intensas. |

Usar texto em sentence case natural ao português brasileiro, rótulos de ação específicos e hierarquia semântica de títulos. Não impor o limite editorial de duas linhas da skill de páginas públicas ao admin ou aos formulários.

## Componentes e estados

Reutilizar componentes locais no admin conforme surgirem consumidores reais; não criar catálogo vazio, pacote compartilhado ou kit de componentes nesta etapa. HTML nativo e CSS existente são a primeira opção. Componentes complexos devem justificar eventual dependência em tarefa específica.

| Componente/padrão futuro | Comportamento esperado |
| --- | --- |
| Botão | Elemento button para ação; variante principal/secundária/destrutiva por função; rótulo específico; carregamento perceptível sem alteração brusca de tamanho. |
| Link | Navegação real, nome compreensível e identificação além da cor; preserva comportamentos nativos do browser. |
| Campo | Label persistente, instrução/erro associados, tipo e autocomplete adequados; placeholder não substitui label. |
| Alerta/status | Texto claro com próximo passo; erro/sucesso não dependem só da cor; anúncio acessível adequado à urgência, sem repetição excessiva. |
| Layout | Landmarks, skip link quando aplicável, ordem de leitura/foco previsível e navegação móvel acessível. |
| Conteúdo vazio | Explica a ausência e a ação possível; não preenche a tela com dados fictícios. |
| Tabela, quando necessária | Semântica de cabeçalhos e leitura preservada; scroll delimitado quando o conteúdo realmente exigir duas dimensões. |
| Cabeçalho de página (`PageHeader`) | Trilha de navegação opcional, `h1`, descrição opcional e ação principal à direita; a ação desce para baixo do título em telas estreitas. |
| Trilha de navegação | `nav` rotulada "Trilha de navegação" com `ol`; ancestrais como links, página atual em texto com `aria-current="page"`, separador "›" decorativo. Usada só em páginas abaixo de um item do menu (produto, novo produto, estoque). |
| Menu de conta | Botão na barra superior com `aria-expanded`, inicial do e-mail em círculo (decorativa), rótulo "Conta" e e-mail (oculto abaixo de 40rem). Revela e-mail, Minhas lojas, Minha conta e "Sair da conta"; fecha com Escape (foco volta ao botão), clique fora ou troca de página. Só aparece com sessão válida; não substitui verificação de sessão nas páginas. |
| Selo de status (`StatusBadge`) | Texto sempre visível; cor semântica apenas reforça. Positivo: sucesso sobre fundo de sucesso. Neutro: texto secundário sobre fundo desabilitado. |
| Navegação lateral | `nav` rotulada, grupos com rótulo visível, ícone decorativo antes do texto; item atual preenchido (texto branco sobre a cor de ação, 8,85:1) com `aria-current="page"`; hover com fundo selecionado e sublinhado. |
| Diálogo de confirmação (`useConfirm`) | `dialog` nativo modal com título e mensagem associados; "Cancelar" (secundário) vem antes da ação na ordem de foco; Escape cancela e o foco volta ao controle de origem. Ações destrutivas usam a variante destrutiva. |
| Card de resumo | Ícone, rótulo, número e link de próxima ação, somente com dados que as APIs existentes devolvem; sem estimativas. |

Definir e revisar padrão, hover, pressionado, foco visível, desabilitado, carregamento, inválido, sucesso e vazio conforme aplicabilidade. Não criar estado sem utilidade no componente. Durante submissão, impedir disparos duplicados sem bloquear desnecessariamente a navegação; após falha, permitir recuperação. Estado de UI não substitui validação, autorização ou idempotência do backend.

## Autenticação

As telas de autenticação pertencem à plataforma, com composição simples de uma coluna e largura de formulário próxima de 28rem quando houver espaço; não exigem hero promocional, imagens ou animações. Fluxos, política de senha, sessão, CSRF e recuperação seguem o ADR-0005 e a entrega funcional autorizada.

Formulários devem permitir teclado, colar, gerenciadores de senhas e autocomplete apropriado. Usar labels persistentes, instruções antes do erro quando úteis, associação de mensagens aos campos e foco previsível ao falhar. Carregamento precisa de rótulo/feedback acessível e prevenção de submissões duplicadas, com nova tentativa após falha.

Mensagens sensíveis não revelam existência ou status de conta. Não vazar credenciais, cookies, hashes, tokens ou dados pessoais em mensagens, logs, analytics, referer ou evidências. Senhas e identificadores de sessão nunca pertencem a URLs. Tokens CSRF e links de recuperação/verificação, quando necessários, seguem exclusivamente o contrato aprovado de identidade, sem exposição fora desse fluxo. Não simular entrega real de e-mail. Essas regras de UX não concedem autorização para implementar autenticação nem alterar sua arquitetura.

## Acessibilidade e responsividade

Objetivo: **WCAG 2.2 nível AA**, avaliado por automação e revisão manual. Não declarar conformidade integral com base em tokens, screenshots ou ausência de violações automáticas.

- Texto normal: contraste mínimo 4,5:1; texto grande conforme definição WCAG: 3:1. Componentes/indicadores visuais relevantes: 3:1, observadas as condições e exceções do critério aplicável.
- Operação completa por teclado, foco visível e não encoberto, sem armadilhas; HTML semântico, nomes acessíveis, idioma pt-BR e mensagens de estado compreensíveis.
- Alvos com preferência de produto por 44×44 CSS px. O mínimo WCAG 2.2 AA de 24×24 CSS px admite condições/exceções próprias; não confundir as duas medidas.
- Não desabilitar zoom. Testar texto a 200%, reflow equivalente a 320 CSS px/400%, espaçamento de texto e conteúdo longo. Não esconder overflow para encobrir conteúdo inacessível; exceções bidimensionais devem ser justificadas.
- Layout fluido desde 320px, com revisão em 390, 768 e 1440 CSS px. Breakpoints respondem ao conteúdo; não são autorização para remover ações em telas menores.
- Formulários em uma coluna quando apropriado; navegação desktop pode se adaptar ao móvel mantendo nomes, foco e operação. A moldura do admin segue a seção "Moldura e padrões do admin".
- Movimento discreto e funcional, com preferência por movimento reduzido respeitada. Nenhuma ação ou informação pode depender de animação.

## Moldura e padrões do admin

Aprovado em 2026-10-17 a partir da [proposta de referências de mercado](PROPOSTA-REFERENCIAS-MERCADO.md) (itens A1–A7).

- **Moldura:** telas de uma loja (`/lojas/[id]/...`) e da conta (`/lojas`, `/conta`) têm barra superior com o menu de conta e navegação lateral fixa em superfície branca a partir de 64rem (1024 CSS px), com 16rem de largura e a assinatura no topo. Abaixo disso, a barra superior mostra a assinatura e um botão "Menu" com `aria-expanded` que revela a mesma lista. Na loja, o nome aparece como texto no topo e o grupo "Gestão da loja" tem Visão geral, Produtos, Pedidos e Entrega, seguido pelo grupo "Conta" com Minhas lojas e Minha conta; na conta, apenas Minhas lojas e Minha conta. Itens somente para funções existentes. A navegação não é prova de autorização; o backend continua validando cada acesso.
- **Áreas da conta (aprovado em 2026-10-18):** `/lojas` lista e cria lojas e é o destino após o login; `/conta` mostra a identidade e a saída. A entrega da loja tem página própria (`/lojas/[id]/entrega`); a visão geral mantém dados, publicação e um atalho para a entrega.
- **Páginas:** conteúdo até cerca de 80rem ao lado do menu; formulários com no máximo 45rem por coluna.
- **Listas operacionais:** tabela semântica com `caption` e cabeçalhos a partir de 48rem; abaixo disso, lista de linhas com os mesmos dados. Sem seleção em massa, filtros ou ordenação enquanto não houver funcionalidade.
- **Criação em rota própria:** "Novo produto" é uma página (`/produtos/novo`) acionada pelo cabeçalho da lista; após cadastrar, o lojista segue para a edição do produto.
- **Assistente (SC-027, 2026-10-25):** uma coluna de até 48rem. A pergunta do lojista fica num balão à direita, com o fundo de seleção. A resposta fica num cartão de superfície com borda esquerda de ação; quando o assistente não responde, a borda é neutra. As fontes aparecem em lista menor abaixo de um divisor. O aviso de IA usa o componente `notice`. Tudo com os tokens existentes, sem cor nova.
- **"Explicar" (SC-028, 2026-10-26):** link de texto discreto (0,875rem, negrito, alvo de 44px) abaixo do número do cartão, ao lado do link de cada alerta e abaixo do título das listas do painel. O nome acessível completa o que será explicado. Só aparece com o assistente ligado.
- **Dados da loja (bloco 1b do MVP, 2026-10-26):** formulário de uma coluna de até 45rem, com dois cartões: "Nome e contatos" e "Logo". A prévia do logo nunca é esticada: altura máxima de 8rem, sobre o fundo neutro com divisor. Na vitrine, o logo tem até 2,5rem de altura ao lado do nome, que continua em texto, e os contatos ficam em links de texto com alvo de 44px. Tudo usa os tokens existentes, sem cor nova.
- **Categorias (bloco 1c do MVP, 2026-10-26):** no admin, lista de uma coluna de até 45rem com renomear em linha e remoção com confirmação; no editor do produto, um cartão lateral "Categoria" com um select e o botão "Salvar categoria", sem mexer na versão do formulário principal. Até a revisão de interface do MVP (2026-10-31), o select salvava ao escolher; no Chrome para Windows, cada seta do teclado no select fechado gravava. Na vitrine, links em pílula acima dos produtos ("Todos" e as categorias com produtos publicados), com alvo de 44px, quebra de linha em telas estreitas e a atual marcada por `aria-current="page"`, negrito e sublinhado, não só por cor. Sem cor nova.
- **Páginas legais (bloco 1d do MVP, 2026-10-26):** coluna de leitura de até 45rem, com títulos de seção e listas. O aviso "Rascunho em validação" usa superfície neutra com borda lateral, sem cor nova. O aviso legal do cadastro fica acima do botão e é ligado a ele por `aria-describedby`. Os links legais dos rodapés têm alvo de 44px.
- **Alterações não salvas (revisão de interface do MVP, 2026-10-31):** todo formulário editável do admin avisa antes de recarregar, fechar a aba ou seguir um link interno com alterações pendentes, pelo hook `useUnsavedChanges` (`lib/use-unsaved-changes.ts`). O aviso é o `confirm` nativo, porque precisa ser síncrono. Link para âncora da mesma página (como o "Ir para o conteúdo"), nova aba e download não perguntam. Trocar a página institucional em edição pede confirmação com o diálogo do design system.
- **Edição em duas colunas:** a partir de 64rem, coluna principal (identificação, descrição, fotos e variações) e lateral com publicação e estoque. Em telas menores, uma coluna na ordem do DOM.

## Repaginação 2026-10 (referência Dashtail)

Aprovada pelo responsável em 2026-10-20. O template comercial Dashtail v1.3.0 (ThemeForest; Next 14, Tailwind 3 e shadcn/Radix) foi usado **somente como referência visual e estrutural** (decisão substituída em 2026-10-26: ver "Design system Dashtail"). Nenhum código, ícone, ilustração, texto ou dependência foi copiado, porque a adoção de Tailwind/Radix contrariaria a política de dependências. **Licença (2026-10-20):** o responsável informou ter uma licença que permite usar o template em qualquer projeto, então copiar código ou recursos dele é permitido. Mesmo assim, uma cópia não pode trazer dependência nova sem aprovação e deve ser adaptada aos tokens e componentes locais. Paleta violeta, fonte Inter, tema escuro, customizador, RTL, busca, notificações, gráficos e toasts do template não foram adotados.

Padrões adotados com CSS e componentes locais:

- **Admin:** moldura com barra superior e navegação lateral agrupada, com ícones SVG próprios (`components/icons.tsx`) e item atual preenchido. Cards com cabeçalho separado por divisor e sombra sutil. Visão geral da loja com três cards de resumo: vitrine, produtos cadastrados/selecionados e pedidos aguardando pagamento. O último mostra "Mais de 20" quando a API indica outra página. Tabelas com hover de linha e selos com ponto e texto. Diálogo de confirmação nativo substitui `window.confirm` ao publicar/ocultar loja e produto, remover foto, mudar o tipo do produto e recarregar versões. As guardas de saída com alterações não salvas continuam nativas, porque precisam ser síncronas.
- **Vitrine:** cabeçalho com borda e sombra, fixo ao rolar a partir de 40rem, com `scroll-padding-top` para não encobrir o foco; abaixo disso, não é fixo. O link do carrinho mostra a quantidade de unidades lida só do cookie da loja. Cards de produto com borda, sombra e elevação no hover, sem movimento com `prefers-reduced-motion`. Checkout e página do pedido com etapas Carrinho → Dados e entrega → Pedido registrado: `aria-current="step"` e "(concluída)" em texto. A situação do pedido tem uma linha do tempo curta (registro e situação atual), sem etapas futuras fictícias.

Validação em 2026-10-20: lint, typecheck, build e test:smoke das duas aplicações. No navegador do app (Chromium), em 1024 CSS px e 375 CSS px, com backend sintético local: moldura, menu móvel, cards de resumo, tabelas, diálogo e fluxo da vitrine até o pedido. O diálogo foi conferido pelo DOM: abre centrado, foca "Cancelar", fecha com Escape e devolve o foco. Revisão com as Web Interface Guidelines atuais, consultadas na mesma data. Com 1024 CSS px, a tabela de produtos rola dentro do card, sem rolagem da página. Axe, leitor de tela e zoom manual não foram executados; não há alegação de conformidade WCAG completa.

Tokens novos: `--sc-shadow-surface`, `--sc-shadow-overlay`, `--store-shadow-surface`, `--store-shadow-raised` e `--store-header-height`. Nenhuma cor, fonte ou raio-base mudou; o aviso "Ambiente de teste" e a separação admin/vitrine foram preservados.

## Design system Dashtail (2026-10-26)

Aprovado pelo responsável em 2026-10-26. O pedido foi uma interface de "nível empresa grande" com os componentes do Dashtail: páginas de login, botões, sidebar retrátil e busca. Esta decisão substitui a restrição "somente referência" da repaginação de 2026-10-20. A licença do responsável permite copiar o código do template.

**Decisões:**
- **Stack, só no admin:** Tailwind CSS 4 e componentes no padrão shadcn/ui copiados do Dashtail e adaptados ao Next 16 e ao React 19.
- **Dependências aprovadas:**
  - `tailwindcss`, `@tailwindcss/postcss` e `postcss` (desenvolvimento);
  - `clsx`, `tailwind-merge` e `class-variance-authority`;
  - `@radix-ui/react-slot`, `react-dialog`, `react-dropdown-menu` e `react-tooltip`;
  - `cmdk` (busca) e `lucide-react` (ícones).

  `recharts` está aprovado para a fase dos gráficos e ainda não foi instalado. A vitrine não muda.
- **Convivência:** o Tailwind entra sem o preflight. O CSS anterior fica na camada `legacy`, abaixo das utilities, então as telas ainda não migradas mantêm a aparência e os componentes novos vencem por camada, não por especificidade.
- **Tokens:** as cores do Tailwind (`primary`, `muted`, `accent`, `border`, `ring`, `destructive`, `success`…) apontam para os tokens `--sc-*` existentes. A paleta e o contraste não mudaram.
- **Tipografia:** Inter, a fonte do Dashtail, servida pelo `next/font`, sem requisição do navegador ao Google. Arial fica como fallback.
- **Tema:** só claro nesta etapa. O escuro fica como melhoria.

**O que foi aplicado:**
- **Componentes (`src/components/ui`):** `Button`, `Input`, `Label`, `PasswordInput`, `Card`, `Badge`, `Kbd`, `DropdownMenu`, `Tooltip` e `Dialog` (também como gaveta lateral). Diferenças em relação ao template:
  - foco visível (o Dashtail usa `focus-visible:ring-0`);
  - alvo de 44px nos controles principais;
  - contraste AA na paleta do admin;
  - nomes acessíveis em todos os botões só com ícone.
- **Moldura (`app-frame.tsx`):** sidebar de 16rem que recolhe para 4.5rem só com ícones. No modo recolhido, o nome de cada link continua acessível e aparece em tooltip. A preferência fica no `localStorage`. Abaixo de 64rem, a sidebar vira gaveta (diálogo com foco preso e Escape). O cabeçalho fixo tem o botão de recolher, a busca e o menu da conta (dropdown com e-mail, lojas, conta e sair). "Categorias" entrou no menu da loja.
- **Busca global (`global-search.tsx`):** Ctrl+K (ou ⌘K) abre a busca em qualquer tela do painel. Ela lista:
  - ações e páginas da navegação;
  - dentro de uma loja, os produtos e os pedidos recentes, carregados só quando a busca abre.

  O filtro é estrito: todas as palavras precisam aparecer, sem diferenciar acentos ou maiúsculas. A regra está em `lib/navigation.ts`, coberta pelo smoke.
- **Autenticação (`(auth)/layout.tsx`):** tela dividida a partir de 64rem, com painel da marca à esquerda e formulário à direita. Os campos são maiores, a senha tem botão de mostrar e ocultar (`aria-pressed`), e o link "Esqueci minha senha" fica junto do campo. As URLs não mudaram.
- **Links:** sem sublinhado por padrão. O sublinhado aparece no hover e continua em links dentro de texto corrido (`p a`, ajuda de campo e páginas legais), para não depender só da cor (WCAG 1.4.1). Botões e links com aparência de botão nunca sublinham.
- **Botões do CSS anterior:** raio de controle de 6px e peso 600, para combinar com os componentes novos.

**Fase 2 (2026-10-26):**
- **Carregamento padronizado:** `Loading` no admin (`ui/loading.tsx`) e na vitrine (`components/loading.tsx`). O texto visível é só "Carregando…", com pulso suave que para quando o sistema pede menos movimento. O que está carregando ("Carregando pedidos da loja…") vai só para leitores de tela, dentro de `role="status"`. Ele substitui os estados "Consultando…" de todas as telas; botões em andamento continuam com o próprio texto.
- **Gráficos (`recharts`, aprovado):**
  - na visão geral, receita por dia em barras e pedidos pagos em linha, com eixo próprio;
  - no financeiro, barras horizontais da receita bruta até a margem de contribuição.

  Os tooltips mostram os valores do backend como texto, sem cálculo no front. O SVG fica oculto para tecnologia assistiva porque a legenda e a tabela ao lado trazem os mesmos números. Para a segunda série e as deduções há dois tokens novos, `--color-chart-2` (#0f766e) e `--color-chart-deduction` (#657888), ambos com 3:1 ou mais sobre o card.
- **Cards de indicador (`ui/kpi-card.tsx`):** ícone em círculo suave, rótulo, valor e detalhe, no estilo das estatísticas do Dashtail. São usados no resumo da loja, no desempenho e no financeiro.
- **Card de produto da vitrine:** portado do card de e-commerce do Dashtail em CSS próprio, sem Tailwind na vitrine. Tem foto emoldurada com zoom leve no hover (desligado com movimento reduzido), selo de disponibilidade sobre a foto, nome, preço e botão contornado "Ver produto" de 44px que se preenche no hover. O card inteiro continua sendo um único link. Favoritos, visualização rápida e avaliação do template ficaram de fora porque não existem no produto.

**Fase 3 (2026-10-26), tabelas com filtros:**
- **Tabelas:** todas usam a mesma classe `.data-table`, agora no estilo do Dashtail: cabeçalho em versalete pequeno sobre faixa neutra, mais respiro nas células e destaque da linha no hover. No celular, as linhas continuam empilhadas com o rótulo de cada coluna.
- **Barra de filtros (`.table-toolbar`):** fica acima da tabela, com `SearchInput` (ícone de busca, `type="search"`) e `Select` (select nativo estilizado, que mantém o teclado, o leitor de tela e o seletor do celular). Quebra de linha em telas estreitas.
- **Produtos:**
  - busca por nome ou SKU, incluindo o SKU das combinações, sem diferenciar acentos;
  - filtros de categoria (com "Sem categoria") e de vitrine;
  - ordenação por cadastro, nome ou preço, com preço comparado em centavos exatos e nunca em ponto flutuante;
  - contagem "X de Y produtos" na legenda, anunciada (`aria-live`), e "Limpar filtros".

  Tudo roda sobre o catálogo já carregado (`lib/product-filters.ts`, coberto pelo smoke).
- **Pedidos:** o select de situação virou abas (links com `aria-current`), que funcionam sem JavaScript e rolam para o lado no celular. Não há busca por texto, porque a API filtra só por situação e pagina de 20 em 20: buscar dentro de uma página daria um resultado enganoso. Para buscar um pedido específico, use a busca global (Ctrl+K), que lista os pedidos recentes.

**Fase 4 (2026-10-26), formulários e telas operacionais:** as telas usam classes compartilhadas, então a mudança foi central, num bloco no fim da camada `legacy` que vence as regras originais com a mesma especificidade. Assim todas as telas mudam de uma vez, sem reescrever cada componente.
- **Campos (`input`, `select` e `textarea`):** 44px de altura, borda de controle com 3:1 que escurece no hover, e foco com borda de ação mais contorno de 2px. O select ganhou o chevron do Dashtail sem perder o controle nativo. Caixas de seleção e opções usam a cor de ação. Campos desabilitados aparecem em cinza, com cursor de bloqueio.
- **Botões e links-botão:** layout flexível com espaço para ícone, peso 600, transição suave. O botão secundário ganha borda de ação no hover. O contorno global de foco ficou mais fino (2px, afastado 2px) e continua laranja, visível sobre qualquer fundo.
- **Cards** (cartões laterais, tabelas, painéis vazios, estados, barra de filtros, editor de produto, lojas e categorias): borda clara `--sc-color-card-border` (#dfe5ec), raio de 8px e sombra suave `--sc-shadow-card`. A borda do card é decorativa, porque os controles de dentro mantêm a própria borda com 3:1. Painéis vazios ficam centralizados.
- **Alertas (`.feedback` e `.notice`):** fundo suave com borda esquerda de 4px na cor da mensagem, como no Dashtail. O texto continua dizendo o que aconteceu.
- **Selos:** em pílula, menores, com peso 600.
- **Cabeçalho de página:** título de 24px em negrito e trilha em texto menor.
- **Ícones:** os estados vazios e de erro (`PageState`) passaram a usar lucide, como o resto do painel. O conjunto antigo (`components/icons.tsx`) foi removido.

**Fase 5 (2026-10-26), revisão de acessibilidade:** feita com as Web Interface Guidelines e a WCAG 2.2 AA. Achados, correções, contraste calculado e limitações estão em [UI-A11Y-REVIEW.md](../quality/UI-A11Y-REVIEW.md). Mudanças de padrão:
- `scroll-padding-top` de 5rem, para o cabeçalho fixo não esconder o foco;
- sidebar sem animação de largura;
- `overscroll-contain` em gaveta e listas roláveis;
- filtros dos produtos refletidos na URL;
- a busca global lista produtos e pedidos só enquanto se digita, até 20 de cada;
- `touch-action: manipulation` e destaque de toque intencional no admin;
- links isolados com alvo de pelo menos 24px.

## Plataforma e temas futuros das lojas

Admin e autenticação usam identidade e tokens `--sc-`. A vitrine é aplicação separada, conforme arquitetura. Sua personalização futura terá contrato próprio de tokens permitidos, por exemplo `--store-`, com fallback e validação de contraste; não adotar CSS arbitrário do cliente ou vazamento de estilos entre aplicações.

Nome, logo e cores da loja não sobrescrevem estilos globais do admin ou telas de autenticação. Tema, slug ou seletor de empresa nunca é prova de autorização; contexto de tenant continua derivado pelo backend de identidade e vínculo autorizado. Não criar infraestrutura de temas, páginas comerciais, catálogo ou vitrines por este documento.

## Gatilhos, limites e portabilidade das skills

| Skill | Gatilho | Limite |
| --- | --- | --- |
| design-taste-frontend | Criar, redesenhar ou revisar direção de arte de páginas públicas elegíveis e autorizadas. | Nunca dashboards ou autenticação; cotas de imagens/animação e regras editoriais não se aplicam ao admin. |
| web-design-guidelines | Revisar UI/UX dos arquivos de interface no escopo. | Buscar regras atuais em cada revisão; relatar arquivo/linha. Não substitui testes funcionais nem avaliação WCAG completa. |
| awesome-design-md | Referência pontual para direção visual, tokens ou DESIGN.md. | Ler índice e referência selecionada, registrar adaptações; não copiar identidade nem tratar análise como especificação oficial. |

Resolver as skills por nome no catálogo do ambiente e consultar seu SKILL.md. Caminhos absolutos, instalação e disponibilidade locais não fazem parte do contrato do repositório. Skills e seus scripts não são dependências do runtime, build ou CI. Registrar indisponibilidade e verificações pendentes, preservando os requisitos de qualidade.

Nenhuma skill autoriza instalação de bibliotecas, alteração da identidade visual, mudança arquitetural ou ampliação do MVP. Exemplos de bibliotecas nas referências não criam obrigação de adoção. Segurança, acessibilidade e decisões aprovadas do projeto prevalecem sobre preferências externas.

## QA, ferramentas futuras e evidências

Frontend revisa a implementação e QA faz avaliação independente conforme [FRONTEND.md](../agents/FRONTEND.md) e [QA.md](../agents/QA.md). Registrar fontes/data das referências, arquivos/linhas dos achados, estados, viewports, resultados e limitações.

**Playwright e Axe são ferramentas propostas para uma etapa futura; não estão sendo instalados ou executados por esta tarefa.** Confirmar versões/compatibilidade e aprovar escopo antes de alterar dependências ou CI. Cobertura futura: navegação, formulários, submissões duplicadas, responsividade, movimento reduzido e Axe nos estados relevantes, complementados por teclado, leitor de tela e zoom manuais. Nenhum teste futuro é considerado aprovado antecipadamente.

Usar dados sintéticos, artefatos sem segredos e retenção controlada; screenshots mascarados não sanitizam traces, URLs ou respostas capturadas. Separar verificações executadas/aprovadas, reprovadas, bloqueadas, pendentes e não aplicáveis justificadas.

## Governança de mudanças

### Landing e demonstrações — escopo autorizado em 2026-10-18

#### Evolução exclusiva da landing — direção Pacto

Após a análise da referência pública `pactosolucoes.com.br` (redirecionada para `sistemapacto.com.br`), o responsável autorizou aplicar essa direção **somente à landing page**. A decisão permite título Unbounded e corpo Mona Sans, fundo escuro, destaque azul, degradês, vidro no cabeçalho, botões em cápsula e movimentos suaves exclusivamente na rota `/`. Motivação: dar à apresentação pública uma hierarquia e presença visual próprias, mantendo a identidade operacional existente. Alternativa considerada: preservar o tratamento claro anterior; a direção escura foi escolhida pelo responsável ao solicitar a aplicação da referência.

Tokens locais `--lp-*`: fundo `#050A16`, superfície `#0C1526`, texto `#F7F9FF`, secundário `#B4BFD4`, ação `#3264EF`, fundo claro `#F5F7FC` e texto escuro `#16243B`. Fontes WOFF2 hospedadas localmente com licenças SIL OFL preservadas; sem carregamento de provedor externo. Controles têm pelo menos 44px, foco contrastante e fallback de movimento reduzido. Animações decorativas contínuas foram limitadas a menos de cinco segundos; conteúdo permanece disponível sem observer/JavaScript. Breakpoints, teclado, contraste e isolamento têm evidências e limites em [LANDING-VALIDATION.md](../quality/LANDING-VALIDATION.md).

O wrapper recebe `.landing-page` somente quando `usePathname()` é `/`, incluindo header/main/footer. Novas regras CSS, fontes selecionadas e animações são restritas a esse wrapper. Não sobrescrever `:root`, `--sc-*` nem `presentation.css` para propagar essa direção. `/admin/produtos`, `/loja/produto`, `/componentes`, autenticação, admin real e `storefront-web` conservam o padrão anterior. Prévia de interface dentro da landing mantém Arial e os tokens operacionais. As alterações prévias de grupos de rotas continuam; não há mudança de API, tenant, dependências ou arquitetura.

Referência primária: inspeção REA de 2026-10-18 do DOM, CSS e JavaScript públicos da Pacto. A referência orienta técnicas de composição e animação; marca, imagens, textos comerciais, números e depoimentos não são copiados. Skills editoriais são adaptadas a essa direção explicitamente solicitada. `awesome-design-md`: índice sem Pacto; análise Linear consultada apenas como apoio de hierarquia entre marketing escuro e prévia de produto, sem adotar sua identidade. A direção da Pacto já escolhida prevalece sobre sorteio de uma direção alternativa.

**Bloco 2a do MVP (2026-10-26):** as demonstrações `/admin/produtos` e `/loja/produto` e a galeria `/componentes` foram removidas por decisão do responsável; a landing mantém o design e as prévias com dados fictícios, com chamadas para cadastro e login. Histórico: o briefing fornecido pelo responsável autorizou a apresentação em `/`, demonstrações isoladas em `/admin/produtos` e `/loja/produto` e galeria em `/componentes`. Essas rotas não existiam no checkout: foram criadas sem substituir telas operacionais. A galeria é uma nova amostra de componentes locais; a antiga home era somente uma entrada administrativa.

O tratamento anterior da apresentação usava fundo de apoio `#E9F1FA` e ação `#00ABE4` com texto `#172B3A`, conforme o briefing. Esse tratamento continua nas demos e galeria; a home adota somente a exceção acima. Os tokens globais do admin/autenticação permanecem. Arial, raios 4/8px, foco e cores semânticas existentes são reutilizados fora da landing. Não há nova biblioteca, integração, imagem de terceiros ou identidade gráfica.

Os grupos de rotas `(presentation)` e `(platform)` separam cabeçalhos, rodapés e navegação. Demos usam dados sintéticos, avisos explícitos e estado efêmero; não consultam identidade nem API de negócio. Itens operacionais sem demo aparecem como “Em preparação”. Recursos planejados não têm ações que simulem disponibilidade.

Mudanças relevantes exigem aprovação humana antes de aplicação: identidade, papéis/valores de cores, tipografia, padrões de interação, acessibilidade, temas ou adoção de biblioteca UI. A proposta deve apresentar motivação, alternativas, impacto nas superfícies/estados, compatibilidade, evidências de acessibilidade e diff documental. Uma recomendação de skill ou referência não é aprovação.

Implementar um componente com os tokens e padrões já aprovados não exige reabrir a decisão visual, desde que a tarefa de implementação esteja autorizada. Mudanças arquiteturais continuam sujeitas aos ADRs e ao fluxo de aprovação próprios. A aprovação desta base documental não autoriza CSS, componentes, páginas, novas dependências ou a SC-002.

## Como seguir o padrão nas próximas entregas

1. Consultar este documento antes de criar ou modificar uma interface.
2. Reutilizar os tokens e componentes locais existentes. Acrescentar variantes somente com necessidade real, sem criar bibliotecas antecipadas.
3. Revisar nome, paleta, tipografia, espaçamento, estados, responsividade, linguagem e acessibilidade no diff da entrega.
4. Registrar desvios como proposta antes de aplicá-los. Mudanças relevantes seguem a aprovação descrita acima.
5. Atualizar a prancha visual quando uma mudança de identidade for aprovada. Ela é uma referência estática, sem fluxos funcionais, dados reais ou dependências de runtime/CI.

## Verificação desta versão

Em 2026-10-16, frontend e QA revisaram a coerência documental e a prancha, sem bloqueadores. Foram consultadas as Web Interface Guidelines atuais; não foram instaladas ferramentas ou dependências.

Cálculo sRGB dos pares existentes: texto principal/branco 14,55:1; texto secundário/branco 8,33:1; branco/ação 8,85:1; link/branco 8,51:1; sucesso/fundo de sucesso 6,81:1; erro/fundo de erro 6,05:1; foco/branco 6,50:1; borda de controle/branco 4,57:1. Esses resultados valem para esses pares opacos, sem generalizar para outras combinações ou estados.

Prancha aberta no navegador local; verificação de dimensões em 320 e 1440 CSS px sem overflow horizontal no documento. Axe, leitor de tela e zoom manual não foram executados; não há alegação de conformidade WCAG completa.

## Referências

- [WCAG 2.2](https://www.w3.org/TR/WCAG22/) e [guia de critérios](https://www.w3.org/WAI/WCAG22/quickref/).
- [Fonte das Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md), a consultar novamente em cada revisão.
- [Playwright: testes de acessibilidade](https://playwright.dev/docs/accessibility-testing), que combinam automação e avaliação manual.
- Referências de mercado consultadas em 2026-10-17 (Nuvemshop/Nimbus, VTEX Shoreline, Wix, Tray, Loja Integrada e Baymard), com fontes e limitações em [PROPOSTA-REFERENCIAS-MERCADO.md](PROPOSTA-REFERENCIAS-MERCADO.md). Usados apenas padrões estruturais; nenhuma identidade, biblioteca ou componente de terceiros foi adotado.
- Coleção [awesome-design-md](https://github.com/VoltAgent/awesome-design-md), revisão local fixada em `3b605329240ce735b510037cca322f1831fdd7f8`, referência analisada IBM, consultada em 2026-10-16. Utilizados apenas os princípios de hierarquia, superfícies simples e papéis de cor. Paleta, Arial, raios e assinatura continuam próprios do Smart Commerce; não foram adotados Carbon, IBM Plex, marca, layout promocional ou dependências de terceiros. Trata-se de análise de referência, não especificação oficial da IBM.

## Base visual da vitrine de consulta

A vitrine autorizada em 2026-10-17 usa o fallback profissional existente: Arial, azul profundo, superfícies claras, controles 4px e superfícies 8px. Tokens --store-* ficam no storefront-web e não sobrescrevem --sc-* do admin. Nome da loja aparece como texto real, sem novo logotipo ou tema personalizado. Publicar somente produtos selecionados, com nome, preço e disponibilidade; sem imagens ou conteúdo comercial inventado. A disponibilidade de compra não é simulada.

O refinamento visual autorizado na mesma data aplica essa base ao cabeçalho em superfície branca, abertura em azul profundo, catálogo com preço e disponibilidade em destaque e detalhe dividido entre identificação do produto e resumo de preço. Em telas menores, a composição passa para uma coluna. Nome real da loja e navegação funcional orientam a página; não adicionar categorias, busca, avaliações, fotos ou ações de compra sem dados e funcionalidades correspondentes. O rodapé identifica a plataforma e o caráter de consulta da vitrine. Os componentes locais compartilham estados de disponibilidade e navegação, sem biblioteca adicional, novo logotipo ou mudança dos tokens aprovados.

O checkout e a página do pedido (SC-009) seguem a mesma base: formulário em uma coluna com resumo lateral, que vira uma coluna abaixo de 52rem; rádios de entrega com rótulo, descrição e total no mesmo alvo; resumo de erros focável com links para os campos; e erros por campo em texto. Os tokens `--store-control-border`, `--store-danger` e `--store-danger-background` reproduzem os valores aprovados de `--sc-color-control-border` e `--sc-color-danger*`, sem nova cor. Os textos usam caixa de frase.

**Pedidos no admin (SC-010).** A lista de pedidos usa a tabela de dados com 5 colunas:
- pedido, com a data de criação como dica;
- situação em selo (positivo para "Aguardando pagamento", neutro para expirado e cancelado);
- comprador;
- entrega com unidades;
- total.

Abaixo de 64rem as linhas ficam empilhadas. O filtro e a página ficam na URL (`?situacao=`, `?apos=`).

O detalhe empilha situação, itens, valores, comprador e cancelamento, e só usa duas colunas a partir de 90rem, onde a tabela de itens ainda cabe ao lado do painel de 22rem.

O cancelamento é uma ação destrutiva em duas etapas: "Cancelar pedido" abre o formulário com o motivo, e "Confirmar cancelamento" (destrutivo) fica ao lado de "Manter pedido" (secundário). As variantes destrutiva (texto branco sobre `--sc-color-danger`, 6,57:1 em cálculo sRGB; o estado de hover sublinha o rótulo em vez de usar outra cor) e secundária (superfície, borda de controle e texto de ação) aplicam a regra "principal/secundária/destrutiva por função" desta página, sem nova cor.

Ajustes aprovados em 2026-10-17 (itens V1–V4 da [proposta](PROPOSTA-REFERENCIAS-MERCADO.md)): a abertura do catálogo é compacta, com nome da loja e contagem de produtos, sem bloco azul; o azul fica reservado a ações. A grade usa cards em coluna com foto de capa real em 1:1 e `object-fit: contain`, nome, disponibilidade e preço. A página de produto coloca galeria e descrição à esquerda e um resumo fixo ao rolar (nome, preço, variações e disponibilidade) à direita a partir de 54rem; em telas menores, galeria, resumo e descrição nessa ordem. Variações são escolhidas por atributo (por exemplo Cor e Tamanho) com rádios nativos estilizados, rótulo em texto e seleção indicada por borda, peso e marca de verificação, não só por cor; combinações indisponíveis são indicadas por texto. Quando as variações não compartilham os mesmos atributos, a lista de combinações em `select` continua como alternativa.

A skill design-taste-frontend apoia composição e revisão, preservando paleta/tipografia e sem impor imagens, animações ou conteúdo incompatíveis com o catálogo real e o design aprovado. Personalização visual futura e mudanças relevantes permanecem sujeitas à governança acima.

## Vitrine padrão V1 (Sprint 8)

Aprovada pelo responsável em 2026-10-27 (decisão V12 do [ADR-0018](../architecture/adr/0018-storefront-v1.md), D5 do [replanejamento](../product/VITRINE_REPLANEJAMENTO.md)), a partir de uma proposta com quatro telas: home desktop, categoria/busca, produto e home no celular. **Substitui a base visual da vitrine de consulta** (Arial, azul profundo, controles 4px) e o card de produto portado do Dashtail quando a SC-044 for implementada; o logo e o "Fale com a loja" do bloco 1b continuam, no novo cabeçalho e rodapé; até lá, a seção anterior continua valendo para o código atual. Não altera `--sc-*`, admin nem autenticação.

**Direção:** varejo grande e neutro, que serve a moda, calçados, acessórios e cosméticos. A foto do produto é o elemento principal; a interface fica em branco e quase preto, e a cor aparece só com função (ação, desconto, contato). Referência funcional, não visual: [BENCHMARK_VITRINE_SPORTLIFE.md](../product/BENCHMARK_VITRINE_SPORTLIFE.md).

**Tipografia:** Manrope (400–800), servida pela própria aplicação (`next/font`, sem requisição ao Google em tempo de execução), com fallback `system-ui, sans-serif`. Títulos em 800 com espaçamento negativo leve (−0,02 a −0,03em); marca do produto em caixa alta pequena (12px, 700, +0,06em); preço em 800; texto corrido 15–16px.

**Cores (tokens `--store-*` novos, contraste medido sobre branco):**

| Papel | Valor | Contraste |
| --- | --- | --- |
| Texto e ação principal (botões, faixa de aviso, chip ativo) | `#111418` | 18,5:1 |
| Texto secundário | `#3D4550` / `#4E5661` | 9,7:1 / 7,4:1 |
| Preço "de" riscado | `#5B6470` | 6,0:1 |
| Destaque (contador do carrinho, link em hover) | `#1F4FD8` | 6,6:1 |
| Desconto (selo "−17%", categoria Outlet) | `#B3261E` | 6,5:1 |
| WhatsApp e confirmação | `#127C3C` | 5,3:1 |
| Borda de controle (campos, botões de variante, seletor) | `#7A828D` | 3,9:1 |
| Divisores e bordas decorativas | `#E3E6EA` | decorativo |
| Superfícies (busca, fotos sem imagem, rodapé) | `#F3F4F6`, `#F1F3F5`, `#F5F6F8` | — |

A proposta começou com `#A9B0BA` nas bordas de controle (2,2:1, abaixo do 3:1 exigido para componentes); o responsável aprovou `#7A828D` em 2026-10-27, já aplicado à proposta visual. Variante esgotada usa texto atenuado, borda tracejada e risco, mas a informação vem também no nome acessível ("GG, esgotado") e o botão fica desabilitado.

**Geometria:** busca, botões de ação, chips e paginação em pílula; cards de produto e controles com 10–12px; banner e foto principal com 16px. Foto de produto em proporção 4:5. Alvo mínimo de 44px em tudo que é clicável, inclusive miniaturas e quantidade.

**Composição:**
- **Global:** faixa de aviso opcional (preto), cabeçalho branco com nome/logo, busca em pílula, "Acompanhar pedido" e carrinho com contador; menu de categorias logo abaixo, com a página atual sublinhada (`aria-current`). Rodapé claro em quatro colunas (sobre, categorias, ajuda/institucional, atendimento) e linha final com razão social, CNPJ e "Loja criada com Smart Commerce". Botão flutuante de WhatsApp, sem cobrir conteúdo no celular.
- **Home:** banner com anterior/pausar/próximo (sem troca automática com `prefers-reduced-motion`), faixa de vantagens com textos vindos da configuração da loja, atalhos de categoria e seções de produtos com "Ver todos".
- **Card de produto:** foto, selo de desconto ou "Esgotado", marca, nome, preço "de" riscado + preço, e "em até N× de R$ X no Mercado Pago" (nunca "sem juros", ADR-0018 V4).
- **Categoria/busca:** breadcrumb, título e contagem, ordenação em `<select>` rotulado, chips dos filtros aplicados com "Limpar filtros", filtros em `<fieldset>` com contagem (painel no celular) e paginação numerada.
- **Produto:** miniaturas verticais + foto principal com zoom; marca, nome, preço, parcelamento; variantes em botões com `aria-pressed`; quantidade e "Adicionar ao carrinho" em pílula; quadro de entrega, trocas e pagamento; descrição; relacionados.
- **Celular:** menu em gaveta pelo botão do cabeçalho, busca logo abaixo, categorias em chips com rolagem horizontal, banner próprio para celular e grade de dois produtos.

**Limites:** sem imagens, avaliações, contagens ou textos comerciais inventados; tudo que aparece vem dos dados e da configuração da loja (ADR-0018 V8). Sem pixels nem scripts de terceiros. Os valores acima são o ponto de partida do contrato de temas da V2: um tema poderá trocar cores, fonte de uma lista fechada e raios, desde que mantenha o contraste.

**Evidência:** proposta visual em artefato privado do responsável (2026-10-27); contrastes calculados pela fórmula WCAG 2.x. Sem teste com leitor de tela, zoom ou Axe nesta etapa: isso entra na SC-048.

**Implementação (SC-044 e SC-045, 2026-10-27):** a base acima está no `storefront-web` e substituiu a anterior. Os tokens `--store-*` existentes receberam os valores novos, então carrinho, checkout e pedido herdaram a paleta, a fonte e os raios sem reescrita. Entraram tokens novos: `--store-accent`, `--store-discount`, `--store-whatsapp`, `--store-strike`, `--store-link-hover`, `--store-band`, `--store-radius-large`, `--store-radius-pill` e `--store-font`. A Manrope vem do `next/font`, baixada no build e servida pela própria vitrine.
- **Moldura de toda página da loja (`StoreFrame`):** cabeçalho com nome/logo, busca em pílula e carrinho com contador no destaque. Menu de categorias com `<details>` nativo (abre por teclado, sem JavaScript): barra com painéis a partir de 54rem e um painel único "Categorias" abaixo disso. Rodapé da loja (categorias e atendimento) e WhatsApp flutuante. A faixa da plataforma ("Loja criada com Smart Commerce", privacidade) fica abaixo, em todas as páginas.
- **Card:** foto 4:5, selo de desconto ou "Esgotado", marca em versalete, nome, preço "de" riscado, preço e "em até N× no Mercado Pago".
- **Categoria/busca:** caminho, título e contagem, ordenação em formulário GET com botão "Aplicar". Filtros como links (lateral a partir de 54rem, painel "Filtrar produtos" abaixo), chips para opções de variante, "Limpar filtros" e paginação numerada em pílula.
- **Produto:** caminho pelas categorias, marca, preço "de" com selo, parcelamento, notas de entrega e pagamento, descrição e "Você também pode gostar".
- **Conteúdo da loja (SC-043):**
  - faixa de aviso no topo;
  - carrossel de banners: um slide por vez, anterior, próximo e "Pausar"; troca sozinho a cada 7 s, mas nunca com `prefers-reduced-motion` nem com o ponteiro ou o foco dentro dele;
  - seções configuráveis da home;
  - páginas institucionais em "Ajuda" e dados legais no rodapé.

  As vantagens da home continuam fora: o texto delas viria de uma configuração que não existe.
- **Checkout e pedido (SC-046):**
  - atalho "Acompanhar pedido" no cabeçalho, só com o ícone no celular;
  - checkout com moldura reduzida (nome da loja e contato, sem busca nem menu) e resumo lateral fixo no desktop;
  - foto de cada item no carrinho.

**Revisão de acessibilidade (SC-048, 2026-10-31):**
- **Tokens:** contrastes recalculados (texto de 4,94:1 a 18,47:1; borda de controle 3,89:1; foco 6,5:1).
- **Navegador, com backend sintético:**
  - ordem do teclado e foco visível no cabeçalho, no menu e no carrinho;
  - árvore de acessibilidade com região, grupos e nomes;
  - sem rolagem horizontal em 320 px, equivalente a zoom de 400%.
- **Corrigido:**
  - título oculto "Produtos" nas listagens, para os títulos dos cards (h3) não virem logo após o h1;
  - dimensões da imagem do banner, para evitar deslocamento de layout.
- **Sem cobertura:** sem Axe, sem leitor de tela real e sem Lighthouse, ferramentas que não estão instaladas. Registro em [SC-048](../quality/SC-048-INTEGRATED-REVIEW.md).

## Conteúdo do produto autorizado

**Ritmo das telas operacionais (2026-10-22).** O contêiner de página separa blocos irmãos por 24 px, usando os tokens existentes; grades internas conservam seu próprio gap. Filtros e navegação de competência usam superfícies e controles locais. Estados de carregamento, vazio e erro oferecem título, explicação e ação pertinente em painel reutilizável. Listas de estoque separam nome, variações e disponibilidade em linhas legíveis. Aplicação dos padrões existentes, sem mudança de identidade, cores, tipografia ou biblioteca.

Na evolução do catálogo de 2026-10-17, fotos reais enviadas pelo lojista podem compor capa e galeria sem trocar a identidade da plataforma. Usar dimensões explícitas, proporção consistente com `object-fit: contain` para não cortar o produto, texto alternativo obrigatório e navegação por teclado. Não mostrar fotos de referência como se fossem mercadoria cadastrada. Nenhum cache/otimizador deve manter a foto pública depois de ocultar o produto em novas consultas.

Organizar o formulário em identificação, descrição, preços/variações, imagens e publicação, com instruções e erros associados aos campos. Descrição permite apenas a formatação sanitizada do ADR-0011; nunca executar HTML de entrada bruta no preview. Opções como cor/tamanho devem usar rótulos textuais e controles nativos, sem depender de amostras de cor. Selecionar uma combinação exibe seu preço/disponibilidade real; não sugerir compra ou reserva inexistente. Limites de upload, necessidade de salvar antes de editar fotos e conflitos de versão precisam ser claros sem perder rascunhos.

**Aviso de ambiente de teste (ADR-0014 H2).** No ambiente de teste hospedado, o admin e a vitrine mostram no topo de todas as páginas uma faixa de texto, "Ambiente de teste. Dados fictícios e sem pagamentos reais. Não informe dados pessoais verdadeiros.", com `role="note"`, fundo informativo e borda divisória dos tokens existentes. A informação está no texto, não só na cor.
