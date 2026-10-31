# Regras de negócio iniciais
- Uma empresa pode possuir uma loja no MVP; estrutura deve permitir expansão futura sem assumir múltiplas lojas agora.
- Slug público único e validado; produtos e pedidos sempre pertencem a um tenant.
- Produto possui preço de venda, custo de aquisição informado e moeda BRL. Custo ausente implica margem indisponível, nunca lucro presumido.
- Disponível para venda = estoque físico - reservado (sem permitir negativo no MVP).
- Pedido nasce PENDING_PAYMENT; estoque é reservado com expiração. Pagamento confirmado gera baixa; cancelamento/expiração libera reserva. Definir tempo de reserva antes da implementação.
- Status de pagamento não deve ser inferido apenas pelo redirect do checkout.
- Webhook deve ser autenticado, idempotente e confirmado por consulta ao provedor quando aplicável.
- Pedido registra snapshot de preço, custo, taxa, frete e desconto quando disponíveis; mudanças posteriores no catálogo não alteram histórico.
- Faturamento, recebíveis, caixa e lucro são conceitos distintos; dashboard deve identificar competência e data de liquidação.
- Taxas, impostos e devoluções devem aparecer separadamente quando disponíveis; não apresentar lucro líquido definitivo sem dados completos.
- Reembolso exige permissão e confirmação; eventos devem ser auditáveis.
- Dados do comprador não são compartilhados entre lojas.
- A IA consulta apenas dados autorizados do tenant e não altera pedidos, valores ou estoque no MVP.

## Vitrine padrão V1 (Sprint 8, [ADR-0018](../architecture/adr/0018-storefront-v1.md))

- **Gratuidade:** tudo da vitrine padrão é gratuito. Temas e módulos pagos são V2.
- **Categorias:** árvore de até 3 níveis. Uma categoria oculta some do menu e da navegação, mas os produtos dela continuam no catálogo. Um produto tem uma categoria principal e até 10 adicionais.
- **Endereço do produto:** cada produto tem um endereço próprio na loja. O endereço antigo redireciona para o novo por 90 dias.
- **Preço "de":** só aparece quando é maior que o preço, e o valor cobrado é sempre o preço. O desconto é arredondado para baixo.
- **Parcelamento:** "em até N× no Mercado Pago" só aparece com a conta conectada, nunca como "sem juros" e sem valor de parcela, porque os juros dependem da conta do lojista.
- **Mais vendidos:** unidades em pedidos pagos dos últimos 90 dias.
- **Conteúdo do lojista:**
  - links de banner só levam a páginas da própria loja;
  - textos institucionais passam pelo mesmo sanitizador da descrição;
  - o CNPJ tem os dígitos verificadores conferidos;
  - até 5 banners e 8 seções.
- **Acompanhar pedido:**
  - por número e e-mail da compra;
  - combinação errada ou inexistente dá a mesma resposta;
  - limites por hora: 20 consultas por endereço, 10 por número de pedido com o mesmo e-mail e 20 por e-mail na loja;
  - a resposta não mostra telefone nem endereço do comprador e não permite pagar.
- **Atualização da vitrine:** ocultar ou alterar algo vale para a próxima visita assim que o backend avisa a vitrine; sem esse aviso, vale em até 60 s. Uma foto já publicada pode continuar acessível pelo endereço por até 1 dia.
