# SC-017 — Revisão integrada da Sprint 4: validação

Data: 2026-10-21. Base: [SPRINT_04.md](../product/SPRINT_04.md), [SECURITY.md](../agents/SECURITY.md), [QA.md](../agents/QA.md) e [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md).

**Estado:** a revisão integrada está concluída e os achados relevantes foram corrigidos. **A validação ponta a ponta no sandbox continua pendente**: depende de uma conta nova no Mercado Pago, com usuários de teste vendedor e comprador (checklist no lembrete do [SPRINT_04.md](../product/SPRINT_04.md)). Enquanto isso, o aceite "fluxo de teste ponta a ponta concluído" da SC-017 não está cumprido e a Sprint 4 não está fechada.

## O que foi feito

1. **Revisão de segurança** independente (subagente só leitura) sobre todo o diff da Sprint 4. O modelo de ameaças e a disposição de cada achado estão em [SC-017-PAYMENTS-THREAT-MODEL.md](../security/SC-017-PAYMENTS-THREAT-MODEL.md). Nada crítico ou alto; 2 médios e 3 baixos corrigidos, 1 baixo mitigado e 2 propostos como risco aceito (R10, R12).
2. **Revisão de QA, UI e acessibilidade** independente (subagente só leitura) das telas da Sprint 4. Fonte: Web Interface Guidelines (vercel-labs, `command.md`), consultada em 2026-10-21, mais WCAG 2.2 AA e [DESIGN.md](../design/DESIGN.md). Quando a diretriz conflita com o design system, o DESIGN.md prevalece. Nada alto; achados abaixo.
3. Correções, testes novos e reexecução de todos os checks.

## Achados de QA, UI e acessibilidade e disposição

| # | Achado | Arquivo | Sev. | Disposição |
| --- | --- | --- | --- | --- |
| 1 | Erro de reembolso exibido como sucesso quando o pedido recarregava como reembolsado | `admin-web/src/components/order-refund.tsx` | média | **corrigido:** mensagens vão para o aviso da página, com o papel e a cor certos |
| 2 | Foco perdido ao fechar o formulário ou o resumo do reembolso | `order-refund.tsx` | média | **corrigido:** o foco volta ao botão que abriu a etapa |
| 3 | Mensagem de reembolso sumia quando o pedido recarregava | `order-refund.tsx`, `order-detail.tsx` | média | **corrigido:** mesmo aviso da página (fora do card) |
| 4 | Alerta "pago sem estoque" com `role="alert"` em conteúdo estático, mantido depois do reembolso | `order-detail.tsx` | média | **corrigido:** sem `role`, e só em pedido `PAID` |
| 5 | "Conectar Mercado Pago" reabilitado enquanto o navegador já ia para o provedor | `admin-web/src/components/store-payments.tsx` | média | **corrigido:** fica desabilitado até sair da página |
| 6 | Comprador sem explicação quando o Checkout Pro não abre logo após o pedido | `storefront-web/src/lib/checkout-actions.ts`, página do pedido | média | **corrigido:** indicação `?pagamento=nao-aberto` (sem dado sensível, nunca usada como estado de pagamento) e aviso focado acima do botão |
| 7 | Estado do provedor cru e em inglês; rótulo "Order" | `order-detail.tsx`, `admin-web/src/lib/orders.ts` | baixa | **corrigido:** rótulo em português com o código entre parênteses; "Pedido no Mercado Pago" |
| 8 | Erro da escolha de estoque fracamente associado | `order-refund.tsx` | baixa | **corrigido:** `aria-describedby` em cada opção e erro anunciado |
| 9 | Texto da escolha de estoque inconsistente; "Revisar" com estilo destrutivo | `order-refund.tsx` | baixa | **corrigido** |
| 10 | Possível anúncio duplo (alerta e foco) na vitrine | `storefront-web/src/components/payment-form.tsx` | baixa | **corrigido:** só foco |
| 11 | Título fixo "Pagamento em confirmação" também para pedido pago ou reembolsado | página de retorno | baixa | **corrigido:** "Retorno do pagamento" |
| 12 | Aspas retas no texto | `admin-web/src/lib/payments.ts` | baixa | **corrigido:** aspas curvas |

## Testes executados e pendentes

| Verificação | Situação |
| --- | --- |
| Backend `mvnw verify` (unitários, integração PostgreSQL, ArchUnit, migrações V17–V21) | executada/aprovada: 81 unitários e 127 de integração |
| Admin e vitrine: `lint`, `typecheck`, `build`, `test:smoke` | executada/aprovada |
| Testes novos desta revisão: conexão perdida reconciliada após reconexão; `cannot_refund_order` como falha e reembolso não consultado depois de 30 dias; `state` consumido com vínculo perdido | executada/aprovada |
| Fluxo real no sandbox: conectar, pagar com cartão e Pix, webhook "Simular", conciliação, reembolso | **bloqueada** (conta Mercado Pago) |
| Navegador: telas de pagamento, retorno e reembolso; teclado, leitor de tela, zoom de 200% e reflow de 320 px; Axe | **pendente** (Playwright e Axe são propostas ainda não aprovadas, ver QA.md) |
| Reembolso concorrente em duas abas pelo HTTP | **pendente** (coberto no serviço por chave única e lock; sem teste HTTP dedicado) |

**Ambiente:** Windows 11, JDK Corretto 21.0.9, Node 24.12, Docker Desktop (Testcontainers com PostgreSQL). Nenhum navegador ou leitor de tela foi usado.

## Próximo passo

Com a conta nova, seguir o checklist do lembrete no [SPRINT_04.md](../product/SPRINT_04.md), registrar o resultado nas validações SC-012 a SC-016 e fechar o aceite ponta a ponta da SC-017. Antes de produção: decidir R10 a R12 e os riscos herdados da Sprint 3 (R1, R5).
