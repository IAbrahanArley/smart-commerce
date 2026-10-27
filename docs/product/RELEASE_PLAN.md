# Plano de entrega do MVP

Registrado em 2026-10-26, a pedido do responsável. Ele organiza o caminho até a primeira entrega em produção. Depois dela, tudo passa a ser melhoria. Base: [PRD.md](PRD.md) ("MVP — funcionalidade" e "Critério de conclusão do MVP"), sprints 1 a 6 e os pendentes de cada validação.

Entrega significa:
- lojistas reais conseguem criar loja, vender e receber pelo Mercado Pago em produção;
- o critério de conclusão do PRD é atendido;
- nada de teste ou demonstração fica exposto.

## Estado em 2026-10-26

**Implementado:**
- identidade e lojas;
- catálogo com variantes e imagens;
- estoque com reservas;
- vitrine, carrinho e pedidos;
- Mercado Pago, com OAuth, Checkout Pro por preferências, webhook, conciliação e reembolso;
- financeiro, despesas e painel;
- alertas;
- assistente com fontes e "Explicar", desligado por padrão.

**No ambiente de teste hospedado** (Render, Vercel e Brevo), o Checkout Pro abriu com uma preferência real.

## Bloco 1 — Lacunas do MVP (PRD)

Itens do PRD que ainda não existem ou estão incompletos, em ordem de execução:

| Item | Por que | Observação |
| --- | --- | --- |
| **1a. Recuperação de senha** (implementada em 2026-10-26, [validação](../quality/MVP-1A-PASSWORD-RESET.md)) | sem ela, um lojista que esquece a senha perde o acesso; é indispensável em produção | reaproveita o e-mail transacional (Brevo) e o padrão de token de uso único da ativação |
| **1b. Configurações da loja: logo e contatos** (implementada em 2026-10-26, [validação](../quality/MVP-1B-STORE-PROFILE.md)) | PRD item 10 (nome, logo, contatos, política de frete e disponibilidade); hoje só há o nome e a entrega | logo pelo mesmo armazenamento das imagens de produto; contatos (e-mail, WhatsApp, Instagram) na vitrine |
| **1c. Categorias de produto** (implementada em 2026-10-26, [validação](../quality/MVP-1C-CATEGORIES.md)) | PRD item 3; o catálogo não tem categorias | categoria simples por loja e filtro na vitrine, sem hierarquia profunda |
| **1d. Páginas legais** (estrutura e rascunho em 2026-10-26, [validação](../quality/MVP-1D-LEGAL.md); faltam razão social, CNPJ e validação do texto) | LGPD e termos (PRD, hipóteses e requisitos não funcionais) | termos de uso e política de privacidade da plataforma; aviso no cadastro e no checkout. **O texto precisa de validação do responsável**; a estrutura pode vir antes |
| **1e. Planos de assinatura (SaaS)** (planos aprovados em 2026-10-26, [PRICING_PROPOSAL.md](PRICING_PROPOSAL.md); cards na landing) | PRD: assinatura paga pelo lojista; sem ela não há receita | **implementado, aguardando validação real** ([ADR-0017](../architecture/adr/0017-saas-billing-stripe.md), adotado): módulo `billing` (SC-031 a SC-033): teste grátis de 14 dias, limites por plano, cota de IA (Sonnet 5.5), assinatura pela Stripe e telas "Plano e cobrança". Falta o responsável criar a conta, os preços e o webhook na Stripe e validar em modo de teste. Pix Automático pela Asaas em cerca de 6 meses |
| **1f. Vitrine padrão V1 (Sprint 8)** (incluída em 2026-10-27 por decisão do responsável, como exceção; [SPRINT_08.md](SPRINT_08.md), [ADR-0018](../architecture/adr/0018-storefront-v1.md)) | a vitrine é o que vende o produto, e a versão no padrão de loja grande tinha ficado de fora deste plano | SC-041 a SC-048; evolui os blocos 1b e 1c (dados da loja e categorias). Conta do cliente, loja de extensões e Sprint 7 ficam para depois da entrega |

## Bloco 2 — Remover o que é de teste e preparar a produção

- **Páginas de demonstração do admin** (removidas em 2026-10-26, bloco 2a, [validação](../quality/MVP-2A-PRODUCTION-PREP.md)): `/componentes`, `/admin/produtos` e `/loja/produto`. A landing (`/`) continua, com o texto atualizado.
- **Marcações do ambiente de teste** (2026-10-26, blocos 2a e 2b): o banner e o checkout sem pagamento ficam restritos a `hosted-test`. Os textos de "desenvolvimento" e "teste" saíram dos rodapés. O D6 foi revisado: em produção, o checkout só liga com pagamento real.
- **Configuração de produção:**
  - Mercado Pago com credenciais de produção (`MERCADO_PAGO_TEST_TOKEN=false`);
  - webhook com o evento "Pagamentos" e `MERCADO_PAGO_NOTIFICATION_URL`;
  - assistente desligado até a avaliação.
- **Observabilidade mínima (requisito não funcional do PRD):** identificador de correlação por requisição nos logs e conferência de que nenhum log leva segredo ou dado pessoal.
- **Revisão completa da interface:** admin e vitrine nas larguras de referência, com as pendências de acessibilidade das sprints 5 e 6 (leitor de tela, zoom e espaçamento).

## Bloco 3 — Validações reais pendentes

| Validação | Depende de | Registro |
| --- | --- | --- |
| Pagamento real em sandbox com o comprador de teste: confirmação por webhook e por conciliação, e reembolso | responsável, com as contas de teste | [SPRINT_04.md](SPRINT_04.md), lembrete |
| SC-018: taxa, líquido e liberação do Mercado Pago | um pagamento real aprovado | [SPRINT_05.md](SPRINT_05.md) |
| SC-029: avaliação paga do assistente (até US$ 5) | responsável, com a própria chave | [SC-029-EVALUATION.md](../quality/SC-029-EVALUATION.md) |
| SC-030: revisão integrada da Sprint 6 | SC-029 | [SPRINT_06.md](SPRINT_06.md) |
| Riscos registrados na SC-023 (CMV de pedido pago sem estoque, retenção das descrições de despesa) | decisão do responsável | [SC-023-VALIDATION.md](../quality/SC-023-VALIDATION.md) |

## Bloco 4 — Infraestrutura de produção (cada item exige aprovação)

- **Banco:** o Postgres gratuito do Render expira cerca de 30 dias após a criação (por volta de 2026-11-18). Para produção, é preciso um banco pago com backup.
- **Ambiente de produção separado** do ambiente de teste hospedado: outro serviço, outro banco e outros segredos.
- **Domínio** da plataforma, com o remetente de e-mail verificado nesse domínio (Brevo).
- **Revisão jurídica:**
  - LGPD: retenção e dados enviados a terceiros (Mercado Pago, Anthropic);
  - termos do Mercado Pago para plataformas.

## Bloco 5 — Entrega

1. Roteiro ponta a ponta em produção, com uma loja do responsável: cadastro, produto, conexão do Mercado Pago, pedido pago, estoque, financeiro e reembolso.
2. Teste de isolamento entre duas empresas no ambiente de produção.
3. Registro da entrega e do que fica como melhoria.

## Depois da entrega

Tudo passa a ser melhoria e entra no [ROADMAP.md](ROADMAP.md) com prioridade própria. Por exemplo:
- ligar o assistente em produção, depois da SC-029 e de uma aprovação separada;
- evoluções da vitrine além da V1 (conta do cliente, loja de extensões, temas);
- frete nacional;
- domínio próprio por loja.
