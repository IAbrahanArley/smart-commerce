# ADR-0005 — Identidade e autenticação do lojista

Status: **D1–D6 APROVADAS. Entrega funcional local de cadastro, ativação, login, identidade autenticada e logout autorizada.**

Data: 2026-10-15. Consolida a proposta anterior da branch `docs/sc-001-governance`, a revisão de architect, backend, security e QA e a aprovação humana de D1–D3 com autorização restrita da fundação. A implementação da Etapa 1 foi validada localmente e sua publicação foi autorizada posteriormente pelo responsável. D4–D6 foram aprovadas posteriormente, autorizando backend e frontend do fluxo local. Recuperação de senha, empresas/lojas, SSO e provedor externo continuam fora desta entrega. Contrato implementado da fundação: [IDENTITY_FOUNDATION.md](../IDENTITY_FOUNDATION.md); evidências: [SC-002-STAGE-01-VALIDATION.md](../../quality/SC-002-STAGE-01-VALIDATION.md).

## Aprovação da entrega funcional local

D4: prova de 30 minutos, reenvio após 60 segundos, invalidando a prova anterior apenas quando efetivamente emitida. Solicitações suprimidas não alteram token nem prorrogam o prazo fixo de sete dias da pendência.

D5: limites PostgreSQL descritos abaixo, sem bloqueio permanente; janelas bloqueadas não são prolongadas. Cadastro e confirmação também têm admissão limitada e cardinalidade máxima dos contadores. Hashing limitado inicialmente a duas operações simultâneas por instância, sem fila; saturação retorna 503/Retry-After uniformemente. Mantidos os parâmetros medidos da Etapa 1, sem novo benchmark.

D6: mensagens DEV em diretório fora do repositório e do OneDrive, acesso restrito, nomes opacos e retenção de 24 horas. Tokens não aparecem em logs; links usam origem configurada e fragmento, com consumo somente em POST explícito. Testes usam captura isolada.

Entrega autorizada: solicitação de cadastro por e-mail → prova → definição de senha/ativação sem login automático → login → consulta de identidade → logout; telas mínimas Next.js conforme DESIGN.md. Sessão JDBC: 30 minutos ociosa e teto absoluto de oito horas, CSRF e rotação de ID. Nenhum tenant, membership ou role é concedido.

## Contexto e limites

Fontes: [PRD](../../product/PRD.md), [Sprint 1](../../product/SPRINT_01.md), [arquitetura](../ARCHITECTURE.md), [ADR-0004](0004-project-bootstrap.md) e [DESIGN.md](../../design/DESIGN.md). O plano incremental e seus gates estão em [SC-002-PLAN.md](../../product/SC-002-PLAN.md); testes verificáveis em [SC-002-TEST-PLAN.md](../../quality/SC-002-TEST-PLAN.md).

O módulo identity controla identidade global, credencial, comprovação de e-mail, recuperação e sessão. SC-002 não cria Tenant, Store, membership, OWNER ou cadastro público de operador da plataforma. Permissões empresariais e isolamento A/B pertencem à SC-003/004. Login de compradores, MFA, SSO, convites e integrações de produção ficam fora desta entrega.

## Base preliminar definida pelo responsável

1. Autenticação local com Spring Security; não reabrir seleção de IdP nesta revisão.
2. Sessão opaca persistida no PostgreSQL existente com Spring Session JDBC.
3. PBKDF2-HMAC-SHA256, parâmetros versionados e benchmark obrigatório.
4. Inatividade de 30 minutos e duração absoluta máxima de 8 horas.
5. Comprovação obrigatória de e-mail para ativação; sem acesso administrativo normal antes disso.
6. Recuperação de senha com token de uso único e validade de 30 minutos.
7. Transporte local de e-mail em desenvolvimento; não contratar nem integrar provedor real.
8. Controles contra abuso, enumeração, CSRF e session fixation.
9. Interface conforme DESIGN.md, com revisão independente de acessibilidade/UX.

Estas escolhas substituem os pontos condicionais da proposta anterior sobre verificação de e-mail e tempos de sessão/reset. As alternativas Keycloak/OIDC e gerenciadas ficam como histórico/evolução futura, não decisões abertas para iniciar esta implementação.

## D1 — Cadastro e ativação seguros: aprovada e implementada na entrega local

**Decisão aprovada:** solicitar somente e-mail → enviar prova → titular escolhe senha em confirmação explícita → ativar atomicamente, sem login automático. Conta pendente não possui credencial utilizável. GET do link não ativa, não consome token nem altera senha. A Etapa 1 entregou persistência e guards; após a aprovação de D4–D6, a entrega funcional local implementou cadastro, comprovação e ativação explícita.

Alternativa: receber senha no cadastro e exigir nova definição ou comprovação explícita após a prova de e-mail. Isso acrescenta estado e caminhos de recuperação, sem benefício necessário ao MVP. Nunca ativar silenciosamente a senha preliminar: se um atacante iniciou o cadastro usando o e-mail da vítima, a confirmação da vítima não pode habilitar uma senha conhecida pelo atacante. Essa é uma análise de ameaça da equipe, não um fluxo já implementado.

Token fica vinculado a finalidade, identidade, geração e validade. Consumo, criação da credencial e transição PENDING_EMAIL → ACTIVE ocorrem na mesma transação. Duas confirmações concorrentes têm um vencedor. Reenvio/cadastro repetido não altera senha ou dados de conta ativa; reset não serve de atalho para ativar pendente.

## D2 — Identificador de e-mail: aprovada; perfil de implementação documentado

**Decisão aprovada:** local-part ASCII preservado, domínio normalizado para lowercase/IDNA, endereço original preservado e chave de comparação única no PostgreSQL. Não remover pontos nem sufixos `+tag`; os fluxos futuros deverão aplicar a mesma regra. A fundação usa dot-atom ASCII e JDK 21 IDN com STD3 (IDNA2003, não IDNA2008/UTS46), com limites, exemplos e mapeamentos explicitados em IDENTITY_FOUNDATION.md. Não executa DNS, envio ou comprovação da caixa postal.

Alternativas históricas não adotadas: lowercase do endereço inteiro melhora conveniência, mas pode colidir; suporte completo a local-part internacionalizado ampliaria compatibilidade necessária. `User@EXAMPLE.com` equivale a `User@example.com`, mas `user@example.com` é outro identificador. Isso não prova que cada identificador corresponda a uma caixa postal distinta. A política aprovada é explícita; não depende da collation padrão. Mudança do perfil IDNA exigirá análise de compatibilidade e de colisões antes de usuários reais.

## D3 — Senha e encoder: política aprovada, configuração candidata sujeita ao benchmark

**Política aprovada:** 15–128 pontos de código Unicode, espaços permitidos, sem trim ou truncamento. Aplicar NFC explicitamente antes da contagem/hash, com limites e exemplos documentados; frontend futuro deverá respeitar o mesmo contrato, sem ter sido implementado. Usar blocklist local versionada no estabelecimento de credencial, sem consulta externa. Nesta etapa a lista é uma baseline autoral de 21 entradas com proveniência e limitação de cobertura registradas; não é corpus abrangente de senhas comprometidas. Verificação não reaplica uma blocklist posteriormente ampliada ao hash existente.

Alternativas de tamanho/normalização foram encerradas pela aprovação D3. Não criar regras de composição ou diminuir os limites aprovados. Rejeitar UTF-16 malformado e limitar entrada bruta antes da normalização é proteção de implementação explicitada no contrato.

**Candidato autorizado para medição:** encoder Spring com HMAC-SHA256 explícito, salt aleatório de 16 bytes, saída de 256 bits e 600.000 iterações; ID versionado identifica algoritmo, normalização, iterações, tamanho de salt e saída. Não assumir que `defaultsForSpringSecurity_v5_8()` representa 600 mil: seu padrão histórico é 310 mil. Hash bruto PBKDF2 não descreve sozinho toda a configuração. Não há alegação de conformidade FIPS/NIST; resultados medidos e recomendação constam na validação da Etapa 1, sem redução silenciosa de parâmetros.

**Protocolo proposto:** registrar CPU/vCPU, RAM, limites do processo, JDK/provider, versões, tamanhos de entrada e parâmetros; aquecer 20 operações, medir 200 operações sequenciais para encode e matches, depois concorrência 2/4/8; reportar p50/p95/p99, throughput, CPU/memória e saturação. Usar dados sintéticos curtos/longos/Unicode e verificação negativa equivalente à de identidade inexistente.

Orçamento recomendado para aprovação: p95 de encode e matches de até 1 segundo em referência representativa documentada, com limite de concorrência dimensionado pelos resultados. Referência de hardware e capacidade real são saída obrigatória do benchmark; não prometer SLA de produção sem ambiente-alvo. Meta mais estrita, como 500ms, reduz margem de capacidade e exige medição antes de compromisso. Não transformar tempo de runner compartilhado em gate rígido. Se o piso não couber, registrar bloqueio e rever capacidade/admissão; não reduzir iterações nem trocar algoritmo automaticamente. Aprovação dos critérios não equivale a benchmark aprovado.

## D4 — Validade da prova e retenção: aprovada

**Recomendação:** prova de e-mail de 30 minutos (distinta do reset), uso único e cooldown de reenvio de 60 segundos. Reenvio invalida o token anterior atomicamente. Alternativa de 24h reduz atrito e aumenta a janela de exposição; não herdar automaticamente o TTL do reset.

Proposta de retenção: pendentes expiram após sete dias, sem reservar indefinidamente o endereço nem preservar credencial anterior; mensagens locais por até 24h; tokens consumidos/expirados removidos em até 24h; contadores removidos em até 24h após a janela. Antes de ativar, verificar também a validade do pendente, independentemente da execução da limpeza. Pendência expirada permite nova solicitação genérica e invalida provas antigas. Contas ativas não são apagadas por essa política.

Retenção maior facilita suporte, mas acumula dados e exige justificativa. Esta proposta não fixa retenção de contas ativas nem conclui avaliação jurídica/LGPD. Não executar purga sobre dados existentes ou ambientes reais por este ADR; implementação e validação da limpeza usarão dados descartáveis em tarefa autorizada.

## D5 — Política contra abuso: aprovada

**Recomendação:** estado de limites no PostgreSQL existente, com atualização atômica, TTL, cardinalidade limitada, limpeza e proteção global de concorrência de hashes. Alternativa em memória é mais simples para DEV de uma instância, mas perde contadores no restart e não coordena réplicas. Redis acrescentaria infraestrutura sem necessidade demonstrada. A opção PostgreSQL exige medir contenção e carga; o rate limit também nega por segurança se seu estado não puder ser verificado.

Baseline proposto, sujeito a revisão com o benchmark: cinco falhas de login por identificador/15min e 50 por origem/15min; envio de prova/reset até três por identificador/hora e dez por origem/hora, cooldown de 60s. Limitar também consumo de tokens e cadastro antes de trabalho caro. Usar espera/backoff temporários, sem alterar status da conta para bloqueio permanente; requisições já bloqueadas não renovam indefinidamente a janela. Penalização excessiva por identificador pode causar negação de serviço ao titular; limites por origem podem afetar redes compartilhadas.

Aplicar a mesma política a identificadores existentes/inexistentes; manter resposta genérica para envio, inclusive quando suprimido, sem alterar credenciais. Contrato de 429/Retry-After para demais limites deve ser consistente e não revelar existência. Derivar origem da conexão/proxy explicitamente confiável; não confiar em X-Forwarded-For arbitrário. O teto global de hashing será fixado pelo benchmark antes do login. Números são propostas do projeto, não valores normativos da OWASP/NIST.

## D6 — Transporte local e inspeção: aprovada

**Recomendação:** adaptador DEV grava mensagens em diretório local ignorado, com acesso restrito ao desenvolvedor e retenção de 24h; testes capturam mensagens em memória. Sem console, endpoint HTTP público, envio externo ou inclusão de mensagens no Git/artefatos CI. Token em mensagem local é dado sensível mesmo em DEV; origem dos links vem de configuração confiável, nunca Host recebido.

Alternativa: caixa SMTP local como Mailpit, com portas em loopback, imagem/versão verificadas e dados descartáveis. Facilita inspeção manual e adiciona serviço/Compose; depende de aprovação específica. Capturador somente em memória basta para testes, mas não oferece inspeção manual persistente. Perfil de produção não pode iniciar silenciosamente com adaptador DEV nem alegar entrega real.

## D7 — Ferramentas de browser: aprovação antes da Etapa 5

Propor `@playwright/test` e `@axe-core/playwright` como dependências de desenvolvimento, após confirmar versões, licença e compatibilidade. Recomendação: Chromium desktop/mobile inicialmente, Firefox/WebKit sob demanda. Alternativa manual não tem a mesma reprodutibilidade e não deve ser apresentada como automação concluída. Nenhuma instalação por este ADR; skills não concedem essa autorização. Axe complementa teclado/leitor de tela/zoom e não certifica WCAG.

## Decisões anteriores à produção, sem bloquear fundação local

Provedor e remetente de e-mail reais, origem/HTTPS, disponibilidade/custo, referência de capacidade de produção, limites operacionais, retenção de contas/auditoria e resposta a incidentes continuam para aprovação específica. Nenhuma contratação, integração externa ou deploy está autorizado. Aceite local não comprova entrega real de e-mail ou prontidão para produção.

## Controles técnicos e fronteiras já necessários

- Preservar Java 21 e Spring Boot 4.1.1. Revisão backend identificou Spring Security 7.1.1 e Session 4.1.1 no BOM local; reconfirmar artefatos/compatibilidade antes de instalar o starter JDBC de sessão, somente na etapa em que será usado. Não atualizar frameworks incidentalmente.
- Migrações Flyway aditivas, V1 preservada. Usuário global com ID estável, e-mail original/chave única, status, datas e auth_version; credencial separada e ausente antes da ativação. Tokens e tabelas de sessão entram somente quando necessários; inicialização automática do schema JDBC desativada.
- Sessão guarda ID, versão e instante da autenticação, sem hash/entidade de credencial. Inatividade JDBC e limite absoluto são controles distintos; atividade ou rotação de ID não estende o teto de 8h sem nova autenticação. Inatividade é medida pelo servidor a partir do acesso à sessão, não por eventos de mouse; evitar polling que renove sessão sem necessidade.
- Login somente ACTIVE, usando estratégia Spring de sessão, rotação do ID e persistência explícita do SecurityContext. Logout POST invalida sessão. CSRF cobre mutações, inclusive login/logout/ativação/reset; o browser renova seu token conforme o fluxo Spring. Na Etapa 2, o contrato pré-autenticação deve ser explícito: usar suporte Spring de cookie/header CSRF com cookie host-only e validação da origem, sem contexto autenticado ou sessão de identidade em memória implícita; testar o mecanismo real antes de expor a primeira rota. Tabelas/sessões autenticadas JDBC continuam na Etapa 3.
- Reset/desativação incrementam auth_version e removem sessões; cada acesso protegido verifica status/versão, impedindo uso de sessão antiga salva por corrida. Isso não desfaz efeitos já confirmados antes da revogação. Banco indisponível não autoriza por fallback.
- Token aleatório com 256 bits de entropia, armazenado somente por digest SHA-256, vinculado a finalidade/usuário/geração/validade. Reset dura 30 minutos, apenas ACTIVE, sem ativar pendente ou autenticar automaticamente. Consumo, novo hash e incremento de auth_version pertencem à mesma transação. A exclusão física das sessões pode ocorrer em transação própria do Spring Session; a garantia de revogação vem da validação de status/versão em cada acesso após o commit, não da atomicidade presumida dessa exclusão.
- Mesma origem visível admin/API por encaminhamento controlado de `/api`; sem cache compartilhado de identidade/SSR. Cookie host-only, HttpOnly, SameSite=Lax, Secure sob HTTPS e prefixo `__Host-` quando seus requisitos forem atendidos; nome/configuração distinta para HTTP local. Não compartilhar cookie com vitrines ou colocar sessão em localStorage.
- GET de links é inerte. Páginas de ação sem recursos de terceiros, com Referrer-Policy restritiva e sem tokens em logs/analytics/traces. CSRF e links de ação só transportam tokens pelo contrato aprovado; jamais vazar segredo como diagnóstico ou props públicas.
- Conta ativa não concede empresa, OWNER ou membership. Campos de cliente não mudam role/status privilegiado. Tenant futuro depende de identidade e vínculo validado no backend, conforme ADR-0004; OIDC futuro usa issuer/subject sem vínculo automático por e-mail.

## Revisões, evidências e prontidão

Architect confirmou limites e etapas; backend verificou BOM/API e persistência/revogação; security analisou ativação maliciosa, abuso e retenção; QA definiu testes concorrentes, de browser e critérios de evidência. A preferência de architect/backend por memória limitada em DEV foi ponderada frente à recomendação security de contadores PostgreSQL: D5 apresenta os custos e mantém a decisão humana pendente.

A revisão inicial foi documental. Após aprovação humana de D1/D2/D3, a Etapa 1 foi implementada e passou à validação local, registrada separadamente com seus resultados reais. A CI anterior da SC-001/UI/UX não comprova a fundação atual nem autenticação funcional. A publicação da Etapa 1 foi autorizada após os 32 testes locais e o benchmark. D4–D6 foram aprovadas para a entrega funcional; D7 continua proposta separada, sem instalação autorizada.

**SEC-003 continua aberta, sem correção nem aceite formal/automático de risco.** Referência: [parecer de segurança SC-001](../../security/SC-001-REVIEW.md). Esta revisão não repete a auditoria nem concede exceção; CI verde não encerra o achado.

## Fontes técnicas consultadas na revisão

- [Spring PasswordEncoder/PBKDF2](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.html) e [armazenamento de senhas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html) e [JdbcIndexedSessionRepository](https://docs.spring.io/spring-session/reference/api/java/org/springframework/session/jdbc/JdbcIndexedSessionRepository.html).
- [Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) e [gerenciamento de sessão](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).
- [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Email Validation](https://cheatsheetseries.owasp.org/cheatsheets/Email_Validation_and_Verification_Cheat_Sheet.html) e [Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
- [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html). Referências não representam certificação; quotas, retenções e orçamento de desempenho são propostas do projeto.
