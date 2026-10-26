# Blocos 2a e 2b do MVP — Retirar o que é de teste e liberar o checkout com pagamento: validação

Data: 2026-10-26. Base: bloco 2 do [RELEASE_PLAN.md](../product/RELEASE_PLAN.md).

## Decisões do responsável (2026-10-26)

1. **Checkout em produção:** só liga com pagamento real. É uma revisão do D6 do [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md).
2. **Demonstrações:** removidas.
3. **Landing:** o design foi mantido e o texto atualizado para o que já existe, com chamadas para cadastro e login.

## 2a — O que saiu e o que mudou

**Rotas removidas:** `/componentes`, `/admin/produtos` e `/loja/produto`. Também saíram os componentes `demo-admin` e `demo-store` e o CSS exclusivo deles. As três rotas agora respondem 404.

**Landing (`/`):**
- as chamadas para as demos viraram "Criar minha loja" (`/cadastro`), "Já tenho conta" e "Entrar" (`/entrar`);
- a seção "Próximas etapas", que dizia que checkout, pagamentos e financeiro não existiam, virou "Do pedido ao financeiro", com o que já existe;
- os textos de entrega e de publicação passaram a citar o pagamento pelo Mercado Pago;
- as prévias continuam, marcadas como "dados fictícios" e sem links para rotas removidas.

**Textos de ambiente:**
- **Admin:** o rodapé "Ambiente de desenvolvimento" saiu das páginas públicas e do painel. A mensagem do cadastro não cita mais a caixa de e-mail local de desenvolvimento.
- **Rodapé da vitrine:**
  - no ambiente de teste: "Pedidos feitos aqui são de teste";
  - com checkout em produção: "O pagamento é feito no Mercado Pago";
  - sem checkout: o texto continua dizendo que a compra não está disponível.
- **Página do produto na vitrine:** "A finalização da compra ainda não está disponível" só aparece quando o checkout está desligado.

**Mantidos:** o aviso permanente do ambiente de teste (ADR-0014 H2), que só aparece com `DEPLOYMENT_ENVIRONMENT=hosted-test`.

**Fora do escopo do texto:** nenhuma afirmação sobre preço ou plano. "Cadastro gratuito" foi evitado porque ainda não há decisão comercial.

## 2b — D6 revisado

**Backend (`CheckoutSafetyCheck`):** fora de `dev` e de `hosted-test`, o backend recusa iniciar com `orders.checkout.enabled=true` sem:
- a integração do Mercado Pago (`MERCADO_PAGO_CLIENT_ID` e o restante);
- o segredo do webhook (`MERCADO_PAGO_WEBHOOK_SECRET`);
- `MERCADO_PAGO_TEST_TOKEN=false`.

O segredo do IP assinado continua obrigatório.

**Vitrine (`checkoutEnabled`):** em build de produção, o checkout exige `STOREFRONT_CHECKOUT_ENABLED=true` e, além disso, `DEPLOYMENT_ENVIRONMENT=production` ou `hosted-test`. Os valores são exatos: "PRODUCTION", "staging" ou vazio continuam desligando.

**Sem mudança:**
- loja sem conta do Mercado Pago conectada recusa pedidos novos (`PAYMENT_UNAVAILABLE`);
- a confirmação do pagamento continua por webhook e conciliação, nunca pelo retorno do navegador.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `CheckoutSafetyCheckTest` (2) | <ul><li>fora de dev, sem `hosted-test` e sem pagamento real: recusa, citando `MERCADO_PAGO_TEST_TOKEN=false`</li><li>produção com pagamento real: aceita</li><li>sem o segredo do IP: recusa em todos os casos fora de dev</li></ul> |
| `mvnw clean verify` (2026-10-26) | 95 unitários e 164 de integração, todos passando (BUILD SUCCESS). O 1 ignorado é a avaliação paga da SC-029 |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke confere que a landing leva a `/cadastro` e `/entrar`, que não cita "em desenvolvimento" nem "demonstração" e que as três demos dão 404 |
| Vitrine: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. As unidades cobrem `checkoutEnabled` com `production`, valores errados e flag ausente. As duas execuções (checkout ligado e desligado) passam |
| Navegador | landing sem links para rotas removidas, sem rolagem horizontal e com o texto conferido |

## Ainda no bloco 2

- **Configuração de produção:** roteiro das variáveis (Mercado Pago de produção, webhook, assistente desligado) e ambiente separado. O ambiente separado depende do bloco 4.
- **Observabilidade mínima:** identificador de correlação por requisição e auditoria de logs.
- **Revisão completa da interface** nas larguras de referência, com as pendências de acessibilidade.
