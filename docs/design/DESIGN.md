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
| Vitrines futuras | Temas dos lojistas têm contrato separado; nome, cores e conteúdo da loja não alteram admin ou autenticação. |

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
- **Edição em duas colunas:** a partir de 64rem, coluna principal (identificação, descrição, fotos e variações) e lateral com publicação e estoque. Em telas menores, uma coluna na ordem do DOM.

## Repaginação 2026-10 (referência Dashtail)

Aprovada pelo responsável em 2026-10-20. O template comercial Dashtail v1.3.0 (ThemeForest; Next 14, Tailwind 3 e shadcn/Radix) foi usado **somente como referência visual e estrutural**. Nenhum código, ícone, ilustração, texto ou dependência foi copiado, porque a adoção de Tailwind/Radix contrariaria a política de dependências. **Licença (2026-10-20):** o responsável informou ter uma licença que permite usar o template em qualquer projeto, então copiar código ou recursos dele é permitido. Mesmo assim, uma cópia não pode trazer dependência nova sem aprovação e deve ser adaptada aos tokens e componentes locais. Paleta violeta, fonte Inter, tema escuro, customizador, RTL, busca, notificações, gráficos e toasts do template não foram adotados.

Padrões adotados com CSS e componentes locais:

- **Admin:** moldura com barra superior e navegação lateral agrupada, com ícones SVG próprios (`components/icons.tsx`) e item atual preenchido. Cards com cabeçalho separado por divisor e sombra sutil. Visão geral da loja com três cards de resumo: vitrine, produtos cadastrados/selecionados e pedidos aguardando pagamento. O último mostra "Mais de 20" quando a API indica outra página. Tabelas com hover de linha e selos com ponto e texto. Diálogo de confirmação nativo substitui `window.confirm` ao publicar/ocultar loja e produto, remover foto, mudar o tipo do produto e recarregar versões. As guardas de saída com alterações não salvas continuam nativas, porque precisam ser síncronas.
- **Vitrine:** cabeçalho com borda e sombra, fixo ao rolar a partir de 40rem, com `scroll-padding-top` para não encobrir o foco; abaixo disso, não é fixo. O link do carrinho mostra a quantidade de unidades lida só do cookie da loja. Cards de produto com borda, sombra e elevação no hover, sem movimento com `prefers-reduced-motion`. Checkout e página do pedido com etapas Carrinho → Dados e entrega → Pedido registrado: `aria-current="step"` e "(concluída)" em texto. A situação do pedido tem uma linha do tempo curta (registro e situação atual), sem etapas futuras fictícias.

Validação em 2026-10-20: lint, typecheck, build e test:smoke das duas aplicações. No navegador do app (Chromium), em 1024 CSS px e 375 CSS px, com backend sintético local: moldura, menu móvel, cards de resumo, tabelas, diálogo e fluxo da vitrine até o pedido. O diálogo foi conferido pelo DOM: abre centrado, foca "Cancelar", fecha com Escape e devolve o foco. Revisão com as Web Interface Guidelines atuais, consultadas na mesma data. Com 1024 CSS px, a tabela de produtos rola dentro do card, sem rolagem da página. Axe, leitor de tela e zoom manual não foram executados; não há alegação de conformidade WCAG completa.

Tokens novos: `--sc-shadow-surface`, `--sc-shadow-overlay`, `--store-shadow-surface`, `--store-shadow-raised` e `--store-header-height`. Nenhuma cor, fonte ou raio-base mudou; o aviso "Ambiente de teste" e a separação admin/vitrine foram preservados.

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

O briefing fornecido pelo responsável autoriza a apresentação em `/`, demonstrações isoladas em `/admin/produtos` e `/loja/produto` e galeria em `/componentes`. Essas rotas não existiam no checkout: foram criadas sem substituir telas operacionais. A galeria é uma nova amostra de componentes locais; a antiga home era somente uma entrada administrativa.

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

## Conteúdo do produto autorizado

**Ritmo das telas operacionais (2026-10-22).** O contêiner de página separa blocos irmãos por 24 px, usando os tokens existentes; grades internas conservam seu próprio gap. Filtros e navegação de competência usam superfícies e controles locais. Estados de carregamento, vazio e erro oferecem título, explicação e ação pertinente em painel reutilizável. Listas de estoque separam nome, variações e disponibilidade em linhas legíveis. Aplicação dos padrões existentes, sem mudança de identidade, cores, tipografia ou biblioteca.

Na evolução do catálogo de 2026-10-17, fotos reais enviadas pelo lojista podem compor capa e galeria sem trocar a identidade da plataforma. Usar dimensões explícitas, proporção consistente com `object-fit: contain` para não cortar o produto, texto alternativo obrigatório e navegação por teclado. Não mostrar fotos de referência como se fossem mercadoria cadastrada. Nenhum cache/otimizador deve manter a foto pública depois de ocultar o produto em novas consultas.

Organizar o formulário em identificação, descrição, preços/variações, imagens e publicação, com instruções e erros associados aos campos. Descrição permite apenas a formatação sanitizada do ADR-0011; nunca executar HTML de entrada bruta no preview. Opções como cor/tamanho devem usar rótulos textuais e controles nativos, sem depender de amostras de cor. Selecionar uma combinação exibe seu preço/disponibilidade real; não sugerir compra ou reserva inexistente. Limites de upload, necessidade de salvar antes de editar fotos e conflitos de versão precisam ser claros sem perder rascunhos.

**Aviso de ambiente de teste (ADR-0014 H2).** No ambiente de teste hospedado, o admin e a vitrine mostram no topo de todas as páginas uma faixa de texto, "Ambiente de teste. Dados fictícios e sem pagamentos reais. Não informe dados pessoais verdadeiros.", com `role="note"`, fundo informativo e borda divisória dos tokens existentes. A informação está no texto, não só na cor.
