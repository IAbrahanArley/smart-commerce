# SC-002 — Plano de testes proposto

Estado: **PLANEJAMENTO; implementação e execução aguardam aprovação humana**. Data: 2026-10-15.

Base: [Sprint 1](../product/SPRINT_01.md), [PRD](../product/PRD.md), [arquitetura](../architecture/ARCHITECTURE.md) e [ADR-0005 proposto](../architecture/adr/0005-identity-authentication.md). Os contratos abaixo são critérios de teste para a alternativa recomendada, não funcionalidades implementadas ou decisões aprovadas.

## Escopo e decisões necessárias

Proposta: identidade global do lojista, credencial local com Spring Security, sessão opaca persistida por Spring Session JDBC no PostgreSQL, frontend administrativo sob a mesma origem visível por proxy de API. Nenhum JWT próprio, token de autenticação em localStorage, IdP, SMTP de produção ou serviço pago será criado na fase de planejamento.

Antes dos testes de aceite, aprovar a alternativa de autenticação e o ADR; parâmetros PBKDF2/senha; limites de sessão/recuperação; política de comprovação de e-mail; contrato do transporte local; códigos/formatos das respostas e regras de limitação de tentativas. Os valores abaixo são propostas condicionais do ADR: PBKDF2-HMAC-SHA256 com piso de 600.000 iterações sujeito a benchmark, senha mínima de 15 caracteres e máximo de 128 sem truncamento, sessão com 30 minutos ociosos/8 horas absolutas, token de recuperação de 256 bits e validade de 30 minutos.

Cadastro cria somente User/credencial. Não concede OWNER, não cria empresa, loja ou membership e não permite cadastro público de operador da plataforma. Domínio e isolamento empresarial A/B permanecem em SC-003/004. A SC-002 pode testar que duas identidades não compartilham a própria sessão ou dados pessoais de identidade, sem apresentar isso como isolamento multi-tenant concluído.

## Matriz de testes após aprovação

| ID / camada | Cenário | Resultado esperado / evidência |
| --- | --- | --- |
| ID-01 — unidade e contrato | Normalização de e-mail; entrada inválida; senha com espaços/Unicode, limites e comprimento excedido | Política documentada e determinística; não remover pontos/sufixos por regra específica de provedor; não truncar senha. Respostas de validação não expõem dados internos. |
| ID-02 — integração PostgreSQL | Dois cadastros simultâneos para o mesmo e-mail normalizado | Constraint e transação permitem uma única identidade/credencial; a perdedora recebe resultado controlado sem stack trace. Contrato de duplicidade deve conciliar UX com decisão antienumeração. |
| ID-03 — contrato e banco | Enviar role, tenant_id, membership, status privilegiado ou campos desconhecidos | Rejeitar ou ignorar conforme contrato aprovado, sem efeito privilegiado no banco; nenhum papel OWNER/operador ou vínculo empresarial é criado. |
| ID-04 — unidade e integração | Codificar/verificar senha; mesma senha para usuários diferentes; senha errada; atualização de parâmetros | PasswordEncoder do framework com identificador versionado e salt independente; hashes diferem; validação correta; nenhum texto em claro ou criptografia reversível. Benchmark separado mede custo e latência sob concorrência antes de fechar parâmetros. |
| ID-05 — contrato HTTP | Identidade atual sem sessão, sessão inválida/expirada e identidade válida sem permissão | GET protegido sem autenticação retorna 401; identidade autenticada sem autorização retorna 403. Mutação sem CSRF pode retornar 403 antes da autenticação; testar essa precedência explicitamente em vez de exigir 401 em toda requisição. |
| ID-06 — contrato e browser | CSRF de pré-login, login válido, identidade atual e logout POST | Fluxo persiste SecurityContext pelo framework, renova identificador de sessão no login e obtém novo token CSRF quando necessário. Não aceitar logout por GET; ausência/token CSRF inválido falha antes de qualquer efeito mutável. |
| ID-07 — integração e browser | Fixation: iniciar sessão anônima, autenticar e tentar reutilizar cookie anterior | Identificador muda; cookie antigo não acessa identidade autenticada. Login JSON deve exercitar a estratégia real de sessão, não apenas mockar autenticação. |
| ID-08 — contrato e observação controlada | Login com conta inexistente, senha incorreta, conta inativa, bloqueio temporário; recuperação com e-mail existente/inexistente | Respostas funcionais equivalentes nos casos sensíveis, sem informar existência/status. Medir distribuições de latência aquecidas e com mesma carga; definir orçamento de diferença antes de avaliar, sem assert frágil de milissegundo exato ou alegação de tempo constante. |
| ID-09 — unidade/integração | Tentativas por origem e identificador, concorrência, recuperação após espera e origem forjada | Limites aprovados são aplicados e retornam contrato de erro controlado; espera expira; sem bloqueio permanente que terceiro possa impor à conta. Não confiar em X-Forwarded-For arbitrário fora da configuração de proxy confiável. Limite em memória, se escolhido, deve declarar perda após restart e limite por instância. |
| ID-10 — integração PostgreSQL | Logout com cookie reapresentado; idle/limite absoluto; cleanup de expiradas | Sessão antiga é negada no servidor, cookie removido com atributos coerentes; atividade não estende o teto absoluto. Relógio controlável testa fronteiras e vencimento sem esperas de 30 minutos/8 horas. Cleanup não remove sessões válidas. |
| ID-11 — integração PostgreSQL | Reiniciar contexto da aplicação mantendo banco/sessão válida | Sessão não expirada permanece utilizável conforme política; status/versão de autenticação continuam validados. Flyway cria tabelas versionadas sem schema automático e reexecução não duplica estruturas. Não reutilizar volume local em testes destrutivos. |
| ID-12 — integração concorrente | Reset/troca de senha/desativação revoga sessões enquanto requisição antiga tenta salvar sessão | Incremento da versão de autenticação e remoção por principal impedem acesso futuro, inclusive se uma requisição concorrente persistir estado antigo. Usar duas sessões e barreira de sincronização; não prometer desfazer efeitos já confirmados antes da revogação. |
| ID-13 — unidade/integração | Criar token de recuperação, alterar token, expirar, consumir e reutilizar | Token aleatório de finalidade exclusiva; banco guarda somente digest, TTL/consumo/usuário. Capturar token somente no adaptador de teste; conferir ausência do valor bruto no banco, resposta pública e logs. Token expirado, adulterado ou reutilizado falha. |
| ID-14 — integração concorrente | Duas confirmações simultâneas do mesmo reset; nova solicitação invalida anterior | Um único vencedor; alteração de senha, consumo e revogação são atômicos. Perdedor não sobrescreve senha; falha transacional não deixa consumo/senha parcialmente gravados. Não autenticar automaticamente após reset. |
| ID-15 — contrato e adaptador DEV | Solicitação de recuperação/notificação; Host e headers de encaminhamento maliciosos | Adaptador local captura mensagem de teste sem SMTP real nem link em logs; origem do link vem de configuração confiável. Resposta não devolve token. Perfil de produção não pode usar silenciosamente o adaptador DEV ou declarar entrega real. |
| ID-16 — condicional à decisão de produto | Comprovação de e-mail, token separado, expiração e replay | Se aprovada, verificação e recuperação não aceitam tokens uma da outra; conta não verificada não ganha sessão administrativa normal, e eventual sessão pré-verificação acessa somente ações explicitamente restritas. E-mail não verificado não é apresentado como comprovado. Se adiada, registrar a limitação e não criar bypass implícito ou ativação fictícia. |
| ID-17 — HTTP/HTTPS e browser | Cookie em DEV HTTP e perfil HTTPS; domínio/path; isolamento de vitrines | HttpOnly, host-only, SameSite=Lax e path aprovado; Secure=true em HTTPS, exceção Secure=false apenas local/teste explícito. Não compartilhar domínio com vitrines. SameSite não substitui CSRF. Testar perfil HTTPS sem deploy ou compra de infraestrutura. |
| ID-18 — browser/proxy/SSR | Proxy preserva Set-Cookie/CSRF; SSR consulta identidade; logout e navegação posterior | Cookies encaminhados apenas ao backend confiável, resposta de identidade no-store e sem cache compartilhado; usuário B não recebe conteúdo de A. Nenhuma senha/sessão em localStorage, URL, bundle ou props públicas. Tela de erro não exibe credenciais. |
| ID-19 — browser e contrato | Origem indevida e, somente se aprovado, CORS entre origens | Same-origin proposto funciona; mutações cross-site não autorizadas falham. Se houver CORS, allowlist exata com credenciais, sem wildcard/reflexão arbitrária; browser verifica preflight e cookies. |
| ID-20 — regressão e revisão | Logs, respostas, principal/sessão JDBC, erros e saúde | Sem senha, hash de senha, cookie, token bruto ou e-mail completo em logs/respostas; principal guarda ID/versão, sem entidade de credencial. Health mínimo e bloqueios de Actuator continuam cobertos; lista de rotas públicas é revisada com os novos contratos. |

## Estratégia de execução e CI proposta

1. Testes unitários para regras de identidade, hash/validação via framework, TTL e limites com relógio controlável. Evitar testes que só reproduzem implementação ou comparam valores de salt aleatório.
2. Testes de contrato HTTP para códigos, schemas, ausência de segredos e fronteiras CSRF/sessão. Testar a cadeia Spring real, sem substituir os controles de segurança por mocks que façam o teste passar.
3. Integração com PostgreSQL/Testcontainers para Flyway, sessões JDBC, unicidades, revogação e atomicidade. Concorrência usa transações/conexões independentes e barreiras; conferir estado final, vencedores e ausência de efeito da tentativa negada. Não simular exclusão mútua apenas com chamadas sequenciais.
4. Browser para jornada completa e atributos de cookie/proxy/SSR, após aprovar ferramenta e instalação. Proposta: Playwright em teste local/runner, com backend e PostgreSQL descartáveis e transporte de e-mail falso controlado. Evitar testar somente HTML estático para declarar login funcional.
5. CI mantém `backend` e `admin`, incluindo os seis testes backend existentes, lint, tipos, build e smoke. Acrescentar integração de identidade ao ciclo Maven e suíte browser ao pipeline conforme decisão de organização. Só alterar nomes/checks obrigatórios com alinhamento da proteção de main; nenhum teste de segurança deve ser ignorado por falta de Docker/serviço.

Comandos exatos e scripts adicionais serão definidos na implementação aprovada. `mvn verify`, lint/build e smoke já existentes não demonstram autenticação enquanto seus novos testes não existirem. Falhas devem produzir relatórios mínimos revisados, sem capturar cookies, tokens ou mensagens de recuperação em artefatos públicos; traces/browser e-mail precisam de redação ou exclusão deliberada.

## Incrementos e evidência de aceite

- Contratos e dados aprovados: registrar decisões pendentes no ADR e matriz; criar apenas migrações de identidade/sessão necessárias. Validar ID-01 a ID-05 e unicidade concorrente.
- Sessão e fronteira web: implementar após aprovação e comprovar ID-06 a ID-12, ID-17 a ID-20 com PostgreSQL real e browser.
- Recuperação/comprovação de e-mail: comprovar ID-13 a ID-16 com transporte de teste; separar aceite local de prontidão de comunicação em produção.
- Consolidar resultados por ID, comando, SHA, ambiente, contagens, relatório e URL de CI. Exigir revisão independente de segurança e aprovação humana; SEC-003 continua acompanhada separadamente, sem exceção automática por CI verde.

## Executados versus pendentes

**Executados nesta atividade:** leitura documental, desenho desta matriz e revisão dos limites propostos. **Nenhum teste da SC-002, benchmark, instalação ou código foi executado/criado.** Todos os cenários desta matriz permanecem propostos e pendentes de aprovação/implementação.

Concorrência de estoque, idempotência financeira e webhook repetido/fora de ordem continuam fora desta tarefa. Isolamento entre empresas exige fixtures com duas identidades/duas empresas e testes de consultas/comandos na SC-003/004; a SC-002 não pode certificar um domínio inexistente.
