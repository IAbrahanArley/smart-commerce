# SC-046 — Carrinho, checkout e acompanhar pedido: validação

Data: 2026-10-28. Base: SC-046 da [SPRINT_08.md](../product/SPRINT_08.md) e a decisão V10 do [ADR-0018](../architecture/adr/0018-storefront-v1.md). A branch parte da SC-043. Os desvios estão no ADR, em "Implementação e desvios registrados".

## O que mudou

- **Acompanhar pedido (`/lojas/{loja}/acompanhar-pedido`):**
  - o comprador informa o número do pedido ("1024", "#1024" ou "nº 1024") e o e-mail da compra;
  - maiúsculas e espaços no e-mail não importam;
  - o formulário é uma server action e funciona sem JavaScript;
  - o resultado mostra situação, prazo da reserva, itens, entrega e valores, e recebe o foco;
  - não há botão de pagar: a página orienta a usar o navegador da compra ou falar com a loja;
  - atalho "Acompanhar pedido" no cabeçalho; no celular, só o ícone, com o nome acessível mantido;
  - a rota e o atalho só existem onde o checkout existe (D6).
- **API:** `POST /api/public/stores/{loja}/orders/lookup`, com `{ number, email }` no corpo.
- **Checkout:**
  - moldura reduzida: nome da loja e contato (WhatsApp, telefone ou e-mail), sem busca, menu de categorias ou atalho do carrinho;
  - rodapé só com as páginas institucionais e os dados legais;
  - resumo lateral fixo no desktop.
- **Carrinho:** foto de cada produto ao lado do nome, inclusive no celular.
- **Página do pedido:** passa a usar o mesmo resumo da consulta. A nota final agora explica que número e e-mail permitem acompanhar o pedido em qualquer aparelho.

As regras do fluxo do carrinho ao pagamento não mudaram: reserva, idempotência, cotação no backend e Checkout Pro.

## Segurança e privacidade

- **E-mail fora de URLs e logs:** o e-mail vai no corpo do POST, nunca na URL. A tabela de limites só guarda HMACs, sem IP, número ou e-mail em claro.
- **Limites:**
  - 20 consultas por hora por endereço assinado;
  - 10 por hora por número de pedido da loja;
  - a tentativa conta antes da leitura, exista o pedido ou não;
  - acima do limite, 429 com `Retry-After`.
- **Resposta uniforme:** e-mail errado, número inexistente, pedido de outra loja e loja inexistente dão o mesmo 404, com o mesmo corpo. Pedido anonimizado nunca é encontrado.
- **Projeção:** a resposta não traz telefone, e-mail, endereço do comprador, SKU nem custo. O "texto da entrega" é o da loja (instruções de retirada ou área do frete), não o endereço do comprador.
- **Sem acesso ao pagamento:** `payable` vem sempre falso na consulta, e a vitrine também o força para falso.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `OrderCheckoutIT` (14, 2 novos) | aprovado. Cobre: <ul><li>consulta com e-mail em maiúsculas e com espaços, "#1" e "nº 1"</li><li>itens iguais aos do pedido, sem endereço, telefone, e-mail, SKU ou custo; `payable` falso; `no-store`</li><li>e-mail errado, número inexistente, pedido de outra loja e loja inexistente com 404 de corpo idêntico</li><li>número e e-mail inválidos dão 400</li><li>pedido anonimizado dá 404</li><li>10 tentativas por número, de endereços diferentes, e depois 429</li><li>20 por endereço em números diferentes, depois 429; outro endereço segue livre</li><li>chaves do limite são HMAC</li></ul> |
| Backend `verify` completo | aprovado: formatação, 115 testes de unidade e 191 de integração (1 ignorado, como antes), BUILD SUCCESS |
| Vitrine: `lint`, `typecheck`, `build` | aprovados |
| Vitrine: `test:smoke` | aprovado. Cobre: <ul><li>checkout sem busca, menu e carrinho, com contato</li><li>consulta sem JavaScript; dados inválidos não chegam ao backend</li><li>pedido encontrado sem botão de pagar e sem dados do comprador</li><li>número com "#" e e-mail com espaços normalizados no corpo</li><li>mensagens de "não encontrado" e de limite</li><li>sem checkout, sem rota nem atalho de consulta</li><li>unidades: número do pedido, validação e parser do pedido</li></ul> |
| Navegador (backend sintético, 1024 e 375 px) | <ul><li>carrinho com foto e resumo</li><li>checkout com cabeçalho reduzido e "Dúvidas? WhatsApp"</li><li>consulta com o resultado focado</li><li>sem rolagem horizontal em 375 px</li></ul> |

Corrigido durante a verificação:
- **Título repetido:** o cabeçalho do checkout repetia "Finalizar compra"; foi removido.
- **Foto do carrinho no celular:** ficava empilhada acima do nome; agora fica ao lado.

## Limitações e pendências

- Sem navegador com backend real: o fluxo completo com Mercado Pago real continua pendente, como já registrado.
- O limite por número de pedido pode atrasar o próprio comprador se alguém insistir no número dele (janela de uma hora).
- O carrinho não ganhou outras mudanças de layout além da foto; ele já seguia os tokens novos desde a SC-044.
- A revisão completa de acessibilidade (Axe, leitor de tela, zoom) é da SC-048.
