# SC-002 Etapa 1 — Validação local para aceite humano

Data: 2026-10-15. Escopo autorizado: fundação de identidade após aprovação de D1/D2/D3. **Registro da validação local anterior à publicação. A publicação da Etapa 1 foi posteriormente autorizada; a próxima entrega aguarda D4–D6.** Base local: main no SHA `c1014ed38fc13d56ed00c64acb34674baf60c17a`; esse SHA não contém as alterações aqui examinadas. Não existe CI remota desta implementação.

Contrato e modelo: [IDENTITY_FOUNDATION.md](../architecture/IDENTITY_FOUNDATION.md). Decisões: [ADR-0005](../architecture/adr/0005-identity-authentication.md). Próximos gates: [plano](../product/SC-002-PLAN.md) e [matriz QA](SC-002-TEST-PLAN.md).

## Modelo e migração

- V2 aditiva cria identity_user (UUID global, e-mail original/chave única collation C, status, auth_version e datas) e identity_local_credential (PK/FK usuário, hash versionado e datas).
- ACTIVE exige verified_at; PENDING não recebe credencial pelo DAO. Não há operação de comprovação/ativação. Elegibilidade cruzada não é imposta por trigger: SQL privilegiado pode contornar esse guard e permanece fora do contrato do runtime.
- Unicidade, FK, estado, tempos e formato/custo canônico do hash são verificados no PostgreSQL. V1 e os seis testes originais foram preservados.
- Nenhuma tabela tenant, store, membership, role, token ou sessão foi criada. Não há endpoint de identidade, frontend, recuperação ou envio.

## Ambiente e versões efetivas

Windows, AMD Ryzen 7 5700U (8 cores/16 processadores lógicos), 16.486.035.456 bytes de RAM física; JDK Amazon Corretto 21.0.9.10.1, Java 21.0.9. Maven Wrapper 3.10.0; Spring Boot 4.1.1; Spring Security Crypto 7.1.1; PostgreSQL 18.6/Testcontainers 2.0.5, Docker Engine 29.1.3. Dependências existentes, sem alteração de POM, manifest ou lockfile.

A identificação de hardware foi consultada somente para contextualizar medições; não foram capturados serial, dados de usuários ou credenciais. Acesso inicial ao pipe Docker dentro do sandbox foi negado; execução autorizada com acesso ao Docker confirmou disponibilidade e todos os testes integrados executaram.

## Testes e verificações executados

Primeiro ciclo: Spotless apply + Maven verify, **30 testes aprovados**, antes de acrescentar duas regressões sobre IDs de hash não canônicos/custos. Revisão encontrou construtor PBKDF2 depreciado; foi substituído pelo construtor com enum SHA-256, preservando salt/iterações/saída, e a versão final foi recompilada/testada.

Ciclo final em 2026-10-15, concluído às 19:04:50 BRT: **BUILD SUCCESS; 32 testes, zero falhas, zero erros, zero ignorados.** Spotless check, compilação, empacotamento, Surefire e Failsafe passaram. Relatórios locais ignorados: backend/target/surefire-reports e backend/target/failsafe-reports.

| Suíte | Testes | Resultado |
| --- | --- | --- |
| BootstrapSecurityTest | 3 | Aprovados, preservados. |
| EmailAddressTest | 5 | Aprovados: comparação, limites, IDNA/ACE, caixa, pontos/tags e entradas inválidas. |
| PasswordPolicyTest | 4 | Aprovados: NFC, pontos Unicode, espaços, surrogates inválidos, limites brutos e blocklist. |
| IdentityPasswordEncoderTest | 5 | Aprovados: JCE independente confirma configuração, salts, verificação, evolução e falha fechada. |
| PasswordHashTest | 1 | Aprovado: formato canônico e faixa de custos. |
| BootstrapIT | 3 | Aprovados: V1 preservada, health/readiness/liveness e fronteira HTTP original. |
| IdentityPersistenceIT | 11 | Aprovados: V1/V2, persistência, FK/constraints, concorrência, rollback e rehash CAS. |

São 26 testes novos e seis anteriores. Testcontainers executou dois bancos descartáveis, sem usar ou limpar volume Compose. Unicidade e CAS foram testados com conexões/transações independentes e barreiras de sincronização, não somente chamadas sequenciais.

O teste de falha Flyway cria deliberadamente DDL seguido de divisão por zero em schema descartável e comprova rollback; o ERROR esperado no log não é migração real defeituosa. O teste original pausa temporariamente seu próprio container para verificar readiness, depois recupera e encerra o container. Nenhum dado de ambiente existente foi apagado.

### Comando reproduzível Windows

Pré-requisitos: JDK 21 selecionado em JAVA_HOME/Path e Docker disponível. A partir de backend, com cache local opcional e ignorado:

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) 'target/maven-user'
$taskRepository = Join-Path (Get-Location) 'target/maven-repository'
.\mvnw.cmd -B -ntp "-Dmaven.repo.local=$taskRepository" `
  com.diffplug.spotless:spotless-maven-plugin:3.10.4:apply verify `
  org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath `
  '-DincludeScope=test' '-Dmdep.outputFile=target/identity-benchmark-classpath.txt'
```

Na validação o coordenador salvou saída em target/identity-stage1-final-verify.log, sem versionar logs. Metadados de repositórios opcionais Flyway retornaram 401 como warning; os artefatos necessários foram resolvidos e o build terminou com sucesso, sem fornecer credenciais para esses feeds. Avisos existentes de Mockito/agent e API depreciada no teste BootstrapIT continuam fora do código de identidade; não foram ocultados.

Não foram repetidos npm/build/frontend nem auditoria SEC-003: nenhum arquivo frontend foi modificado. Isso não representa aprovação ou resolução de dependências frontend.

Conferência final de escopo: 26 arquivos candidatos, 42 links relativos válidos e nenhuma referência a caminho local de máquina. `git diff --check` passou; índice Git vazio e HEAD preservado. Gitleaks 8.30.1 examinou o snapshot dos 26 arquivos com resultado **no leaks found**; comparação adicional com os valores sensíveis locais também não encontrou ocorrência. Cache, logs e relatórios operacionais continuam ignorados. Security e QA conferiram independentemente o JSON final e seu SHA-256, sem novos achados bloqueantes.

## Benchmark reproduzível

Harness: backend/src/test/java/com/smartcommerce/benchmark/IdentityHashBenchmark.java. Main opt-in fora da descoberta JUnit, sem iniciar Spring, HTTP ou banco. Dependências de teste já existentes; plugin Maven apenas gera classpath, sem alterar POM.

Metodologia: PBKDF2-HMAC-SHA256 candidato de 600.000 iterações, salt16 e derivação256; três entradas sintéticas (ASCII de 15 pontos, texto Unicode decomposto e 128 símbolos suplementares). Para cada entrada, encode, matches positivo e matches negativo, concorrência 1/2/4/8; 20 aquecimentos e 200 amostras por série. Total: 36 séries, 7.200 amostras medidas, mais aquecimento e hashes sintéticos de referência.

Percentis por nearest rank, latência de serviço por operação excluindo espera na fila do executor; throughput inclui escalonamento. Negativa do caso máximo substitui um ponto em vez de exceder 128, garantindo que o KDF seja exercitado. Não há login inexistente/HTTP; custo negativo de KDF não demonstra resistência a enumeração de contas.

Recursos: tempo de CPU do processo e percentual relativo a um core/todos os processadores, pico de heap amostrado a cada 50ms, memória virtual comprometida e GC. Heap não é RSS; memória virtual não é consumo físico. Inclui overhead leve de amostragem. Não houve CPU affinity, controle térmico/potência ou isolamento completo do desktop; isso limita extrapolação. Relatório nunca contém senha ou hash.

```powershell
$taskClasspath = 'target/test-classes;target/classes;' + `
  (Get-Content target/identity-benchmark-classpath.txt -Raw).Trim()
java '-Xms128m' '-Xmx512m' '-Dfile.encoding=UTF-8' '-cp' $taskClasspath `
  com.smartcommerce.benchmark.IdentityHashBenchmark target/identity-stage1-benchmark.json
```

Equivalência Linux não executada nesta rodada: usar bash ./mvnw e separador `:` no classpath. O harness Java é portável; o benchmark não foi adicionado ao workflow nem transformado em gate de latência em runner compartilhado. Opções JVM com ponto devem ser delimitadas em PowerShell; a primeira tentativa sem delimitação de -Dfile.encoding falhou no lançamento antes de qualquer medição e foi corrigida.

### Resultado e recomendação

Medição concluída com saída 0: **36 séries e 7.200 amostras**, além de 720 operações de aquecimento e três hashes sintéticos de referência. Executada de 19:05:13 a 19:23:36 BRT (22:05:13–22:23:36 UTC), em 15/10/2026; aproximadamente 18min23s. Provider SunJCE; heap JVM configurado em 128–512MiB.

Dados completos sem senhas/hashes: [SC-002-STAGE-01-BENCHMARK.json](evidence/SC-002-STAGE-01-BENCHMARK.json). SHA-256 do JSON: `9c2c0a2bff7d543167988cbc3b1c8f52ff64bb281565a5b37e567a87495689de`.

A tabela mostra o **maior p50 e maior p95 entre as nove séries** de cada concorrência; não é percentil agregado de todas as amostras. CPU é a maior média por série normalizada pelos 16 processadores lógicos, não pico instantâneo nem medida de capacidade física livre.

| Concorrência | Maior p50 (ms) | Maior p95 (ms) | Throughput observado (op/s) | Maior média CPU total | Pico heap amostrado (MiB) |
| --- | --- | --- | --- | --- | --- |
| 1 | 546,17 | 604,95 | 1,90–5,37 | 6,29% | 84,32 |
| 2 | 475,71 | 552,31 | 4,12–10,21 | 12,37% | 85,02 |
| 4 | 505,00 | 617,09 | 7,63–18,80 | 24,65% | 84,85 |
| 8 | 576,49 | 694,80 | 13,34–32,59 | 48,47% | 84,47 |

Maior p95: 694,80ms em matches positivo de 128 pontos suplementares, concorrência 8; nessa série, p50 576,49ms, p99 763,86ms e throughput 13,34op/s. Pico global de heap amostrado: 85,02MiB; maior memória virtual comprometida: 235,72MiB. CPU atingiu média de até 775,47% em escala de um core (48,47% dos 16 lógicos). Threads lógicas não equivalem a cores físicos independentes nem indicam reserva proporcional de capacidade.

**Recomendação:** manter o candidato medido — 600.000 iterações, salt16 e saída256, ID `pbkdf2-sha256-nfc-i600000-s16-l256-v1` — como baseline da Etapa 1 para revisão humana. Todas as séries ficaram abaixo da referência proposta de p95=1s; nenhum parâmetro foi reduzido. Configuração efetiva foi confirmada separadamente pelo teste JCE independente.

A ordem dos casos foi fixa e o desktop não foi isolado: variação entre séries não pode ser atribuída exclusivamente ao comprimento da senha. O ensaio não demonstra SLA, throughput HTTP, resistência a enumeração ou capacidade de produção. Antes dos endpoints, D5 precisa dimensionar admissão/concorrência no ambiente-alvo e reservar CPU para o restante da aplicação; não adotar oito hashes paralelos automaticamente nem baixar iterações para compensar recursos. Não foi aplicada configuração de sessão, rate limit ou etapa futura.

## Revisões e achados de segurança

- Architect: revisou limites do módulo, esquema, locks/CAS e ausência de funcionalidades posteriores; sem bloqueio arquitetural.
- Backend: implementou somente primitivas/recursos de produção, confirmou API Spring local e corrigiu construtor depreciado; não instalou dependências.
- Security: analisou Unicode/IDNA, registry, diagnóstico de credenciais, elegibilidade e benchmark. Solicitou rejeitar IDs com zeros iniciais; correção em modelo/V2 e regressões Java/PostgreSQL executadas com sucesso. Sem achado crítico bloqueante no snapshot revisado; limites abaixo permanecem.
- QA: criou cinco classes de teste, incluindo duas regressões finais; execução foi centralizada pelo coordenador para evitar concorrência de builds. Revisão dos resultados não equivale a teste funcional de ativação/login.

Riscos/pontos de acompanhamento: blocklist de 21 entradas tem cobertura limitada e exige revisão de amplitude/procedência antes do cadastro público; IDNA2003 não deve ser apresentado como IDNA2008/UTS46 e requer análise antes de mudar o perfil; invariantes cruzadas dependem do DAO e podem ser contornadas por SQL privilegiado; versões novas exigem registry compatível em todos os leitores antes de persistir; benchmark local não comprova capacidade de produção.

**SEC-003 permanece aberta, sem correção, nova auditoria ou aceite de risco.** Testes backend verdes não aceitam o risco da cadeia de lint frontend. Referência: [SC-001-REVIEW.md](../security/SC-001-REVIEW.md).

## Critérios de aceite

| Critério | Estado |
| --- | --- |
| Persistência mínima global e credencial separada, sem domínio empresarial | Atendido. |
| V2 aditiva, V1 preservada e PostgreSQL real | Atendido por testes. |
| D2 comparações/validação/unicidade concorrente documentadas | Atendido pelo perfil documentado e testes. |
| Política NFC/Unicode/espaços/blocklist e encoder versionado | Atendido; cobertura da blocklist limitada explicitamente. |
| Persistência de hash real, evolução/CAS e não downgrade | Atendido por testes. |
| Benchmark completo e recomendação fundamentada | Atendido: 36 séries/7.200 amostras; manter candidato para revisão, sem SLA de produção. |
| Ausência de endpoints, ativação, sessão, token, e-mail, UI, tenant/roles | Atendido por diff/revisão. |
| Revisões independentes e documentação | Realizadas; números e artefatos conferidos e limites registrados. |
| CI remota desta implementação | Não executada; commit/push não autorizados. |
| Publicação da Etapa 1 | Autorizada após revisão local; integrar por PR com backend/admin aprovados. |
| Próxima entrega funcional | Aguarda aprovação de D4–D6; não iniciada. |

## Inventário

Produção: oito classes em backend/src/main/java/com/smartcommerce/identity — EmailAddress, PasswordPolicy, IdentityPasswordEncoder, PasswordHash, IdentityStatus, IdentityUser, LocalCredential e JdbcIdentityRepository; V2__identity_foundation.sql; password-blocklist-v1.txt e BLOCKLIST-PROVENANCE.md.

Testes: EmailAddressTest, PasswordPolicyTest, IdentityPasswordEncoderTest, PasswordHashTest e IdentityPersistenceIT no pacote de identidade; IdentityHashBenchmark no pacote benchmark. BootstrapIT/BootstrapSecurityTest originais não foram alterados.

Documentação: ADR-0005, SC-002-PLAN, SC-002-TEST-PLAN e este registro; novo IDENTITY_FOUNDATION e JSON do benchmark; atualizações pontuais de README, ARCHITECTURE e DATA_MODEL. Os três documentos de planejamento já estavam locais e não versionados antes da implementação; foram preservados e atualizados com a autorização D1–D3. POM, wrapper, V1, configuração/fronteira HTTP, frontend e CI permanecem iguais ao baseline.

### Lista exata dos 26 arquivos candidatos

- `README.md`
- `backend/src/main/java/com/smartcommerce/identity/EmailAddress.java`
- `backend/src/main/java/com/smartcommerce/identity/IdentityPasswordEncoder.java`
- `backend/src/main/java/com/smartcommerce/identity/IdentityStatus.java`
- `backend/src/main/java/com/smartcommerce/identity/IdentityUser.java`
- `backend/src/main/java/com/smartcommerce/identity/JdbcIdentityRepository.java`
- `backend/src/main/java/com/smartcommerce/identity/LocalCredential.java`
- `backend/src/main/java/com/smartcommerce/identity/PasswordHash.java`
- `backend/src/main/java/com/smartcommerce/identity/PasswordPolicy.java`
- `backend/src/main/resources/db/migration/V2__identity_foundation.sql`
- `backend/src/main/resources/identity/BLOCKLIST-PROVENANCE.md`
- `backend/src/main/resources/identity/password-blocklist-v1.txt`
- `backend/src/test/java/com/smartcommerce/benchmark/IdentityHashBenchmark.java`
- `backend/src/test/java/com/smartcommerce/identity/EmailAddressTest.java`
- `backend/src/test/java/com/smartcommerce/identity/IdentityPasswordEncoderTest.java`
- `backend/src/test/java/com/smartcommerce/identity/IdentityPersistenceIT.java`
- `backend/src/test/java/com/smartcommerce/identity/PasswordHashTest.java`
- `backend/src/test/java/com/smartcommerce/identity/PasswordPolicyTest.java`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/DATA_MODEL.md`
- `docs/architecture/IDENTITY_FOUNDATION.md`
- `docs/architecture/adr/0005-identity-authentication.md`
- `docs/product/SC-002-PLAN.md`
- `docs/quality/SC-002-STAGE-01-VALIDATION.md`
- `docs/quality/SC-002-TEST-PLAN.md`
- `docs/quality/evidence/SC-002-STAGE-01-BENCHMARK.json`
