# ADR-0014: Ambiente de teste hospedado (Render + Vercel, planos gratuitos)

Status: **adotado** em 2026-10-19 para H1–H4 (ver "Aprovação"). Proposto na mesma data. Nada foi criado nem implantado. Contas, projetos, bancos e segredos são criados **pelo responsável**, e a preparação do código ainda exige autorização própria.

## Objetivo

Observar o sistema fora da máquina de desenvolvimento, em HTTPS público e em infraestrutura gerenciada, e testar os fluxos de ponta a ponta. Isso **não é produção comercial**: não há pagamento (Sprint 4 em andamento), a validação jurídica do D2 está pendente, os riscos R1, R2 e R6 da SC-011 seguem abertos e os planos gratuitos têm limites que impedem operação real.

## Topologia proposta

| Parte | Serviço | Observação |
| --- | --- | --- |
| Backend (Spring Boot, Java 21) | Render Web Service **Free**, via **Docker** | Uma instância; HTTPS do Render; `SERVER_ADDRESS=0.0.0.0` e `SERVER_PORT=$PORT` (o backend já lê essas variáveis) |
| Banco | Render Postgres **Free** | Flyway aplica V1–V16 na inicialização (todas aditivas) |
| Admin (`admin-web`) | Vercel, projeto com raiz `admin-web` | `/api/*` reescrito para o backend no Render (mesma origem para cookie e CSRF) |
| Vitrine (`storefront-web`) | Vercel, projeto com raiz `storefront-web` | O servidor chama o backend por `STOREFRONT_API_ORIGIN` |

## Limites dos planos (documentação oficial consultada em 2026-10-19)

**Render Free:**
- **Instância:** 0,1 CPU e 512 MB de RAM.
- **Sono:** dorme após **15 min sem tráfego** e leva **~1 min** para acordar.
- **Horas:** 750 h/mês por workspace; ao esgotar, os serviços free ficam suspensos até o mês seguinte.
- **Rede:** portas SMTP **25, 465 e 587 bloqueadas** para saída.
- **Disco:** sem disco persistente.
- **Sem:** worker e cron, várias instâncias e shell.
- **Postgres free:** 1 GB; **expira 30 dias após a criação** (com 14 dias de carência antes de ser apagado); **sem backup**; um por workspace.

**Vercel Hobby:**
- **Uso:** só pessoal e **não comercial**; operação comercial exige o plano Pro.
- **Cotas:** fixas, sem cobrança de excedente; ao estourar, o recurso fica bloqueado até o ciclo seguinte.
- **`X-Forwarded-For`:** a Vercel **sobrescreve** o valor enviado pelo cliente quando é a borda mais externa, então a vitrine pode usar `STOREFRONT_TRUST_FORWARDED_FOR=true` (requisito R1 da SC-011).

**Fontes:**
- [Render: Deploy for Free](https://render.com/docs/free)
- [Render: Compute Plans](https://render.com/docs/compute-plans)
- [Vercel: Request headers](https://vercel.com/docs/headers/request-headers)
- [Vercel: Hobby Plan](https://vercel.com/docs/plans/hobby)
- [Vercel: Fair Use Guidelines](https://vercel.com/docs/limits/fair-use-guidelines)

## Bloqueios e ajustes encontrados no código atual

| # | Item | Situação | Ajuste proposto |
| --- | --- | --- | --- |
| B1 | **E-mail de cadastro** | Fora do perfil `dev` não há transporte de e-mail: o cadastro responde "Transporte de e-mail indisponível". No Render free, SMTP está bloqueado | Adaptador de e-mail por **API HTTP** de um provedor (decisão H1), ou contas de teste semeadas pelo operador |
| B2 | Imagem do backend | Não existe Dockerfile | Dockerfile multi-stage (build Maven → JRE 21), usuário sem privilégios e memória limitada (`-XX:MaxRAMPercentage`), com healthcheck em `/actuator/health/readiness` |
| B3 | Destino da API do admin | `next.config.ts` reescreve para `http://127.0.0.1:8080` fixo | Variável de build validada (HTTPS em produção), mantendo o padrão local |
| B4 | Origem e cookies | `IDENTITY_PUBLIC_ORIGIN` e `IDENTITY_SECURE_COOKIES` precisam apontar para a URL HTTPS do admin | Só configuração (o backend já exige HTTPS explícito fora do loopback) |
| B5 | **Checkout sem pagamento (D6)** | O D6 proíbe aceitar pedidos fora de DEV sem meio de pagamento | Manter desligado, ou exceção explícita para "ambiente de teste" (decisão H2) |
| B6 | Sono do Render e timeouts | A vitrine espera o backend por 5–10 s, e o backend leva ~1 min para acordar; a primeira visita depois do sono mostra "indisponível". A inicialização com 0,1 CPU pode levar minutos | Aceitar, manter acordado por ping externo (consome ~744 h das 750 h) ou usar o plano Starter (decisão H4) |
| B7 | Banco free expira em 30 dias e não tem backup | Dados do teste são descartáveis | Tratar como descartável ou usar um banco que não expire (decisão H3) |
| B8 | Rotinas da SC-010 | Rodam no próprio processo, só enquanto ele está acordado | Aceitável no teste: a expiração da reserva vale pelo relógio, sem depender da rotina |
| B9 | Dados pessoais | Política de privacidade e validação jurídica pendentes | Somente dados sintéticos e contas de teste; vitrine já é `noindex` |
| B10 | Segredos | Segredo do IP, senha do banco e chave do provedor de e-mail | Cadastrados pelo responsável nos painéis do Render e da Vercel, nunca no chat ou no Git |

## Aprovação

Em 2026-10-19 o responsável aprovou:

- **H1, e-mail:** adaptador de e-mail por **API HTTP** de um provedor com camada gratuita. O provedor ainda será escolhido; a conta é criada pelo responsável e a chave fica só no painel do Render.
- **H2, checkout:** **exceção restrita ao D6** apenas neste ambiente de teste hospedado. Ela exige dados sintéticos, aviso visível de "ambiente de teste" em todas as páginas da vitrine e do admin, liga só por configuração explícita do ambiente e deve ser retirada antes de qualquer uso real. A produção comercial continua proibida de aceitar pedido sem pagamento.
- **H3, banco:** **Render Postgres free**, tratado como descartável (expira em 30 dias e não tem backup).
- **H4, sono e CPU:** **aceitar o sono** do Render free e observar o comportamento real, sem ping de manutenção nem plano pago.

## Decisões (aprovadas em 2026-10-19)

- **H1, e-mail:** provedor com API HTTP e camada gratuita, que é um serviço externo novo; ou semeadura de contas de teste pelo operador, sem cadastro público.
- **H2, checkout no ambiente de teste:** manter desligado (D6) ou abrir uma exceção restrita ao ambiente de teste, com dados sintéticos, aviso visível de ambiente de teste e retirada antes de qualquer uso real.
- **H3, banco:** Render Postgres free (expira em 30 dias), plano pago do Render ou outro provedor com camada gratuita sem expiração (serviço externo novo).
- **H4, sono e CPU:** aceitar o sono do free, usar um ping de manutenção (GitHub Actions agendado ou serviço externo) ou usar o plano pago Starter do backend.

## Fora deste ADR

Domínio próprio, Mercado Pago (Sprint 4), produção comercial, CDN e backups gerenciados, monitoramento pago, testes de carga.
