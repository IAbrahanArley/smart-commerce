# SC-044 e SC-045 — Base visual e páginas da vitrine: validação

Data: 2026-10-27. Base: SC-044 e SC-045 da [SPRINT_08.md](../product/SPRINT_08.md), decisão V12 do [ADR-0018](../architecture/adr/0018-storefront-v1.md) e a seção "Vitrine padrão V1" do [DESIGN.md](../design/DESIGN.md). As duas tarefas foram feitas na mesma branch para a busca do cabeçalho nunca apontar para uma página inexistente. A branch parte da SC-042 (PR #56), cuja API ela consome.

## O que mudou na vitrine

- **Base visual:**
  - novos valores dos tokens `--store-*`: branco, quase preto, destaque azul, desconto vermelho, WhatsApp verde e raios de 10 a 16 px com botões em pílula;
  - fonte Manrope pelo `next/font`.

  Carrinho, checkout e pedido herdaram o visual sem reescrita.
- **Moldura (`StoreFrame`) em todas as páginas da loja:**
  - cabeçalho com nome/logo, busca e carrinho;
  - menu de categorias em até 3 níveis com `<details>` nativo;
  - rodapé com categorias e atendimento;
  - WhatsApp flutuante;
  - abaixo de tudo, a faixa da plataforma.
- **Home (`/lojas/{loja}`):** atalhos de categoria, "Lançamentos" (8 mais recentes) e "Ver todos os produtos".
- **Categoria (`/c/{categoria}`) e busca (`/busca?q=`):**
  - caminho, título, contagem e ordenação (formulário GET);
  - filtros como links: subcategorias, marca, faixa de preço e opções de variante em chips; "Limpar filtros";
  - paginação numerada.

  Tudo fica na URL e funciona sem JavaScript. Sem busca, `/busca` é "Todos os produtos".
- **Produto (`/p/{endereço}`):** caminho pelas categorias, galeria, marca, preço "de" com selo de desconto, "em até N× no Mercado Pago" (nunca "sem juros"), notas de entrega e pagamento, descrição e até 8 relacionados. Endereço antigo leva ao atual.
- **Card:** foto 4:5, selo de desconto ou "Esgotado", marca, nome, preço "de" riscado, preço e parcelamento. O texto oculto para leitor de tela lê "De R$ X por R$ Y" e "20% de desconto".

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `lint`, `typecheck`, `build` (vitrine) | aprovados |
| `test:smoke` com checkout ligado e desligado | aprovados. O backend sintético ganhou a API de catálogo, e o smoke cobre: <ul><li>home com atalhos, menu e subcategoria</li><li>card com endereço, desconto, "Esgotado" e parcelamento, sem "sem juros"</li><li>busca no cabeçalho, rodapé e WhatsApp</li><li>categoria com título, contagem, filtros de marca, opção e preço (limite superior um centavo abaixo da faixa), página 2, "Limpar filtros" e filtro ativo marcado</li><li>busca com relevância e busca sem resultado</li><li>produto por endereço com marca, caminho, preço "de", parcelamento e relacionados</li><li>endereço antigo levando ao novo</li><li>categoria, endereço inexistente e endereço inválido com "Página não encontrada"</li><li>contatos inválidos descartados</li></ul> Carrinho, checkout, pedido, fotos e cabeçalhos de privacidade continuam cobertos como antes |
| Smoke de unidades | parser do menu (formato, endereço inválido, nome vazio, profundidade acima de 3, entrada ausente) e caminho da categoria |
| Navegador (backend sintético, 1280 e 375 px) | <ul><li>Manrope e paleta aplicadas, sem erros no console</li><li>menu em barra no desktop e painel "Categorias" no celular</li><li>categoria atual marcada no menu</li><li>filtros na lateral (desktop) e em "Filtrar produtos" (celular), sem ids repetidos</li><li>grade de 2 colunas em 375 px</li><li>produto com "De R$ 62,40 −20%" e "em até 6× no Mercado Pago"</li><li>sem rolagem horizontal em 375 px na home, na categoria e no produto</li></ul> |

Corrigido durante a verificação:
- **Ids repetidos:** os filtros aparecem duas vezes (lateral e painel do celular); passaram a usar ids com prefixo.
- **Título da aba:** na categoria, mostrava o endereço em minúsculas; agora começa com maiúscula.

## Limitações e pendências

- A captura de tela não foi possível (painel do navegador oculto). A verificação usou o DOM e os estilos calculados.
- O endereço antigo do produto redireciona dentro do HTML (status 200), não com 308. Isso fica para a SC-047.
- A rota `/produtos/{id}` continua respondendo, porque o carrinho posta nela. A URL canônica vem na SC-047.
- Banners, seções configuráveis da home, faixa de aviso e páginas institucionais dependem da SC-043. "Acompanhar pedido" depende da SC-046.
- Cada página que não é de catálogo faz uma chamada a mais à API para montar o menu; o cache da SC-047 resolve.
- Axe, leitor de tela real e zoom de 200% ficam para a SC-048.
