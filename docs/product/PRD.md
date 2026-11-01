# PRD — Smart Commerce (v0.1)
## Problema
Pequenos varejistas têm dificuldade para vender online e gerir estoque, pedidos, margens e caixa em ferramentas desconectadas.

## Visão
Permitir que cada empresa crie sua própria loja virtual independente e administre vendas e operação com indicadores e recomendações inteligentes.

## Público inicial
Pequenos varejistas brasileiros de produtos físicos (moda, calçados, acessórios e cosméticos). Não atender serviços, assinaturas de produtos, marketplaces agregadores ou atacado no MVP.

## Modelo de negócio
Assinatura SaaS paga pelo lojista; pagamentos das compras são processados para a conta Mercado Pago autorizada de cada lojista. Preços dos planos ainda serão validados. Não assumir split de pagamentos ou retenção de fundos pela plataforma.

## Papéis
- Operador da plataforma: suporte e administração, com acesso restrito e auditável.
- Proprietário da loja: configura empresa, produtos, pagamentos e gestão.
- Funcionário da loja: permissões limitadas (fase posterior ou mínimo necessário).
- Comprador: navega na loja, compra e acompanha pedido.

## MVP — funcionalidade
1. Cadastro/login e criação de empresa/loja com slug único.
2. Painel administrativo e vitrine pública por loja.
3. Catálogo: categorias, produtos, imagens, SKU, preço, custo e variantes simples.
4. Estoque: quantidade disponível, reserva, baixa e estorno; histórico de movimentações.
5. Carrinho, checkout com identificação do cliente, frete fixo ou retirada, pedidos e estados.
6. Mercado Pago: autorização de conta do lojista, Checkout Pro, webhooks validados, idempotência, reembolso controlado e conciliação mínima.
7. Financeiro: receitas por vendas, taxas, despesas manuais, CMV, margem de contribuição, contas a receber e fluxo de caixa simplificado.
8. Dashboard: vendas, pedidos, ticket médio, margem estimada, estoque baixo e itens sem giro.
9. IA: agente consultivo read-only sobre métricas auditáveis, com explicações e sugestões; sem movimentar dinheiro.
10. Configurações básicas da loja: nome, logo, contatos, política de frete e disponibilidade.

## Evolução aprovada após o MVP (Sprint 7)
Aprovada em 2026-10-26 ([ADR-0019](../architecture/adr/0019-cash-payables-receivables.md)): funcionário da loja (operador de caixa); contas financeiras com sessão de caixa, sangria e suprimento; contas a pagar; contas a receber e crediário; venda presencial registrada à mão ou por planilha, sem PDV, baixando o mesmo estoque da vitrine; livro de vendas online + loja física; fluxo de caixa projetado.

## Vitrine padrão V1 (Sprint 8)
Aprovada em 2026-10-27 com prioridade sobre a Sprint 7 ([ADR-0018](../architecture/adr/0018-storefront-v1.md), decisões técnicas e visuais aprovadas na mesma data): categorias e marcas, preço "de/por", busca com filtros, home configurável, páginas institucionais, acompanhar pedido e SEO, gratuitos para o lojista. A conta do cliente também será gratuita, em etapa posterior. Uma loja de extensões pagas (temas e módulos, cobrados do lojista) está em proposta e não altera "marketplace unificado" abaixo, que continua fora.

## Fora do MVP
Emissão fiscal, contabilidade oficial, cálculo tributário automático, split de pagamento, integração de frete nacional, múltiplos adquirentes, marketplace unificado, ERP completo, campanhas automáticas, recomendação autônoma de preços e domínio personalizado automático.

## Jornadas principais
**Lojista:** cria conta → cria empresa → configura loja → cadastra produto e estoque → conecta Mercado Pago → publica loja → acompanha pedido → visualiza margem e alertas.
**Comprador:** acessa URL da loja → escolhe produto/variante → adiciona ao carrinho → informa dados e entrega → paga → recebe confirmação e acompanha pedido.

## Requisitos não funcionais
- Isolamento multi-tenant testado em todas as consultas e comandos.
- Autenticação e autorização por função; auditoria de eventos sensíveis.
- Privacidade/LGPD: minimização de dados, controle de acesso e políticas de retenção a definir.
- Webhooks repetidos e fora de ordem não podem gerar baixa dupla de estoque.
- Operações de pagamento e estoque devem ser idempotentes.
- Observabilidade mínima: logs estruturados sem segredos, métricas e correlação de requisições.
- Datas com fuso explícito; valores monetários com precisão decimal.

## Métricas de validação
Tempo até publicar primeira loja; percentual de lojas com primeiro produto; percentual que conclui primeira venda; pedidos pagos corretamente; divergências de estoque; uso semanal do dashboard; disposição a pagar.

## Hipóteses a validar
- Interesse em pagar pela gestão inteligente, além da vitrine.
- Facilidade de conexão de contas Mercado Pago por lojistas.
- Custos reais de aquisição, suporte, hospedagem e IA.
- Requisitos legais, fiscais e termos do provedor de pagamentos.

## Critério de conclusão do MVP
Um lojista cria loja, cadastra produto, conecta sua conta de pagamento, recebe pedido de teste ponta a ponta, acompanha estoque e vê métricas financeiras coerentes, com testes de isolamento entre duas empresas.
