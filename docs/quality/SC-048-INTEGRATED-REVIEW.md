# SC-048 — Revisão integrada da Sprint 8

Data: 2026-10-31. Alvo: diff `main...feat/sc-048-integrated-review`, com os commits da SC-042 à SC-047 mais as correções desta revisão (cerca de 180 arquivos). Base: SC-048 da [SPRINT_08.md](../product/SPRINT_08.md) e o [ADR-0018](../architecture/adr/0018-storefront-v1.md).

## 1. Segurança e isolamento multi-tenant

**Como foi feita:** revisão independente e só de leitura, por um agente separado com o papel de [SECURITY.md](../agents/SECURITY.md). O relatório completo está fora do repositório, em `%LOCALAPPDATA%\SmartCommerce\security-reviews\SC-048-sprint8-diff.md`. Não há skill `differential-review` instalada, então o rastreamento foi manual, com `file:line`.

**Verificado e correto:**
- **Rotas novas do admin** (conteúdo, categorias adicionais, destaque, parcelamento):
  - ficam na cadeia autenticada com CSRF;
  - o acesso de OWNER é checado por `lockOwnedStore`;
  - todo SQL é filtrado por `tenant_id`;
  - V37 a V39 usam FKs compostas;
  - id de outro tenant dá o mesmo 404.
- **Rotas públicas:**
  - todas partem de `slug` com `status='PUBLISHED'` e `p.published`;
  - facetas, mais vendidos, relacionados, sitemap, endereço, banners e imagens, seções, páginas e empresa não misturam lojas.
- **SQL do catálogo:** montado só com fragmentos fixos; a entrada do usuário vai sempre como parâmetro e a busca é reduzida a `[a-z0-9]:*`.
- **Acompanhar pedido:**
  - o e-mail nunca vai na URL;
  - o 404 é uniforme;
  - o limite conta antes da leitura;
  - a resposta não traz dados de contato do comprador.
- **Revalidação:**
  - HMAC com janela de ±60 s e comparação em tempo constante;
  - só dispara após o commit, e a tentativa de um não-OWNER é revertida e não invalida nada;
  - a URL vem da configuração, sem seguir redirecionamentos e com timeout.
- **Conteúdo:**
  - cache com chave e etiqueta por loja;
  - JSON-LD escapa `<`;
  - HTML limpo por Jsoup e conferido de novo na vitrine;
  - links de banner só internos;
  - upload com regex restrita e imagens normalizadas com `nosniff`.

**Achados e disposição:**

| # | Achado | Gravidade | Disposição |
| --- | --- | --- | --- |
| 1 | Bloqueio dirigido: o limite contava só o número do pedido, então quem soubesse o número bloqueava o comprador por 1 h (`OrderAdmissionService`) | baixa, confirmado | **Corrigido:** o limite agora conta o número junto com o e-mail. Teste: 12 tentativas com e-mails errados e, em seguida, o comprador ainda consulta |
| 2 | Enumeração dos números sequenciais de um comprador conhecido, a partir de vários endereços | baixa, plausível | **Corrigido:** novo limite de 20 por hora por e-mail na loja, com teste |
| 3 | Sem `STOREFRONT_TRUST_FORWARDED_FOR` e segredo, todas as lojas dividem o limite do IP do servidor da vitrine | baixa, depende da configuração | **Mantido:** é o mesmo padrão do checkout. O ambiente hospedado já documenta `true`; registrado aqui |
| 4 | A busca livre contornava o cache e o enchia de chaves | baixa/média, plausível | **Corrigido:** buscas com `q` não entram no cache. Limite de taxa no catálogo público fica como pendência (borda/CDN) |
| 5 | O 308 do `proxy.ts` e o sitemap sem `STOREFRONT_PUBLIC_ORIGIN` usam o Host da requisição | baixa, plausível | **Mantido:** o caminho é sempre interno (sem open redirect) e o HTML é `no-store`. Configurar `STOREFRONT_PUBLIC_ORIGIN` em produção, como já documentado |
| 6 | `/interno/revalidar` lia o corpo antes de conferir a assinatura | baixa, plausível | **Corrigido:** sem cabeçalhos dá 401 e acima de 1.000 bytes dá 413, antes da leitura |
| 7 | Slug concatenado no JSON da revalidação | informativo | Seguro: o slug é sempre `[a-z0-9-]` |
| 8 | Pedido de loja suspensa continua consultável por número e e-mail | informativo | **Decisão de produto**, como já valia pelo token: o comprador mantém acesso ao próprio pedido |

## 2. UI/UX e acessibilidade

**Fonte:** [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md) (Vercel), lidas em 2026-10-31, sem commit visível. Escopo: os 34 arquivos `.tsx` e `.css` do diff da vitrine e do admin, por busca dirigida aos antipadrões, mais navegador com backend sintético. Regras de copy em inglês ("Title Case") não se aplicam: a interface é em português, com frases no início maiúsculo.

**Conforme:**
- **Foco:** contorno visível em tudo, com `:focus-within` na busca e `:focus-visible` nos demais.
- **Formulários:** todos os campos com rótulo; `autocomplete` e `inputMode` no e-mail e no número.
- **Movimento e números:** `prefers-reduced-motion` respeitado; `tabular-nums` nos preços.
- **Formatação:** datas e moeda com `Intl`.
- **Texto e toque:** `…` nos estados de carregamento, `touch-action: manipulation` e títulos com `text-wrap: balance`.
- **Navegação:** filtros e páginas na URL; o único carrossel tem botão de pausa.

**Navegador** (vitrine com backend sintético):
- **Estrutura:** em 7 páginas, `lang="pt-BR"`, um `h1`, link "Ir para o conteúdo", todos os links e botões com nome e todas as imagens com `alt`.
- **Teclado:** a ordem vai do link de pular, pelo cabeçalho, busca, atalhos e menu, até os controles do carrossel, sempre com contorno de foco.
- **Árvore de acessibilidade** (como um leitor de tela a vê):
  - o carrossel aparece como região "Destaques da loja";
  - os slides aparecem como grupos "N de M";
  - cada card é um `article` com o título do produto, e o preço "por" e o desconto são anunciados.
- **Zoom:** em 320 px (equivalente a 400%), nenhuma rolagem horizontal na home, categoria, produto, página institucional e "Acompanhar pedido".
- **Contraste dos tokens:** textos de 4,94:1 a 18,47:1; borda de controle 3,89:1; foco 6,5:1; texto sobre ação, destaque, WhatsApp e desconto de 5,28:1 a 18,47:1.

**Achados e disposição:**

| Achado | Disposição |
| --- | --- |
| A `<img>` do banner saía sem `width`/`height`, deslocando o layout | **Corrigido:** a API pública manda as dimensões das duas imagens (`HomeContentIT`) e a vitrine as usa (smoke) |
| Na busca, os títulos dos cards (h3) vinham logo depois do h1 | **Corrigido:** título oculto "Produtos" (h2) nas listagens; o estado vazio virou h3 |
| A logo sai sem `width`/`height` (do MVP) | **Pendente:** as dimensões da logo não são expostas; o deslocamento fica limitado pela altura máxima do CSS |
| O admin não avisa sobre alterações não salvas ao sair da página | **Pendente:** o admin nunca teve esse aviso; fica para outra tarefa |
| Dois `main#conteudo` no HTML transmitido | **Não é achado:** é o *fallback* do `loading.tsx` antes da troca por streaming; o DOM final tem um só |

## 3. Desempenho

O Lighthouse não está instalado, e instalá-lo seria dependência nova, sem autorização no `AGENTS.md`. A medição usou as APIs de desempenho do navegador sobre o **build de produção** (`next start`), com backend sintético local, depois de aquecer o cache. São números locais, sem metas:

| Página | TTFB | `load` | HTML | Requisições |
| --- | --- | --- | --- | --- |
| Home | 25 ms | 62 ms | 10 KB | 12 |
| Categoria | 26 ms | 83 ms | 8 KB | 11 |
| Produto | 38 ms | 89 ms | 8 KB | 12 |

Os recursos pesam 141 KB de JS comprimido (7 arquivos), 34 KB de CSS e 24 KB de fonte. O painel do navegador não reporta FCP nem LCP, que ficam sem medição. O custo por consulta ao backend foi apontado pelo revisor de segurança (catálogo com cerca de 10 consultas); hoje ele é amortecido pelo cache de 60 s.

## 4. Verificação

| Verificação | Resultado |
| --- | --- |
| Backend `verify` completo | aprovado: formatação, 115 testes de unidade e 194 de integração (1 ignorado, como antes), BUILD SUCCESS. A primeira tentativa falhou porque o Docker estava parado; rodou de novo depois de reiniciado |
| Vitrine: `lint`, `typecheck`, `build`, `test:smoke` | aprovados |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados |

## 5. Documentação atualizada

- ADR-0018: limites do "Acompanhar pedido" e ajuste da SC-048;
- ARCHITECTURE e BUSINESS_RULES (seção nova "Vitrine padrão V1");
- DESIGN: conteúdo, checkout e revisão de acessibilidade;
- README: rotas e variáveis da vitrine;
- status na SPRINT_08.

DATA_MODEL (V33 a V39) e ADR-0010 já vinham atualizados das tarefas anteriores.

## 6. Pendências da Sprint 8

- **Ferramentas não executadas:** Axe, leitor de tela real (NVDA ou VoiceOver) e Lighthouse, porque não estão instaladas; a instalação depende de aprovação.
- **Validador de dados estruturados do Google:** só depois do deploy.
- **Limite de taxa no catálogo público:** fica para a borda/CDN.
- **Dimensões da logo e aviso de alterações não salvas no admin.**
- **Validação real do Mercado Pago:** confirmação, webhook e reembolso, já pendente desde o MVP.
- **Antes do deploy:** confirmar a extensão `unaccent` no PostgreSQL hospedado e configurar `STOREFRONT_PUBLIC_ORIGIN` e os segredos de revalidação.
