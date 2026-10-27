# Roadmap inicial
1. Sprint 1: fundação, identidade e tenant.
2. Sprint 2: catálogo, variantes e estoque.
3. Sprint 3: vitrine, carrinho, pedidos, frete básico. Vitrine de consulta já entregue ([ADR-0010](../architecture/adr/0010-public-storefront.md)); carrinho, pedido `PENDING_PAYMENT` com reserva, frete fixo/retirada e pedidos no admin entregues conforme [SPRINT_03.md](SPRINT_03.md) (SC-005 a SC-011) e o [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md) (adotado); **concluída em 2026-10-20**.
4. Sprint 4: Mercado Pago sandbox, webhooks, conciliação e estornos. Plano proposto em [SPRINT_04.md](SPRINT_04.md), com as decisões P1–P11 do [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md) adotadas em 2026-10-19.
4a. Estado da Sprint 4 (2026-10-21): SC-012 a SC-017 implementadas e revisadas em sandbox com servidores falsos; a validação ponta a ponta com uma conta de teste real do Mercado Pago está pendente e fecha a sprint.
5. Sprint 5: financeiro, despesas, CMV, margens e dashboard. Plano proposto em [SPRINT_05.md](SPRINT_05.md), com as decisões F1–F10 do [ADR-0015](../architecture/adr/0015-finance-dashboard.md) aprovadas em 2026-10-21.
6. Sprint 6: agente consultivo read-only, alertas e explicabilidade. Plano proposto em [SPRINT_06.md](SPRINT_06.md), com as decisões A1–A12 do [ADR-0016](../architecture/adr/0016-consultative-agent.md) aprovadas em 2026-10-23.
7. Sprint 7: caixa, contas a pagar e a receber, venda presencial sem PDV (manual ou por planilha, no mesmo estoque da vitrine), crediário e livro de vendas online + loja física. Plano em [SPRINT_07.md](SPRINT_07.md), com as decisões C1–C14 do [ADR-0017](../architecture/adr/0017-cash-payables-receivables.md) aprovadas em 2026-10-26; só planejamento registrado, implementação ainda não autorizada.
7a. Sprint 8 (faz parte da **entrega do MVP**, bloco 1f do [RELEASE_PLAN.md](RELEASE_PLAN.md), e vem antes da Sprint 7; aprovado em 2026-10-27): vitrine padrão V1 no nível de uma loja grande, gratuita. Plano em [SPRINT_08.md](SPRINT_08.md), decisões V1–V13 e direção visual do [ADR-0018](../architecture/adr/0018-storefront-v1.md) aprovadas em 2026-10-27; só planejamento registrado, implementação ainda não autorizada; contexto em [VITRINE_REPLANEJAMENTO.md](VITRINE_REPLANEJAMENTO.md) e referência funcional em [BENCHMARK_VITRINE_SPORTLIFE.md](BENCHMARK_VITRINE_SPORTLIFE.md). Depois: conta do cliente (gratuita) e loja de extensões pagas (depende da cobrança da plataforma).
8. Entrega do MVP: plano em [RELEASE_PLAN.md](RELEASE_PLAN.md) (registrado em 2026-10-26), com as lacunas do PRD, a preparação de produção, as validações reais pendentes e a infraestrutura.
9. Beta: validação com lojistas, segurança, suporte e custos.

Roadmap adaptativo: priorização e datas revistas após cada sprint.

## Refinamento do catálogo — imagens de produtos

Solicitação registrada em 2026-10-22, para priorização após a revisão integrada da Sprint 5. Upload, capa, ordenação, texto alternativo e galeria já existem conforme ADR-0011; esta evolução melhora sua presença no admin.

- Mostrar a foto de capa nas listas de produtos e um estado explícito “Sem foto”, mantendo identificação e preço legíveis em telas pequenas.
- Tornar o acesso à gestão das fotos fácil de encontrar no cadastro/edição e indicar qual imagem é a capa.
- Reutilizar os endpoints, limites e regras de publicação existentes; não adicionar armazenamento externo, biblioteca ou imagens fictícias.
- Aceite previsto: produto com/sem capa, troca de capa, upload inválido, teclado, layout móvel e isolamento A/B; ocultar produto/loja deve continuar impedindo novas consultas públicas às imagens.

Planejamento registrado; implementação e evidências ficam em tarefa própria, sem incluir essa funcionalidade na entrega atual de sessão e espaçamento.
