# Guia de deploy: ambiente de teste hospedado (Render + Vercel + Brevo)

Base: [ADR-0014](../architecture/adr/0014-hosted-test-environment.md). O ambiente é **de teste, com dados fictícios e sem pagamento**; não é produção comercial.

Regras gerais:
- Todas as contas, projetos, bancos e chaves são criados por você nos painéis.
- **Nenhum valor secreto vai para o chat, o Git ou um print.**
- Siga a ordem abaixo, porque cada passo usa a URL gerada no anterior.

## 0. Antes de começar

- Use a `main` atualizada.
- Use só **dados sintéticos**: e-mails seus ou de teste, nunca dados de terceiros (D2 pendente de validação jurídica).
- Gere o segredo compartilhado do IP, com pelo menos 32 caracteres, e guarde-o num gerenciador de senhas. Ele vai, com o mesmo valor, no backend e na vitrine:

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

## 1. Brevo (e-mail de cadastro)

1. Crie uma conta gratuita no Brevo (300 e-mails/dia).
2. **Remetentes** (no painel, algo como "Senders"; os nomes podem variar): adicione um remetente e confirme o código de 6 dígitos enviado a ele.
   - Sem domínio próprio, o Brevo troca o domínio do remetente por `@brevosend.com`, o que basta para teste.
   - Endereços de provedores gratuitos (Gmail etc.) não podem ser autenticados.
3. Se as configurações transacionais tiverem rastreamento de cliques, **desligue-o**: o link de confirmação contém o token e não deve passar por redirecionamento de terceiros.
4. **Chaves de API** (no painel, algo como "SMTP & API"): crie uma chave só para este ambiente.

## 2. Render: banco

1. **New → Postgres**, plano **Free**. Escolha uma região e use **a mesma** no backend.
2. Anote os dados de **Internal Connection**: host, porta, banco, usuário e senha.
3. Limites:
   - expira **30 dias** após a criação (com 14 dias de carência);
   - **não tem backup**;
   - trate os dados como descartáveis.

## 3. Render: backend

1. **New → Web Service**, conecte o repositório e configure:
   - **Language:** Docker.
   - **Branch:** `main`.
   - **Root Directory:** `backend`.
   - **Dockerfile Path:** `./Dockerfile`.
   - **Instance Type:** Free.
   - **Health Check Path:** `/actuator/health/readiness`.
2. Variáveis de ambiente:

| Variável | Valor |
| --- | --- |
| `DB_URL` | **Cole a Internal Database URL do Render** (`postgresql://usuario:senha@host/banco`): o backend a converte para JDBC e usa o usuário e a senha dela. Também aceita `jdbc:postgresql://<host>:5432/<banco>`. Aspas e espaços nas pontas são ignorados. Como contém a senha, trate como segredo |
| `DB_USERNAME` / `DB_PASSWORD` | Opcionais com a Internal Database URL; obrigatórios com a forma `jdbc:` (têm prioridade sobre os da URL) |
| `DEPLOYMENT_ENVIRONMENT` | `hosted-test` |
| `ORDERS_CHECKOUT_ENABLED` | `true` (exceção H2; o backend recusa iniciar sem `hosted-test`) |
| `STOREFRONT_FORWARDING_SECRET` | o segredo gerado no passo 0 |
| `STOREFRONT_REVALIDATION_URL` | URL de produção da vitrine na Vercel (passo 5), por exemplo `https://<projeto-vitrine>.vercel.app`; vazio: as páginas se atualizam a cada 60 s (SC-047) |
| `STOREFRONT_REVALIDATION_SECRET` | outro segredo aleatório de pelo menos 32 caracteres, só para a revalidação da vitrine |
| `IDENTITY_PUBLIC_ORIGIN` | URL **de produção** do admin na Vercel (passo 4), por exemplo `https://<projeto-admin>.vercel.app`; até lá, um valor HTTPS provisório |
| `IDENTITY_SECURE_COOKIES` | `true` |
| `IDENTITY_MAIL_PROVIDER` | `brevo` |
| `IDENTITY_MAIL_BREVO_API_KEY` | chave do Brevo (segredo) |
| `IDENTITY_MAIL_SENDER_EMAIL` | remetente verificado no Brevo |
| `IDENTITY_MAIL_SENDER_NAME` | `Smart Commerce (teste)` |

   **Mercado Pago (SC-012, opcional e só sandbox).** Sem estas variáveis, a página Pagamentos mostra "Indisponível neste ambiente":

| Variável | Valor |
| --- | --- |
| `MERCADO_PAGO_CLIENT_ID` | APP_ID da aplicação de teste |
| `MERCADO_PAGO_CLIENT_SECRET` | segredo da aplicação (segredo) |
| `MERCADO_PAGO_REDIRECT_URI` | `https://<projeto-admin>.vercel.app/api/payments/mercado-pago/callback`, idêntica à cadastrada na aplicação |
| `PAYMENTS_TOKEN_ENCRYPTION_KEY` | 32 bytes aleatórios em Base64 (segredo; comando em [SC-012-VALIDATION.md](../quality/SC-012-VALIDATION.md)) |
| `MERCADO_PAGO_WEBHOOK_SECRET` | segredo do webhook da aplicação (segredo; painel → Webhooks). Cadastre a URL `https://<servico>.onrender.com/api/public/webhooks/mercado-pago` com o evento **"Pagamentos"** (SC-014; o Checkout Pro usa preferências desde a mudança da P1 do ADR-0013) |
| `MERCADO_PAGO_NOTIFICATION_URL` | opcional: a mesma URL do webhook, enviada em cada preferência para que os pagamentos de todos os lojistas conectados notifiquem a plataforma |
| `STOREFRONT_PUBLIC_ORIGIN` | URL de produção da vitrine na Vercel (passo 5), por exemplo `https://<projeto-vitrine>.vercel.app`, para a página de retorno do Checkout Pro (SC-013) |

   **Assistente (SC-026, opcional).** Sem estas variáveis, o assistente fica indisponível. Use só dados sintéticos neste ambiente (ADR-0016 A11):

| Variável | Valor |
| --- | --- |
| `ADVISOR_ENABLED` | `true` para ligar |
| `ANTHROPIC_API_KEY` | chave criada no console da Anthropic (segredo; cadastre só no painel do Render, nunca no chat ou no Git) |
| `ADVISOR_GLOBAL_MONTHLY_CAP_USD` | opcional: teto mensal da plataforma em US$ (padrão 20,00) |
| `ADVISOR_MONTHLY_QUOTA` | opcional: perguntas por loja por mês (padrão 100) |

   **Assinatura da plataforma (SC-031 e SC-032, opcional).** Sem estas variáveis, não há limite de plano nem cobrança. Neste ambiente, use só o **modo de teste** da Stripe: o backend recusa iniciar com chave live fora de `DEPLOYMENT_ENVIRONMENT=production` (ADR-0017 B12):

| Variável | Valor |
| --- | --- |
| `BILLING_ENABLED` | `true` para ligar os limites dos planos, o teste de 14 dias, o bloqueio de contas inativas e a assinatura pela Stripe (ADR-0017). Exige as duas variáveis abaixo |
| `STRIPE_SECRET_KEY` | chave de teste da Stripe, de preferência restrita (`rk_test_`) com: Customers (gravação), Prices (leitura), Checkout Sessions (gravação), Customer portal (gravação), Subscriptions (gravação), Subscription schedules (gravação) e Invoices (gravação). As três últimas são da troca de plano no painel (ADR-0017 B13): prévia do valor e anulação da fatura de uma subida recusada. Segredo; só no painel do Render |
| `STRIPE_WEBHOOK_SECRET` | segredo `whsec_` do endpoint de webhook (segredo). Cadastre na Stripe a URL `https://<servico>.onrender.com/api/public/webhooks/stripe` com os eventos `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `customer.subscription.paused`, `customer.subscription.resumed`, `invoice.paid` e `invoice.payment_failed` |
| `BILLING_TRIAL_DAYS` / `BILLING_GRACE_DAYS` | opcionais: duração do teste grátis (padrão 14) e carência depois de uma cobrança recusada (padrão 7) |

   Na Stripe, crie os 3 produtos com 3 preços recorrentes em BRL cada (mensal, a cada 6 meses e anual), com as `lookup_key` `essencial_mensal`, `essencial_semestral`, `essencial_anual`, `profissional_mensal`, `profissional_semestral`, `profissional_anual`, `escala_mensal`, `escala_semestral` e `escala_anual`, nos valores de [PRICING_PROPOSAL.md](../product/PRICING_PROPOSAL.md). Ative o portal do cliente só com formas de pagamento e histórico de faturas, **sem** troca de plano e sem cancelamento: desde a B13 do ADR-0017, essas ações são feitas no painel.

3. **Não defina** `SPRING_PROFILES_ACTIVE=dev`. A `PORT` é definida pelo Render, e a imagem já escuta em `0.0.0.0`.
4. **Primeiro deploy:** o build Maven dentro da imagem leva alguns minutos. Na simulação local com o mesmo limite do free (0,1 CPU e 512 MB), a aplicação levou **cerca de 4 minutos para ficar pronta**, aplicando as 16 migrações, e usou cerca de 230 MB.
5. Anote a URL pública do backend, `https://<servico>.onrender.com`.

## 4. Vercel: admin

1. **Add New → Project**, importe o repositório e configure:
   - **Root Directory:** `admin-web`.
   - **Framework:** Next.js.
2. Variáveis (Production):

| Variável | Valor |
| --- | --- |
| `ADMIN_API_ORIGIN` | URL do backend no Render (HTTPS, sem caminho); o build falha se for inválida |
| `NEXT_PUBLIC_STOREFRONT_ORIGIN` | URL de produção da vitrine (passo 5); pode ser preenchida depois, com novo deploy |
| `DEPLOYMENT_ENVIRONMENT` | `hosted-test` (desde 2026-11-04 o admin não usa mais esta variável; pode ficar) |

3. Depois do deploy, copie a URL de **produção** do projeto e coloque em `IDENTITY_PUBLIC_ORIGIN` no Render, que reinicia o backend.
4. **URLs de preview** da Vercel têm outra origem, então o login nelas é recusado pela verificação de origem. Isso é esperado; teste na URL de produção.

## 5. Vercel: vitrine

1. Novo projeto com **Root Directory:** `storefront-web`.
2. Variáveis (Production):

| Variável | Valor |
| --- | --- |
| `STOREFRONT_API_ORIGIN` | URL do backend no Render |
| `STOREFRONT_CHECKOUT_ENABLED` | `true` |
| `DEPLOYMENT_ENVIRONMENT` | `hosted-test` (sem ele, a vitrine não mostra o checkout: D6) |
| `STOREFRONT_FORWARDING_SECRET` | o mesmo segredo do backend |
| `STOREFRONT_TRUST_FORWARDED_FOR` | `true` (a Vercel sobrescreve `X-Forwarded-For` na borda; requisito R1 da SC-011) |
| `STOREFRONT_PUBLIC_ORIGIN` | a URL de produção da própria vitrine (SC-047: `canonical`, Open Graph e JSON-LD) |
| `STOREFRONT_REVALIDATION_SECRET` | o mesmo segredo de revalidação do backend |

3. Atualize `NEXT_PUBLIC_STOREFRONT_ORIGIN` no admin e faça um novo deploy dele.

## 6. Verificação depois do deploy

1. `https://<backend>/actuator/health` responde 200. Se o serviço estava dormindo, a primeira chamada pode demorar minutos.
2. **Admin:**
   - o cadastro envia o e-mail de confirmação;
   - o link abre `/confirmar-email`, você define a senha e entra.
3. **Configuração da loja:** crie a loja, um produto com estoque e a entrega; publique produto e loja.
4. **Vitrine:**
   - o rodapé diz que os pedidos são de teste (a faixa do topo saiu em 2026-11-04);
   - adicione ao carrinho, finalize e veja a página do pedido com o prazo da reserva;
   - no admin, em Pedidos, o pedido aparece; cancele com um motivo.
5. Se alguma etapa falhar, registre o horário e o passo. Os logs do Render e da Vercel não devem conter chave, token, senha ou e-mail de comprador.

## 7. Comportamentos esperados nos planos gratuitos

- **Sono:** o backend dorme após 15 min sem tráfego. Ao acordar, a vitrine (que espera 5 a 10 s) e o admin podem mostrar "indisponível" até ele subir; basta tentar de novo depois (H4).
- **Rotinas:** expiração e anonimização só rodam com o backend acordado. A expiração da reserva continua valendo pelo relógio.
- **Horas:** são 750 h/mês por workspace; um serviço sempre acordado consumiria quase tudo.
- **Uso comercial:** o Vercel Hobby não permite uso comercial; para operar de verdade, use o Pro.

## 8. Encerramento e rotação

- Para encerrar: apague os dois projetos da Vercel, o web service e o Postgres do Render, e revogue a chave do Brevo.
- Se um segredo vazar, gere outro, atualize o Render e a vitrine **com o mesmo valor** e faça novo deploy dos dois.
