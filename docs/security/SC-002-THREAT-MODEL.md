# SC-002 — Modelo de ameaças e plano de segurança

Data: 2026-10-15. Estado: **proposta para aprovação; nenhuma implementação autorizada por este documento**. Base: AGENTS.md, PRD, SPRINT_01, BUSINESS_RULES, ARCHITECTURE, DATA_MODEL e ADR-0002. O ADR de autenticação deve consolidar a decisão antes do código.

## Escopo e recomendação

Planejar identidade global de lojista: cadastro, verificação de e-mail, login, consulta da própria identidade, logout, expiração/revogação e recuperação segura de senha. Não criar Tenant, Store, memberships funcionais, permissões comerciais, autenticação de compradores, pagamentos, OAuth Mercado Pago ou agentes de IA. Essas fronteiras permanecem nas tarefas de domínio. Autenticação de uma pessoa não lhe concede acesso a uma empresa.

Recomendação conjunta com backend: Spring Security, sessão opaca server-side e Spring Session JDBC no PostgreSQL existente. Um único backend controla credenciais, estado e revogação; o browser recebe apenas identificador de sessão. Não criar JWT proprietário nem segundo sistema de sessão no Next. Isso evita contratação inicial de IdP e novo serviço de produção, mas transfere para a equipe o custo de desenvolvimento, monitoramento e recuperação de contas. Não equivale a custo operacional zero nem a prontidão para produção.

A [documentação Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html) fornece persistência de sessões no banco. Migrações serão Flyway, com o esquema da versão efetivamente gerenciada pelo Boot; não inicializar schema automaticamente em produção. Banco indisponível deve impedir autenticação/acesso, sem fallback de confiança.

## Alternativas comparadas

Fontes oficiais consultadas em 2026-10-15. Franquias são referência atual, não orçamento garantido; contratação, envio de e-mail, região, tratamento de dados e custos exigem decisão humana.

| Opção | Benefício | Custo e responsabilidade | Avaliação para SC-002 |
| --- | --- | --- | --- |
| Spring Security + sessão local/JDBC | Integração com backend existente e revogação sob nosso controle | Sem licença de IdP; equipe implementa proteção de senha/reset/rate/abuso e opera e-mail | Recomendada como proposta de baixo custo inicial de infraestrutura. |
| Keycloak + OIDC | Centraliza identidade, recuperação, sessões e integração SSO | Exige serviço adicional, atualização, backup, TLS, banco e disponibilidade; não é custo total zero | Alternativa quando operação de IdP/SSO justificar. Não provisionar agora. A proteção contra brute force vem desabilitada por padrão e precisa ser configurada. [Guia oficial](https://www.keycloak.org/docs/latest/server_admin/index.html). |
| Auth0 gerenciado | Login hospedado e funções de identidade operadas pelo provedor | Página Free consultada: até 25 mil MAU e cinco Organizations; recursos/limites diferem por plano, incluindo RBAC e SSO | Possível alternativa após aprovação de conta, termos, custos e exportação. Organization do provedor não substitui autorização local. [Preços oficiais](https://auth0.com/pricing). |
| Amazon Cognito gerenciado | User pools e federação no ecossistema AWS | Franquia de 10 mil MAU diretos/sociais em Lite/Essentials, 50 MAU federados; e-mail/SMS podem ter cobrança separada | Exige conta/região/configuração AWS e avaliação de operação/custos; não criar recursos nesta tarefa. [Preços oficiais](https://aws.amazon.com/cognito/pricing/). |
| Clerk gerenciado | Integração e UI prontas para Next | Hobby: 50 mil MRU e 100 organizações retidas; sessão fixa de sete dias no plano consultado. Recursos pagos podem ser necessários | Avaliar compatibilidade de duração/revogação com backend antes de escolher; contagem MRU não é MAU. Não adotar SDK apenas para acelerar UI. [Preços oficiais](https://clerk.com/pricing). |

SSO futuro não exige realm, user pool ou aplicação por empresa. Identidade pode participar de várias empresas; as permissões permanecem vinculadas à membership autorizada no backend. A escolha de provedor por franquia isolada pode deslocar custo para SSO, envio de mensagens, retenção de logs e migração.

## Ativos e fronteiras de confiança

Ativos: senha derivada, identificador de sessão, tokens de ação, e-mail verificado, estado da conta e futuros vínculos de participação. Atores: visitante, titular, pessoa autenticada sem vínculo, atacante com cookie/token roubado e operador com acesso restrito. Browser, qualquer header/payload e código de PR são entradas não confiáveis; servidor e banco aplicam controles explícitos.

Fluxo proposto: navegador → origem administrativa Next → proxy restrito de `/api` → Spring Security → PostgreSQL. O Next encaminha Cookie/Set-Cookie e tokens CSRF necessários, sem autenticação paralela. Não aceitar URL upstream fornecida pelo cliente; não repassar headers de identidade arbitrários como prova de usuário. Host/Origin/forwarded headers só devem ser confiados conforme topologia explicitamente configurada.

SSR e respostas autenticadas: acesso sempre confirmado no backend, respostas privadas `Cache-Control: no-store`, sem cache compartilhado de `/me` ou páginas pessoais. Não serializar hash, token de reset, identificador de sessão ou cookie em props/HTML. Redirecionar UI não substitui negar API. Callback/return URL deve aceitar somente destino local permitido para evitar open redirect.

## Controles propostos antes da implementação

### Senha e identidade

Usar PasswordEncoder do Spring com formato versionado via DelegatingPasswordEncoder, salt aleatório por hash e rehash controlado. Proposta pragmática: PBKDF2-HMAC-SHA256 pela implementação do framework/JDK, com piso inicial de 600 mil iterações e benchmark no ambiente alvo, sem algoritmo próprio. Argon2id é alternativa preferida pela OWASP para memória resistente, mas exige avaliar dependência adicional e recursos; PBKDF2 aqui é trade-off operacional, não alegação de conformidade FIPS. Não usar hash rápido para senha. [Spring](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html), [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Política proposta para aprovação: mínimo 15 e máximo 128 caracteres, Unicode sem truncamento ou alteração silenciosa, sem regras artificiais de composição. Limitar tamanho de request antes de trabalho caro. Calibrar custo e concorrência para que autenticação não esgote CPU. Política de normalização de e-mail deve ser documentada, consistente com unicidade e testada; não remover pontos/sufixos como se todos os provedores tivessem semântica Gmail. E-mail é contato/login, não chave de autorização externa nem identificador imutável.

Não permitir autoatribuição de role, status verificado ou tenant no cadastro. Conta sem verificação não ganha sessão administrativa de acesso normal; uma eventual sessão pré-verificação só pode acessar ações explicitamente restritas. Cadastro de e-mail existente e recuperação devem ter resposta genérica e não retornar IDs ou status da conta.

### Sessão, CSRF e revogação

Cookie de sessão: HttpOnly, host-only, Path `/`, SameSite=Lax; Secure obrigatório sob HTTPS e prefixo `__Host-` se compatível com a configuração aprovada. Desenvolvimento HTTP loopback deve usar perfil local explícito, sem enfraquecer produção. Não definir Domain compartilhado com vitrines. Sem sessão em URL, localStorage ou JWT acessível a JavaScript. [Spring Session](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).

Manter CSRF de sincronização para ações mutáveis, incluindo login e logout. Propor endpoint de obtenção do token CSRF e renová-lo após transições de autenticação; cookie de sessão permanece HttpOnly. SameSite e CORS não substituem CSRF. Same-origin pelo proxy é preferência; qualquer CORS excepcional exige origem explícita e teste com credenciais, nunca wildcard. [Proteção CSRF Spring](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

Usar mecanismos Spring de autenticação, rotação de ID e persistência de contexto. Login JSON não pode apenas preencher SecurityContextHolder: precisa executar a estratégia de sessão e salvar o contexto conforme versão do framework. Logout POST invalida sessão no banco e remove cookie. Reset de senha e bloqueio da conta revogam todas as suas sessões; validação de estado da conta não pode depender de role/status indefinidamente em cache. Proposta alinhada ao ADR: versionar autenticação (`authVersion`) e conferir versão/status atual no acesso protegido, incrementando-a transacionalmente no reset/bloqueio. Isso impede que uma requisição concorrente volte a persistir uma sessão revogada; testar essa corrida, sem confiar apenas em apagar linhas de sessão. [Gerência de sessão Spring](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).

Limites sugeridos para aprovação: inatividade de 30 minutos e duração absoluta de oito horas; sem remember-me no primeiro incremento. A duração absoluta exige controle próprio de política sobre a sessão do framework, não apenas timeout de inatividade. Definir e testar revogação de todas as sessões persistidas e comportamento após restart; banco indisponível não permite fallback para sessão não verificável.

### Recuperação, verificação de e-mail e abuso

Propor tokens opacos de 32 bytes CSPRNG, hash SHA-256 no banco, usuário/finalidade/expiração vinculados e consumo único atômico na mesma transação da ação. Hash rápido é adequado aqui por se tratar de token aleatório de alta entropia, não senha humana. TTL proposto: 30 minutos para reset e verificação, configurável e sujeito a aprovação. Novo token invalida o anterior da mesma finalidade; duas tentativas concorrentes permitem somente um sucesso. Não autenticar automaticamente após reset. [Orientações OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

Não expor token em resposta pública, logs, analytics, exceptions ou query de páginas com recursos externos. URL usa origem fixa configurada; nunca Host arbitrário do pedido. Página de consumo usa Referrer-Policy restritiva, sem terceiros, e o GET de link não altera senha nem consome token antes da ação confirmada, evitando scanners de e-mail. Verificação e reset usam finalidades distintas, impossibilitando reaproveitamento entre fluxos.

Taxas propostas para avaliação: login com limitação combinada por IP/origem confiável e identificador normalizado; cadastro, envio, reenvio e consumo de token também limitados. Escolher números após avaliação de capacidade (ponto inicial: cinco falhas por conta em 15 minutos, mais limite por IP), com backoff temporário e sem bloqueio permanente acionável por terceiros. Retornar erros genéricos e evitar atalhos de processamento distinguíveis; não prometer tempo de rede matematicamente constante. Proteger cardinalidade/expiração dos contadores e testar restart; antes de múltiplas instâncias, estado compartilhado e limites globais devem estar definidos, sem contratar serviço adicional automaticamente.

Envio real de e-mail é decisão pendente: provedor, remetente, domínio, custos e retenção. No desenvolvimento/testes usar adapter capturável apenas pelo teste, sem entregar credenciais/tokens por endpoint ou console. Um adapter fictício não comprova entrega ou recuperação em produção. Sem canal verificado, recuperação não fica apta à produção. Coleta de consentimento, retenção de conta/sessões/IP e remoção de dados pessoais precisam de política, sem registrar e-mail/senha/cookie completos em auditoria.

## OIDC/SSO: requisitos se a alternativa for escolhida

Preferir Authorization Code com PKCE, cliente e redirect URI restritos, `state`/`nonce` associados à tentativa e sessão. Validação por bibliotecas Spring: assinatura, algoritmo permitido, issuer configurado, audience, expiração e nonce. Não seguir issuer/JWKS indicado livremente pelo cliente e não usar ID token como autorização irrestrita de API.

Identidade externa é o par `(issuer, subject)` com unicidade; e-mails iguais não autorizam vincular contas automaticamente. Mudança de provedor/issuer exige processo explícito. Separar sessão local, sessão SSO e refresh token; logout local não garante revogação no IdP e invalidar refresh token pode não invalidar imediatamente access tokens já emitidos. Definir rotação, armazenamento server-side e janela de validade antes do uso. [OIDC Core](https://openid.net/specs/openid-connect-core-1_0.html), [Auth0](https://auth0.com/docs/secure/tokens/token-best-practices), [Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-refresh-token.html).

## Ameaças prioritárias e testes reproduzíveis planejados

Os testes abaixo são critérios futuros, não comandos já executados ou cobertura existente.

| Prioridade / ameaça | Preparação e resultado esperado |
| --- | --- |
| Crítica: sessão/identidade forjada | Sem cookie, com cookie aleatório ou header `X-User-ID`/`X-Tenant-ID` inventado, chamar endpoint protegido: 401; nenhuma identidade criada por payload. Verificar também acesso direto ao backend. |
| Alta: fixation | Obter sessão anônima/CSRF, fazer login válido e comparar cookie antes/depois; ID muda e o anterior não acessa `/me`. |
| Alta: CSRF | Enviar login/logout/reset mutável sem token ou com token de outra sessão; 403 sem efeito. Com token válido cumprir contrato esperado; logout por GET não altera sessão. |
| Alta: credencial roubada após revogação | Criar duas sessões da mesma conta, resetar senha ou desabilitar conta; ambas deixam de acessar endpoint protegido, inclusive após restart. |
| Alta: reset concorrente/replay | Duas requisições simultâneas com mesmo token: no máximo uma altera senha; token expirado, usado ou de finalidade errada falha; senha permanece inalterada na falha. |
| Alta: enumeração/abuso | Solicitar cadastro/reset/login com e-mail existente e inexistente; resposta pública não revela existência, controle de custo similar; ultrapassar limites produz negação temporária sem criar bloqueio permanente de vítima. |
| Alta: falha da persistência | Parar banco em teste isolado; login, consulta e mutação protegida não autorizam por fallback. Retomar sem revalidar cookies falsos. |
| Alta: mass assignment/IDOR de identidade | Cadastro contendo role/admin/status/tenant não eleva privilégio. Conta A não consulta conta B fornecendo ID. `/me` usa principal servidor. |
| Alta: cache/SSR | Acessar `/me` e página autenticada em duas sessões e modo anônimo; nenhum conteúdo cruza usuários ou fica em cache público; HTML/respostas não contêm segredo. |
| Média: cookie/rede/redirect | Conferir Set-Cookie sob HTTPS/proxy, ausência de Domain amplo, atributos e exclusão no logout; rejeitar destino externo e Host manipulável. |
| Média: hash/senha | Mesmo texto para dois usuários produz hashes diferentes; nenhum plaintext; verificar parâmetros/rehash, Unicode e limites sem truncamento; medir custo/concorrência. |
| Alta, condicional OIDC | Simular callback com state/nonce errado, issuer/audience inválido e mesmo e-mail com subject distinto: falha sem auto-link. Testar logout/revogação com janelas declaradas. |

SC-003/004 acrescentarão fixtures A/B de empresas e memberships, negação cruzada em leitura/escrita e integridade de referências. Futuro seletor de empresa sempre exige vínculo válido em serviços/repositórios; revogar membership deve revogar autorização, mesmo que a sessão de identidade continue válida. Não certificar A/B na SC-002 sem esse domínio.

## Decisões que o planejamento precisa aprovar

Abordagem sessão local versus IdP; política de senha/hash; TTLs; comportamento de contas não verificadas; limites de abuso e armazenamento dos contadores; canal de e-mail e suas condições; topologia same-origin/HTTPS; revogação; retenção e recuperação de conta. Segurança e produto devem decidir parâmetros antes de tratá-los como padrão aceito. O backend/arquitetura consolidam os contratos; QA reproduz as negativas, frontend verifica cookies/CSRF/SSR e revisão independente confere o resultado. Nenhum serviço, assinatura, integração ou rotina agendada foi criado neste planejamento.
