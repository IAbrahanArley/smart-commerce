# SC-002 Etapa 1 — Contrato da fundação de identidade

Data: 2026-10-15. Implementação local autorizada exclusivamente para persistência, política de e-mail/senha, encoder, testes e benchmark. Base: [ADR-0005](adr/0005-identity-authentication.md); resultados: [validação da Etapa 1](../quality/SC-002-STAGE-01-VALIDATION.md).

## Limites

Um pacote `com.smartcommerce.identity` no monólito, usando JDBC e dependências existentes. Não há controllers, endpoints, cadastro público, ativação, token, sessão, envio de e-mail, recuperação, frontend, tenant, store, membership ou role. Nenhuma classe de identidade é registrada como mecanismo de autenticação HTTP: o bloqueio do bootstrap permanece intacto. Nenhum serviço externo ou dependência de aplicação foi adicionado.

## Persistência implementada

V2 `identity_foundation`, aditiva, cria somente:

| Tabela / campo | Contrato |
| --- | --- |
| identity_user.id | UUID global estável, PK; não é identificador de empresa. |
| email_original | VARCHAR(1024), entrada validada preservada; não deve ser usada como prova de posse. |
| email_key | VARCHAR(254), chave normalizada ASCII, UNIQUE com collation C, sensível à caixa local. |
| status | PENDING_EMAIL, ACTIVE ou DISABLED; default PENDING_EMAIL. Nenhuma operação de ativação foi implementada. |
| auth_version | BIGINT >=0; preparado para proteção de evolução concorrente. Não implementa sessão/revogação agora. |
| created_at / updated_at / verified_at | TIMESTAMPTZ; updated >= created; ACTIVE exige verified_at; PENDING exige verified_at nulo. |
| identity_local_credential.user_id | PK/FK para usuário, uma credencial por identidade; exclusão referenciada restrita. |
| password_hash | VARCHAR(200), somente formato PBKDF2 versionado canônico, custo 600.000–2.000.000 e payload hexadecimal de 48 bytes. |
| created_at / updated_at | TIMESTAMPTZ, atualização não anterior à criação. |

V1 `bootstrap` e seu checksum não são alterados; app_metadata continua técnica. A V2 não recebe senha em claro nem valores reais. CHECK de formato não comprova que um hash foi realmente derivado: persistir apenas resultados do encoder selecionado. A faixa de custos do armazenamento não registra automaticamente todos esses custos no encoder.

O repositório tem primitivas internas de criação de identidade pendente, consulta, inserção de credencial elegível e rehash CAS. Inserção exige ACTIVE + verified_at sob lock; pendente não recebe credencial pelo repositório. Não há método de comprovação/ativação. Fixtures ACTIVE são criadas por SQL somente nos testes descartáveis.

Elegibilidade cruzada entre usuário/credencial é garantida pelo DAO, não por trigger: SQL privilegiado pode contorná-la. FK/status/tempos/formato continuam protegidos no banco. Esse limite deve ser preservado nas futuras transações de ativação; não há garantia de posse de e-mail ou autorização empresarial apenas por existir um registro.

Rehash usa hash anterior + auth_version + estado elegível e lock do usuário, impedindo sobrescrever alteração concorrente; rejeita redução do custo. Rehash técnico não é troca de senha e não incrementa auth_version. Não se implementou reset/revogação ou uma transição de status funcional. Escritas sanitizam exceções PostgreSQL para evitar que detalhes de failing row revelem e-mail/hash; não há logging de credenciais. Value objects/modelos têm toString redigido.

## E-mail: validação, comparação e unicidade

- Um endereço sem display-name, comentários, aspas ou formato de domínio literal; local-part ASCII dot-atom. Um único `@`, local de até 64 caracteres; rejeitar pontos iniciais/finais/consecutivos e caracteres fora desse perfil.
- Não fazer trim; whitespace externo, CR/LF, controles e UTF-16 malformado são rejeitados. Entrada bruta limitada a 1024 unidades UTF-16; chave final ASCII até 254 caracteres, domínio até 253 e labels conforme STD3.
- Preservar caixa, pontos e `+tags` locais. Domínio convertido pelo `java.net.IDN` do JDK 21, USE_STD3_ASCII_RULES e Locale.ROOT lowercase; validar ACE/punycode por roundtrip. Rejeitar label vazio, underscore, hífen inválido, trailing root dot e ACE inválido.
- Este é o perfil **IDNA2003 do JDK**, não IDNA2008/UTS46. Por exemplo, `faß.example` mapeia para `fass.example`. Alterar esse perfil/JDK requer analisar colisões e compatibilidade antes de novas identidades reais; não instalar biblioteca de IDNA automaticamente.

| Entrada | Chave |
| --- | --- |
| User.name+Tag@BÜCHER.Example | User.name+Tag@xn--bcher-kva.example |
| User@EXAMPLE.com | User@example.com |
| user@example.com | user@example.com, distinta de User@example.com |
| User+tag@example.com | Preserva +tag; distinta de User@example.com |

Validação não consulta DNS/MX nem prova entregabilidade/controle da caixa postal. Dois identificadores distintos podem chegar à mesma caixa; local-parts com aspas ou internacionalizados não são suportados neste perfil inicial. A collation C garante a comparação escolhida independentemente do locale padrão do banco. Unicidade é do identificador, não da pessoa ou caixa física.

## Senha e blocklist

Antes de NFC, rejeitar null, UTF-16 malformado e entrada maior que 1024 unidades UTF-16, limitando custo de normalização/KDF. Depois aplicar NFC e contar pontos Unicode: 15–128, sem trim ou truncamento. Espaços em frases são preservados; entrada composta somente de whitespace é bloqueada como segredo trivial. Não há regras de composição ou troca periódica.

`Cafe` + acento combinante e `Café` produzem a mesma representação NFC; espaços iniciais/finais continuam fazendo parte da senha. 128 símbolos suplementares equivalem a 256 unidades UTF-16 e são aceitos; 129 pontos não são. Esse contrato deverá orientar o frontend futuro, que não existe nesta etapa.

Blocklist `sc-local-v1`, 21 entradas autorais, match exato após NFC e lowercase Locale.ROOT, sem trim. Não há corpus de terceiros nem alteração da licença do projeto. Proveniência e limites estão em `backend/src/main/resources/identity/BLOCKLIST-PROVENANCE.md`. Lista pequena não garante cobertura de senhas comprometidas; revisar amplitude/procedência antes do cadastro público. A consulta externa de senhas não foi implementada.

Estabelecimento aplica comprimento e blocklist. Verificação normaliza/rejeita entradas inválidas e mantém o limite superior, mas não reaplica a blocklist ou um mínimo que poderia bloquear credencial histórica. Essa separação permite ampliar bloqueios para novas senhas sem invalidar silenciosamente as existentes.

## Encoder e evolução

`IdentityPasswordEncoder` implementa PasswordEncoder com DelegatingPasswordEncoder e registry explícito; não integra filtro HTTP nem registra provedor/usuário de login. Candidato medido: PBKDF2WithHmacSHA256, 600.000 iterações, salt aleatório 16 bytes, chave derivada 256 bits. Construtor Spring com enum explícito, sem construtor depreciado ou dependência nova.

Formato: `{pbkdf2-sha256-nfc-i600000-s16-l256-v1}` seguido de 96 caracteres hexadecimais (salt16 + derivação32). ID identifica também a normalização. Unknown ID, noop, plaintext, hash truncado, payload inválido e custo não registrado falham fechado, sem fallback. IDs com zeros iniciais são rejeitados pelo modelo e banco.

Configuração explícita permite candidato de custo superior até 2.000.000 para testes/evolução, preservando leitura de 600.000; o default não passa a aceitar qualquer custo declarado pelo dado. Antes de gravar nova versão, todos os leitores precisam conhecê-la. Voltar a uma configuração que desconhece o novo ID falha fechado; rollout não pode assumir rollback transparente. Não reduzir custo automaticamente, nem rehash em background nesta etapa.

Testes usam JCE independente para verificar SHA-256/600.000/salt16/saída256 efetivos, além de salt distinto, NFC, matches e CAS real em PostgreSQL. Isso verifica configuração; não alega certificação FIPS/NIST.

## Execução e evidências

`verify` pelo Maven Wrapper com JDK 21 executa formatação, unitários e PostgreSQL/Testcontainers. A suíte original de seis testes permanece intacta; bancos descartáveis são separados do Compose. Benchmark manual é classe de teste com main, fora dos padrões de descoberta de JUnit; não é gate de latência em runner compartilhado. Comandos reproduzíveis e medições estão no relatório da Etapa 1.

SEC-003 segue aberta, sem nova auditoria ou aceite. As Etapas 2–6, alterações de CI, commit e push não são autorizados por este contrato.

## Referências técnicas

- [Oracle IDN — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/IDN.html): perfil IDNA do JDK e STD3.
- [Spring PBKDF2](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.html): configuração do encoder usado.
- [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): referência de segurança, sem alegação de certificação ou aceite automático.
