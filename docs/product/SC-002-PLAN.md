# SC-002 — Prontidão e implementação incremental

Data: 2026-10-15. **D1/D2/D3 e somente Etapa 1 autorizadas pelo responsável.** Fundação implementada para validação local; aceite, commit/push e Etapas 2–6 aguardam autorização. Contrato: [IDENTITY_FOUNDATION.md](../architecture/IDENTITY_FOUNDATION.md); resultados reais: [SC-002-STAGE-01-VALIDATION.md](../quality/SC-002-STAGE-01-VALIDATION.md).

Esta revisão substitui o planejamento anterior da branch `docs/sc-001-governance` para a SC-002, sem mesclar aquela branch ou modificar registros da SC-001. A base preliminar e decisões pendentes estão no [ADR-0005](../architecture/adr/0005-identity-authentication.md). Interfaces seguem [DESIGN.md](../design/DESIGN.md); a matriz verificável está em [SC-002-TEST-PLAN.md](../quality/SC-002-TEST-PLAN.md).

## Escopo e estado de prontidão

Implementar identidade global do lojista, comprovação de e-mail, senha, login/sessão, logout e recuperação no monólito existente. A condição de ativação por e-mail é obrigatória na base preliminar do responsável. Não há cadastro público de operador, concessão de OWNER, empresa/loja/membership, SSO, login de compradores, catálogo, pagamentos ou IA nesta tarefa.

**Fundação autorizada e em validação:** as quatro revisões técnicas convergiram na viabilidade. D1/D2/D3 foram aprovadas; D4–D7 continuam pendentes e bloqueiam as etapas indicadas abaixo. A autorização da Etapa 1 não autoriza automaticamente a seguinte.

## Registro de decisões e gates

| ID | Recomendação | Alternativa e impacto | Quando decidir |
| --- | --- | --- | --- |
| D1 | Cadastro e-mail primeiro; prova antes de definir senha; sem auto-login. | Fluxo aprovado como contrato; nenhuma ativação foi implementada na fundação. | Aprovada para orientar Etapa 1. |
| D2 | Local-part ASCII preservado; domínio lowercase/IDNA; chave única documentada, sem remoção de pontos/tags. | Perfil JDK IDNA2003/STD3 e limites documentados; não há comprovação de caixa postal. | Aprovada; contrato e testes na Etapa 1. |
| D3 | Senha 15–128 pontos Unicode/NFC; blocklist local; PBKDF2 versionado e candidato 600 mil/salt16/256bits. | Benchmark obrigatório mede percentis, concorrência e recursos; não garante capacidade de produção. | Política aprovada; candidato medido antes da recomendação final. |
| D4 | Verificação 30min; cooldown 60s; pendentes sete dias; mensagens/tokens/contadores com limpeza delimitada conforme ADR. | Verificação 24h e retenções maiores reduzem atrito/aumentam exposição e dados retidos. | Antes da Etapa 2 ou uso de dados reais. |
| D5 | Limites atômicos no PostgreSQL e quotas iniciais do ADR, sem bloqueio permanente; teto de hashes pelo benchmark. | Memória limitada é mais simples, perde estado em restart e não coordena instâncias; PostgreSQL exige avaliar carga/contenção. | Antes da Etapa 2; calibrar login antes da Etapa 3. |
| D6 | Mensagens DEV em diretório ignorado/restrito, testes em memória, sem console/endpoint público; retenção 24h. | Caixa SMTP local facilita inspeção e adiciona serviço/Compose; capturador em memória sozinho não atende inspeção manual persistente. | Antes da Etapa 2. |
| D7 | Aprovar Playwright e Axe como dependências de desenvolvimento, versões a conferir. | Revisão apenas manual tem menor repetibilidade e não comprova automação; browser extra aumenta tempo de CI. | Antes da Etapa 5. |

O transporte local está preliminarmente escolhido; D6 decide apenas o mecanismo de inspeção seguro. Sessão 30min/8h, reset 30min, algoritmo, framework e exigência de e-mail não são reapresentados como escolhas abertas. Provedor real/TLS/custos/retenção operacional exigem decisão antes de produção e não bloqueiam benchmark/schema local.

## Etapa 1 — Fundação de identidade e benchmark

**Entrada atendida:** D1/D2/D3 aprovadas e autorização explícita exclusivamente desta etapa. Resultados e pendências de aceite ficam no relatório de validação; esta entrada não autoriza commit/push.

**Mudanças previstas:** pacote identity mínimo, migração aditiva de usuário global/credencial e índices necessários; ID estável, e-mail original/chave única, status, datas e auth_version. Pendente sem credencial utilizável. Encoder Spring com parâmetros versionados, política aprovada e blocklist local de origem/licença conferidas. Nenhuma tabela antecipada de tenant, tokens ou sessões: entram em suas etapas. Nenhuma rota pública, tela, envio ou rotina de purga.

**Aceite e testes:**

- Flyway aplica V1 preservada e a nova migração em PostgreSQL/Testcontainers; reexecução não duplica estruturas. Erro de migração/constraint não deixa estado parcial.
- Unicidade de e-mail e correspondência da política D2 comprovadas, inclusive concorrência; FK/unicidade da credencial evita duplicidade e ligação a usuário inexistente.
- Mesma senha gera salts/hashes distintos; matches válido/inválido, ID de parâmetros e rehash são verificados; Unicode/limites sem truncamento, blocklist e ausência de plaintext são cobertos.
- Benchmark conforme D3 registra hardware/JDK, parâmetros, amostras, concorrência, percentis e capacidade. Se falhar, relatar bloqueio; não reduzir piso de segurança para obter resultado verde. Medições não ficam confundidas com testes funcionais da CI.
- Backend verify e seis testes existentes permanecem aprovados, ampliando expectativas de migrações sem retirar os cenários V1/health/segurança. Nenhum endpoint de identidade fica acessível.

**Arquivos previstos após autorização:** POM somente se dependência necessária à etapa, fontes/testes identity, migração Flyway e documentação de execução/evidências. Starter Session JDBC, frontend e Docker Compose não são necessários nesta etapa. Instalação/upgrade deve ter compatibilidade confirmada; nenhum framework será atualizado incidentalmente.

**Saída para revisão:** diff restrito, relatório do benchmark e matriz de resultados; aprovação do gate não equivale a aprovação das Etapas 2–6.

## Etapa 2 — Prova de e-mail, ativação e proteção inicial contra abuso

**Entrada:** Etapa 1 aceita; D4/D5/D6 aprovadas. Definir contratos de entrada/erros/CSRF antes da primeira rota pública.

**Mudanças previstas:** tokens por finalidade/geração, digest/TTL/consumo; origem configurada; adaptador DEV e captura de teste; limites antes de envio/hash. Pedido de cadastro e reenvio genéricos para existente/inexistente/pendente; conta ativa não é alterada. GET permanece inerte, com contrato de consulta/redirecionamento seguro, sem antecipar UI; POST com CSRF define senha + consome prova + ativa na mesma transação. Contrato CSRF pré-autenticação com suporte Spring de cookie/header, cookie host-only e origem validada, sem persistir identidade em sessão de memória implicitamente; JDBC autenticado permanece na Etapa 3. Sem auto-login. Política de expiração/purga somente em dados descartáveis autorizados, sem apagar dados existentes nesta atividade.

**Aceite e testes:** ID-04 a ID-09 e ID-20 da matriz: pré-cadastro por atacante não deixa credencial anterior utilizável, comprovado na persistência/serviço; a tentativa HTTP de login com senha anterior entra na regressão da Etapa 3, quando existir a rota. GET/scanner não muda estado; duas ativações dão um vencedor; finalidade trocada, token expirado/adulterado/reutilizado e geração anterior falham; pendente expirado não reserva indefinidamente endereço; reenvio e quotas concorrentes funcionam; sem segredo em respostas/logs. CSRF/origem indevidos negam a mutação já nesta etapa. Banco indisponível nega ação, sem fallback. UI de produção não é criada.

## Etapa 3 — Login, sessão JDBC e logout

**Entrada:** Etapa 2 aceita e capacidade/limites D5 calibrados. Confirmar starter/schema compatíveis com BOM efetivo antes de adicionar.

**Mudanças previstas:** starter JDBC de sessão gerenciado pelo Boot, tabelas Flyway da versão efetiva, schema automático desativado; login somente ACTIVE, principal mínimo e persistência do contexto pela estratégia Spring; `/me`, CSRF e logout POST. Limites 30min/8h independentes; auth_version/status por acesso e revogação resistente a corrida. Sem endpoint público de administração/desativação de usuários.

**Aceite e testes:** ID-10 a ID-14: fixation/CSRF, 401 em GET anônimo protegido e 403 em negação apropriada; cookie HTTP local/HTTPS, origem indevida, persistência/reinício, expiração em relógio controlável, limpeza segura de sessão e dois usuários sem compartilhar identidade. Requisição antiga não ressuscita autorização após revogação. Inatividade, teto absoluto e falha de banco não se confundem. Nenhuma garantia empresarial A/B é declarada.

## Etapa 4 — Recuperação e revogação de credenciais

**Entrada:** Etapa 3 aceita; contrato de recuperação e limites revisados.

**Mudanças previstas:** solicitação genérica para contas ACTIVE; token específico de 30min, novo pedido invalida anterior; confirmação POST/CSRF troca senha e consome token atomicamente; incremento de auth_version/invalidação de sessões. Sem ativar PENDING, alterar cadastro por pedido anônimo ou autenticar automaticamente.

**Aceite e testes:** ID-15/ID-16: replay, expiração, adulteração, finalidade errada, pedidos concorrentes e rollback; duas confirmações dão um vencedor; falha não altera senha parcialmente; ambas as sessões antigas são negadas, inclusive após restart e tentativa concorrente de persistência. Pedido para pendente não ativa nem fornece atalho de credencial.

## Etapa 5 — Integração administrativa e testes de interface

**Entrada:** Etapas backend aceitas, D7 aprovada e versões de ferramentas conferidas. Consultar DESIGN.md, FRONTEND.md e QA.md; nenhuma decisão visual relevante muda sem aprovação.

**Mudanças previstas:** telas mínimas de solicitação/prova/definição de senha, login, identidade atual, logout e recuperação; encaminhamento same-origin `/api`, cookie/CSRF e SSR no-store. Reutilizar componentes locais quando necessários, sem kit UI ou conteúdo comercial. `design-taste-frontend` não se aplica a autenticação; referências visuais não autorizam bibliotecas.

**Aceite e testes:** ID-17 a ID-19: jornada real browser → API → PostgreSQL → resposta; labels/autocomplete/colar/gerenciadores, teclado/foco/erros e loading acessíveis; Enter/clique repetido não dispara duas submissões; nova tentativa após falha; 320/390/768/1440px, zoom/movimento reduzido; Axe + revisão manual WCAG, sem certificação automática. Sem dados cruzados de identidade por SSR/cache ou segredo em storageState/traces/URLs alheias ao fluxo aprovado.

## Etapa 6 — Revisão e evidências finais

**Entrada:** etapas anteriores aceitas e todos os cenários relevantes executados.

**Aceite:** backend verify, instalação limpa admin, lint, typecheck, build, smoke e novas suítes relevantes aprovados no SHA revisado. Manter checks `backend`/`admin` e proteção de main; adaptar organização da suíte browser sem enfraquecer gates. Segurança/QA independentes revisam ameaças e evidências, README/configuração local e rollback não destrutivo de aplicação. Qualquer bloqueio fica explícito, sem teste skipped declarado aprovado.

Registrar por cenário comando, SHA, data, ambiente, contagens, relatório e URL de CI. Não provar entrega de e-mail real com transporte DEV. SEC-003 fica aberta em registro separado, sem exceção automática; aceite funcional não encerra o risco do tooling.

## Revisões e resultado desta atividade

- Architect: confirmou fronteiras identity/tenant e gate da Etapa 1; destacou risco de ativar senha pré-cadastrada.
- Backend: verificou BOM/API Spring e requisitos de transação, versão de autenticação, migrações e benchmark; sem instalação.
- Security: revisou pré-cadastro, política de e-mail/senha, quotas/retenção e proteção das mensagens; sem auditoria nova ou aceite de SEC-003.
- QA: definiu matriz concorrente/HTTP/browser e separação entre evidência planejada e executada.

O planejamento inicial foi documental. A execução autorizada da Etapa 1 adiciona apenas persistência, política/encoder, migração V2, testes e benchmark; resultados reais estão na validação específica. Nenhuma funcionalidade HTTP de identidade foi implementada. D4–D7 não estão tacitamente aprovadas.

SEC-003 permanece aberta conforme [SC-001-REVIEW.md](../security/SC-001-REVIEW.md); não foi reavaliada nem aceita. Somente a fundação da SC-002 foi iniciada; as Etapas 2–6 continuam proibidas sem autorização.
