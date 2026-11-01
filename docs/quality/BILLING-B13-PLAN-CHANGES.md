# ADR-0017 B13 — Troca de plano e cancelamento no painel

Data: 2026-11-01. Pedido do responsável depois da primeira assinatura real em modo de teste: subida, descida e cancelamento de plano dentro da plataforma e tela de plano renovada. Decisões e regras em [ADR-0017, B13](../architecture/adr/0017-saas-billing-stripe.md).

## Critérios de aceite

- Subida (plano maior e ciclo igual ou mais longo) mostra o valor exato a cobrar, vale na hora e cobra a diferença proporcional; cartão recusado não muda o plano.
- Descida (plano menor, ciclo mais curto ou mistura) não cobra nada, vale no fim do período e pode ser desfeita.
- Cancelamento vale no fim do período e pode ser revertido; cancelar descarta uma descida agendada.
- O portal da Stripe fica só para cartão e recibos.
- O dono vem da sessão; um dono não alcança a assinatura de outro; o valor nunca vem do navegador.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `PlanChangeTest` (regra de tempo) | 3 aprovados |
| `StripePlanChangeIT` (Postgres real, Stripe falsa) | 6 aprovados: subida com prévia e cobrança; confirmação sem data ou valor, com data velha ou futura, com outro valor ou com outro plano recusada sem chamada de cobrança; segundo clique sem nova cobrança; ciclo mais longo no mesmo plano; pagamento pendente recusado; cartão recusado com fatura anulada; descida agendada, outra mudança e cancelamento recusados até desfazer; cancelamento e retomada; isolamento entre donos e anônimo |
| `StripeBillingIT`, `BillingIT` e unitários de cobrança | aprovados (sem regressão) |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração) | aprovado: 118 unitários e 202 de integração, 0 falhas (1 ignorado, já existente) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados; o smoke cobre os novos leitores (`parseSubscription`, `parsePreview`), `moneyText`, mensagens dos novos códigos e `cycleTotalText` |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório, com as respostas do contrato acima):
- assinatura ativa: card "Seu plano" com uso, card "Pagamento", seletor de ciclo, "Plano atual" no card certo e seção de cancelamento;
- subida: diálogo "Mudar para Escala mensal?" com "Cobrança agora: R$ 113,67", foco inicial em "Cancelar"; ao confirmar, o plano passa a Escala e a mensagem diz o valor cobrado;
- descida: diálogo sem cobrança, com a data e o aviso de lojas acima do limite; depois, aviso "Em 21/11/2026, seu plano muda para Essencial mensal", selo "Agendado" e "Desfazer mudança" funcionando;
- cancelamento: diálogo destrutivo com "Manter assinatura" primeiro; depois, aviso de término, cards bloqueados com explicação e "Manter assinatura" funcionando;
- teste grátis: cards com "Assinar …" e sem seção de cancelamento;
- 320 px: sem rolagem horizontal; um `h1`, títulos sem salto.

## Achados e disposição

| Achado | Disposição |
| --- | --- |
| No diálogo de cancelamento, o link "Termos de uso" recebia o foco inicial, antes de "Manter assinatura" | **Corrigido:** o link saiu do diálogo e foi para a seção de cancelamento |
| Revisão de segurança (média): a confirmação só conferia a idade do `prorationDate`, sem amarrar plano e valor à prévia | **Corrigido:** a confirmação traz o valor da prévia, o backend refaz a prévia para a mesma data e recusa qualquer diferença |
| Revisão (média): liberar o *schedule* antes de cobrar, agendar ou cancelar perdia a descida agendada se a chamada seguinte falhasse | **Corrigido:** uma mudança por vez; com descida agendada, outra mudança e o cancelamento são recusados até desfazer (no painel, cards e cancelamento explicam isso). Também elimina a prévia de subida sobre assinatura com *schedule* |
| Revisão (média): recusa e 3DS deixavam uma fatura aberta que podia ser paga depois; `CardException` virava 503 | **Corrigido:** fatura anulada e `CardException` tratado como recusa (402) |
| Revisão (baixa): erro de leitura depois de uma subida já cobrada aparecia como falha | **Corrigido:** a resposta mostra o novo plano e o webhook concilia |
| Revisão (baixa): *schedule* criado com atualização falhando ficava preso | **Corrigido:** é liberado na hora |
| Revisão (baixa): botão "Pagar R$ 0,00" | **Corrigido:** "Confirmar mudança" quando o valor é zero |
| Revisão (baixa): as fases do *schedule* recriam só preço e quantidade | **Aceito:** não há cupons, impostos nem metadados de fase hoje |

## Limitações

- **Stripe real:** os testes usam uma Stripe falsa. Prévia, subida, *schedule* e cancelamento precisam de um teste em modo de teste no ambiente hospedado, depois de ampliar a chave restrita e desligar troca e cancelamento no portal.
- **Diálogo nativo no painel oculto:** com o painel do navegador oculto, o Chromium não dispara o evento `close` do `<dialog>` (nem num `<dialog>` criado à mão na página). A confirmação foi exercitada disparando esse evento; com o painel visível o comportamento é o normal.
- **Leitor de tela, Axe e Lighthouse:** não executados, como nas revisões anteriores.
