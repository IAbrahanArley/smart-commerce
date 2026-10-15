# ADR-0005 — Identidade e autenticação do lojista

Status: **PROPOSTO — aguardando aprovação humana antes da implementação da SC-002.**

Data da pesquisa: 2026-10-15. Este documento é planejamento; não autoriza instalação de provedor, contratação, integração real, infraestrutura ou código da SC-002.

## Contexto e limites

O PRD prevê cadastro/login do lojista e, depois, criação de empresa/loja. A SC-002 estabelece identidade, sessão e proteção de endpoints no monólito Java 21/Spring Boot com PostgreSQL e admin Next.js separado. Spring Security é o framework de segurança usado com qualquer alternativa abaixo; não é um provedor de identidade.

A SC-002 não cria Tenant, Store ou TenantMembership reais, nem concede papel de proprietário. Esses vínculos pertencem à SC-003. Autenticar alguém não autoriza acesso a uma empresa. Login de compradores, convites de funcionários, suporte com impersonação, login social, MFA e SSO empresarial não são entregas desta tarefa.

## Alternativas avaliadas

Limites comerciais abaixo são uma fotografia das fontes oficiais consultadas, não garantia contratual. MAU, MRU e organizações não são unidades equivalentes. Revalidar plano, região, e-mail, recursos B2B e cobrança antes de contratar.

| Alternativa | Custo inicial e operação | Segurança e evolução | Avaliação para o MVP |
| --- | --- | --- | --- |
| Spring Security + credenciais locais + sessão no PostgreSQL | Sem serviço de identidade adicional; usa backend/banco existentes. Mantemos cadastro, recuperação, abuso e entrega de e-mail. Operação e trabalho de engenharia têm custo. | Sessões revogáveis; senha protegida pelo PasswordEncoder do framework. SSO externo exige integração OIDC posterior. | Recomendada sob as condições e os testes deste ADR: menor infraestrutura adicional, maior responsabilidade de segurança da equipe. |
| Spring Security + Keycloak OIDC | Software aberto; requer operar outro serviço, atualizações, TLS, disponibilidade, backup e configuração. Autohospedagem não é custo total zero. | OIDC/SAML, SSO e gestão central de contas/sessões; autenticação delegada ao IdP. | Boa opção quando SSO/federação justificar a operação adicional; não adicionar ao Compose agora. |
| Spring Security + Auth0 | Free anuncia até 25.000 MAU e 5 Organizations. Limites de ambientes, recursos B2B e planos pagos devem ser revistos. | Menos código próprio de credenciais; dependência externa e requisitos de exportação/migração. Organizations do provedor não substituem autorização local. | Alternativa gerenciada se a prioridade for reduzir responsabilidade sobre credenciais, mediante aprovação da integração. |
| Spring Security + Amazon Cognito | Lite/Essentials anunciam 10.000 MAU de autenticação direta/social gratuitos; federação SAML/OIDC tem franquia distinta de 50 MAU. E-mail/SMS podem gerar cobrança separada. | IdP gerenciado; configuração AWS, custos e operação de comunicação continuam necessários. | Não selecionar só pela franquia; exige decisão de conta, região e custos. |
| Spring Security + Clerk | Hobby anuncia 50.000 MRU, 100 MRO e até 20 membros por organização; sessão com duração fixa de 7 dias no plano gratuito. MRU não significa MAU. | SDK/UI favorecem Next.js, mas a API Java ainda valida a identidade e aplica autorização própria. SSO e ajustes de sessão dependem de plano. | Integração atraente para frontend, com mais dependência das regras comerciais e do modelo de sessão do fornecedor. |

Fontes do comparativo: [Keycloak](https://www.keycloak.org/), [Auth0 Pricing](https://auth0.com/pricing), [Cognito Pricing](https://aws.amazon.com/cognito/pricing/), [Clerk Pricing](https://clerk.com/pricing).

## Recomendação proposta

Adotar autenticação local no módulo `identity`, usando Spring Security e sessão opaca do servidor persistida com Spring Session JDBC no PostgreSQL existente. Não construir emissão/validação de JWT própria, criptografia própria ou um protocolo de autenticação. O browser recebe um identificador de sessão, sem credenciais ou bearer tokens em localStorage.

Sessão persistida facilita revogação por usuário e evita incorporar Redis ou outro processo. Tabelas técnicas de sessão serão versionadas por Flyway, com inicialização automática de schema desativada e limpeza de expiradas planejada. O principal guarda somente identificador e versão de autenticação, nunca a entidade de credenciais ou seu hash. Revogação deve incrementar a versão da autenticação no usuário e remover sessões indexadas por principal; cada acesso valida versão/status para impedir restauração de sessão antiga por requisição concorrente. A configuração deve ser compatível com as versões já fixadas no backend, sem trocar a linha do Spring Boot incidentalmente. Referências: [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html), [Spring Session API](https://docs.spring.io/spring-session/reference/api.html).

### Fronteiras e modelo mínimo

- `identity` possui usuário global, credencial local, ciclo de sessão, tokens de verificação/recuperação e consulta da própria identidade. `tenant` possuirá memberships e permissões por empresa na SC-003.
- Proposta de dados: usuário com identificador estável, e-mail normalizado único, status e datas; credencial com hash versionado; recuperação com digest do token, usuário, validade e consumo. Não transformar `DATA_MODEL.md` inteiro em migração.
- Normalizar e-mail de forma documentada, sem remover pontos ou sufixos específicos de provedores. Preservar um identificador interno que não mude quando o e-mail mudar.
- O cadastro não aceita `role`, `tenant_id`, membership ou status privilegiado enviados pelo cliente. Não existe cadastro público de operador da plataforma.
- Contratos a implementar após aprovação: cadastro, login, logout por POST, identidade atual, obtenção de token CSRF, solicitação e confirmação de recuperação. Todos respondem sem hash, token de recuperação ou detalhes internos.

### Senhas e prevenção de abuso

Usar `DelegatingPasswordEncoder` com `Pbkdf2PasswordEncoder` do framework, PBKDF2-HMAC-SHA256, salt aleatório por hash e parâmetros versionados; nenhuma implementação criptográfica artesanal. Propor 600.000 iterações como piso inicial, sujeito a benchmark de custo/latência e revisão antes de liberar. A escolha evita biblioteca criptográfica adicional no bootstrap de identidade; não é alegação de conformidade FIPS. Argon2id é alternativa preferencial da OWASP, a avaliar se aprovados sua dependência e seu custo de memória. Não usar hash rápido ou criptografia reversível para senha. Proposta de política: mínimo de 15 caracteres na ausência de MFA, máximo de 128, permitir espaços e Unicode, sem truncamento e sem regras arbitrárias de composição ou troca periódica.

Login, cadastro e recuperação precisam de limitação de tentativas por origem e identificador, com espera temporária sem bloqueio permanente explorável contra terceiros. Respostas de login e recuperação não revelam existência, status ou bloqueio da conta; medir diferenças de tempo nos testes. Senha, cookies, tokens e e-mail completo não aparecem em logs. Referências: [Spring Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html), [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).

### Cookies, navegador e sessão

Preferir admin e API sob a mesma origem visível ao browser, com encaminhamento explícito de `/api` para Spring. Backend continua sendo a autoridade da sessão; encaminhamento não cria segundo mecanismo de login. Preservar Set-Cookie/headers CSRF e encaminhar cookies em consultas SSR autenticadas; respostas de identidade usam no-store, sem cache compartilhado. Se origens distintas forem necessárias, aprovar allowlist CORS exata com credenciais, sem wildcard, e testar o fluxo no browser. A topologia de produção permanece pendente; não exige deploy nesta tarefa.

Cookie de sessão: `HttpOnly`, `Secure` em HTTPS, `SameSite=Lax`, host-only e caminho explícito. Não compartilhar por domínio com vitrines de lojistas. Exceção `Secure=false` somente no perfil local HTTP e em testes explicitamente configurados. `SameSite` complementa, não substitui CSRF.

Manter CSRF em ações mutáveis, incluindo login e logout. Admin obtém o token por contrato dedicado e o envia em header; renovar após login/logout conforme integração Spring. Verificar rejeição sem token ou com origem indevida. Sessão muda de identificador no login para impedir fixation. Um login JSON deve executar a estratégia de autenticação da sessão e persistir o SecurityContext pelo framework; alterar apenas SecurityContextHolder não garante persistência ou rotação. Logout invalida sessão no servidor e remove cookie; troca/reset de senha ou desativação revogam todas as sessões do usuário. Proposta inicial: 30 minutos ociosos e teto absoluto de 8 horas, sem remember-me, sujeitos à aprovação de produto. Referências: [Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Spring Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).

### Recuperação e comprovação de e-mail

Token de recuperação aleatório com gerador criptográfico, pelo menos 256 bits de entropia, validade proposta de 30 minutos, uso único e apenas digest SHA-256 armazenado. Hash rápido é adequado a este token de alta entropia, não a senhas. O consumo e a troca de senha devem ser atômicos, inclusive com duas requisições concorrentes. Nova solicitação invalida tokens anteriores. Não autenticar automaticamente após reset; revogar sessões e notificar a conta. Link usa origem configurada, nunca `Host` não confiável; sem token em logs, analytics, referer ou respostas de API.

Em desenvolvimento/testes, capturar a mensagem em transporte local controlado e inspecionável, isolado de produção; não imprimir o link em logs. Provedor real, domínio de envio, disponibilidade, reputação, retenção e custo de e-mail exigem aprovação antes da produção. O aceite local não comprova entrega real. Referência: [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

Recomenda-se comprovação de e-mail para ativar a conta do lojista. Essa inclusão e a experiência de cadastro aguardam decisão de produto. Se aprovada, usar token separado por finalidade e nenhum privilégio empresarial antes da verificação. Não afirmar que uma conta sem essa etapa tem e-mail comprovado.

### Tenant, permissões e OIDC futuro

A identidade autenticada tem acesso apenas à própria identidade nesta tarefa. Não inventar memberships temporários. Na SC-003, o backend resolverá tenant autorizado a partir de identidade + vínculo ativo; um seletor de empresa apresentado pelo cliente será validado. Serviço e repositório aplicam escopo; payload, header, slug e claim fornecido pelo frontend não concedem acesso. Remoção de membership deve produzir revogação efetiva de permissão, sem confiar indefinidamente em papéis gravados na sessão.

Uma identidade global poderá participar de mais de uma empresa no futuro sem duplicar credenciais. Isso é diferente de SSO entre aplicativos ou federação empresarial. Para OIDC posterior, mapear identidade externa pelo par `(issuer, subject)` validado pelo framework; e-mail não é chave de vínculo automático. Vinculação exige comprovação das duas contas ou fluxo explícito de recuperação. IdP não passa a ser proprietário da autorização financeira ou empresarial. Referência: [OpenID Connect — estabilidade de claims](https://openid.net/specs/openid-connect-core-1_0.html#ClaimStability).

## Critérios verificáveis após aprovação e implementação

1. Cadastro persiste somente identidade/credencial; duplicatas concorrentes não criam duas contas; não permite escalada por campos extras. Protegidos retornam 401 sem sessão e 403 quando há identidade sem permissão.
2. Login válido estabelece sessão e altera seu identificador; senha incorreta, conta inexistente e inativa não revelam diferenças funcionais indevidas. Hash não é senha em claro e pode evoluir de parâmetros.
3. Browser completa obtenção CSRF, login, identidade e logout. Mutação sem CSRF falha, cookie respeita perfil e CORS não aceita origem arbitrária.
4. Logout, expiração ociosa/absoluta, reset e desativação tornam sessões inaproveitáveis no servidor. Reset concorrido admite um único vencedor; token expirado, alterado ou reutilizado falha.
5. Banco PostgreSQL/Testcontainers valida migrações, unicidades e atomicidade; testes não acessam provedores externos. Frontend testa estados de sessão/erro sem exibir credenciais.
6. Revisão verifica abuso, enumeração, CSRF/fixation, vazamento em logs e respostas. Testes A/B de dados empresariais continuam na SC-003/004, quando houver domínio.
7. README distingue fluxo local de e-mail de entrega de produção; build, lint e testes existentes continuam obrigatórios. Nenhuma dependência ou serviço é instalado na fase de planejamento.

## Decisões pendentes e consequências

Antes de implementar: aprovar autenticação local versus IdP; persistência JDBC; PBKDF2 e parâmetros; limites propostos de senha/sessão/reset; comprovação de e-mail e contrato local de envio. As escolhas afetam segurança e experiência e não são consideradas aprovadas pela criação deste ADR.

Antes de produção: decidir transporte real de e-mail, origem/HTTPS, limites de abuso operacionais, retenção e revisão de riscos. Não autorizar cobrança, serviço ou deploy com este documento.

A recomendação reduz serviços extras e dependência comercial, mas aumenta nossa responsabilidade por segurança e recuperação. Se a equipe não puder manter esses controles e testes, preferir IdP gerenciado aprovado a enfraquecer os requisitos. Uma migração OIDC futura requer plano de vínculo/recuperação; não se presume transferência transparente de senhas. RLS, MFA e SSO permanecem decisões posteriores.
