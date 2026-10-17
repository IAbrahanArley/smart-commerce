# Smart Commerce

SaaS multi-tenant de lojas virtuais independentes com gestão financeira e operacional inteligente.

## Estado e escopo

Validações locais: [autenticação](docs/quality/SC-002-AUTH-VALIDATION.md), [empresa/loja](docs/quality/SC-003-VALIDATION.md) e [catálogo básico](docs/quality/CATALOG-VALIDATION.md). A suíte atual possui 62 testes backend aprovados após a refatoração de arquitetura. Publicação e CI remota das alterações funcionais posteriores à fundação permanecem pendentes.


Backend Spring Boot, admin Next.js, PostgreSQL/Flyway e CI preparados. A SC-002 acrescenta o fluxo local de solicitação por e-mail, confirmação com definição de senha, login, consulta de identidade e logout. A SC-003 acrescenta criação e consulta da empresa/loja do proprietário, com isolamento por vínculo. O catálogo básico acrescenta cadastro, listagem e edição de produtos por loja. Vitrine pública, estoque, pagamentos, IA e recuperação de senha não integram esta entrega.

Decisões: [ADR-0004](docs/architecture/adr/0004-project-bootstrap.md). Resultados e limitações: [validação SC-001](docs/quality/SC-001-VALIDATION.md). O isolamento implementado para empresa/loja e catálogo é verificado por testes A/B; observabilidade ampliada da SC-004 continua pendente.

SC-001 publicada no repositório privado confirmado pelo usuário. A [CI remota](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235) aprovou backend e admin no commit `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`: seis testes backend sem falhas/erros/ignorados, lint, tipos, build e smoke HTTP do admin aprovados. A primeira execução falhou no seletor Corretto; foi corrigido para Java `21`, sem alterar dependências da aplicação. SEC-003 permanece aberta, sem exceção aprovada: a última auditoria registrou cinco alertas altos na cadeia de desenvolvimento e zero com `--omit=dev`; ESLint 9 está fora de suporte. CI verde não aceita esse risco; a autorização da SC-002 foi concedida separadamente pelo responsável. Detalhes em [validação](docs/quality/SC-001-VALIDATION.md), [segurança](docs/security/SC-001-REVIEW.md) e [publicação](docs/quality/SC-001-PUBLICATION.md).

## Estrutura

```text
backend/          Spring Boot, Maven Wrapper, Flyway e testes
admin-web/        Next.js App Router, TypeScript e shell administrativo
storefront-web/   reservado; sem aplicação executável
infrastructure/   Compose e inicialização técnica do PostgreSQL
scripts/          execução local do backend em PowerShell
docs/             produto, arquitetura, ADRs, papéis e evidências
.github/workflows/ci.yml
```

## Ferramentas

- JDK 21. A validação local utilizou Corretto 21.0.9; a CI aprovou OpenJDK 21.0.12.1 / Corretto 21.0.12.12.1. O adaptador Corretto da action aceita somente a major `21`, selecionando seu patch disponível. Selecione o JDK no terminal; o Java global pode continuar em outra versão.
- Maven 3.10.0 obtido pelo Maven Wrapper 3.3.4, sem instalação global.
- Spring Boot 4.1.1; dependências gerenciadas pelo parent/BOM no `backend/pom.xml`.
- Node.js 24.12.0; npm local 11.21.0 e npm remoto aprovado 11.6.2 (ambos atendem ao engine 11 do projeto). Versões exatas das bibliotecas em `admin-web/package.json` e `package-lock.json`.
- Docker Desktop/Engine Linux e Compose v2. O CLI instalado sem engine acessível não basta.
- PostgreSQL 18.6 Bookworm, com digest fixado no Compose e nos testes.

Os primeiros downloads de Maven, npm e imagens exigem rede. Nenhum serviço pago ou credencial de produção é necessário.

## Executar localmente no Windows (PowerShell)

Comece na raiz. Substitua o caminho ilustrativo de JAVA_HOME pela instalação do seu JDK 21.

```powershell
$env:JAVA_HOME = 'C:\caminho\para\seu\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
node --version
npm --version
docker info
```

Inicie Docker Desktop e aguarde o engine Linux. Crie o arquivo local somente se ele não existir:

```powershell
if (-not (Test-Path infrastructure/.env)) {
    Copy-Item infrastructure/.env.example infrastructure/.env
}
```

Edite `infrastructure/.env` e preencha `POSTGRES_ADMIN_PASSWORD` e `DB_PASSWORD` com senhas locais distintas. O exemplo deixa esses campos vazios intencionalmente. Use valores simples, sem aspas, espaços, `$` ou `#`, compatíveis com o helper. Nunca use credenciais de outro ambiente nem versione `.env`. Nesta execução da SC-001, um arquivo local ignorado pode já estar preparado com senhas aleatórias.

```powershell
docker compose --env-file infrastructure/.env -f infrastructure/compose.yaml config --quiet
docker compose --env-file infrastructure/.env -f infrastructure/compose.yaml up -d --wait postgres
docker compose --env-file infrastructure/.env -f infrastructure/compose.yaml ps
```

Em terminal com JDK 21 selecionado:

```powershell
./scripts/start-backend.ps1
```

O helper lê o arquivo sem avaliar comandos, fornece `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` ao Maven e restaura as variáveis ao encerrar. Não fornece a senha administrativa ao Java. O `.env` do Compose não é carregado automaticamente pelo Spring Boot.

Em outro terminal:

```powershell
Set-Location admin-web
npm ci --no-fund --no-audit
npm run dev
```

- Admin: <http://127.0.0.1:3000>.
- Health: <http://127.0.0.1:8080/actuator/health>.
- Prontidão: <http://127.0.0.1:8080/actuator/health/readiness>.
- Vivacidade: <http://127.0.0.1:8080/actuator/health/liveness>.

Abra o admin para criar a conta. O helper inicia o perfil `dev`; as mensagens são gravadas em caixa local protegida fora do repositório/OneDrive, sem envio externo. Copie o link da mensagem mais recente, defina a senha na confirmação e depois faça login. A ativação não autentica automaticamente. Na conta autenticada, o lojista pode criar sua empresa/loja e visualizar somente suas lojas. O vínculo de proprietário é criado pelo servidor; a identidade sozinha não concede acesso a empresas de outros usuários.

Caixa DEV padrão no Windows: `%LOCALAPPDATA%/SmartCommerce/mail`; no Linux: `~/.local/share/SmartCommerce/mail`. Abra o arquivo `.eml` mais recente como texto e copie o link completo, incluindo `#token=...`. O link é sensível: não envie ao chat nem versione mensagens. Tokens duram 30 minutos; o reenvio tem cooldown de 60 segundos.

Rotas: `/cadastro`, `/confirmar-email`, `/entrar`, `/conta` e `/lojas/{id}`. API de mesma origem via Next.js: `/api/auth/csrf`, `/registration`, `/activation`, `/login`, `/me` e `/logout`, todos sob `/api/auth`. Mutações exigem CSRF; senhas e sessões não usam localStorage.

Para encerrar, Ctrl+C nos terminais e parada do banco preservando volume:

```powershell
docker compose --env-file infrastructure/.env -f infrastructure/compose.yaml stop postgres
```

## Outros shells

Para desenvolvimento, defina também `SPRING_PROFILES_ACTIVE=dev`. O backend aceita `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` pelo ambiente. Exporte-as localmente sem colocar senhas em comandos versionados ou logs. `SERVER_ADDRESS` e `SERVER_PORT` são opcionais, assumindo `127.0.0.1` e `8080`.

Com JDK 21 e essas variáveis presentes, em `backend/`:

```sh
bash ./mvnw spring-boot:run
```

Comandos npm/Compose são equivalentes nos demais sistemas. O helper documentado é para Windows; outros shells devem fornecer o ambiente diretamente.

## Banco e migrações

Compose publica PostgreSQL somente em loopback. Na primeira inicialização de um volume vazio, cria uma conta de aplicação sem privilégios de superusuário, criação de bancos ou roles. A conta administrativa é separada. O usuário local é proprietário do banco para permitir Flyway; separar usuários de migração e runtime fica para a configuração futura de produção.

V1 cria `app_metadata` e registra `bootstrap=SC-001`. Essa tabela global guarda metadados técnicos. A SC-002 Etapa 1 adiciona V2 com `identity_user` e `identity_local_credential`; V3 acrescenta provas de e-mail e contadores de abuso, V4 cria o schema oficial do Spring Session JDBC V5 adiciona empresa, vínculo e loja; V6 cria o catálogo básico com integridade tenant/loja e precisão monetária. Flyway controla as versões; V1/V2 não foram reescritas e a inicialização automática de sessão fica desabilitada. Contrato: [fundação de identidade](docs/architecture/IDENTITY_FOUNDATION.md).

Alterar senhas no `.env` não altera usuários de um volume já inicializado. Não apague volumes para corrigir isso; trate a mudança de credenciais deliberadamente. Remoção de dados exige aprovação humana e não integra os comandos normais do projeto.

## Verificações

Backend, em `backend/`, com JDK 21:

```powershell
.\mvnw.cmd -B -ntp verify
```

No Linux: `bash ./mvnw -B -ntp verify`. O ciclo verifica formatação, executa testes de segurança e integrações PostgreSQL via Testcontainers. Integrações exigem Docker e não são ignoradas quando ele está indisponível. O banco de teste é descartável e separado do volume Compose.

Admin, em `admin-web/`:

```powershell
npm ci --no-fund --no-audit
npm run lint
npm run typecheck
npm run build
npm run test:smoke
```

Smoke inicia o build de produção em `127.0.0.1:3100`, verifica página inicial, idioma, conteúdo e 404, encerrando o processo ao final. Requer build e porta livre. É teste HTTP; não certifica renderização visual em navegador nem acessibilidade completa.

## Integração contínua

GitHub Actions executa backend e admin em push/PR. Actions são fixadas por commit, com permissões de leitura e sem deploy ou segredos de produção. Backend usa Testcontainers no runner, sem serviço PostgreSQL duplicado. Admin executa lint explicitamente, tipos, build e smoke.

Origin confirmado: `https://github.com/IAbrahanArley/smart-commerce.git`, repositório privado, branch `main`. A etapa documental UI/UX foi publicada no SHA `c1014ed38fc13d56ed00c64acb34674baf60c17a`, com CI backend/admin aprovada. Main possui proteção e exige os checks backend/admin. A Etapa 1 da SC-002 foi publicada no SHA `adbc1b549e863745fd5f2136a4dfcb4e34d49c18`, com [backend/admin aprovados](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38818169657). Essa execução não comprova as alterações funcionais posteriores, ainda locais. SEC-003 continua aberta, sem aceite de risco.

## Limites da entrega

D1–D6 aprovadas no [ADR-0005](docs/architecture/adr/0005-identity-authentication.md). Fundação e benchmark: [contrato](docs/architecture/IDENTITY_FOUNDATION.md) e [validação da Etapa 1](docs/quality/SC-002-STAGE-01-VALIDATION.md). Mantido PBKDF2-HMAC-SHA256 com 600.000 iterações, salt16 e saída256; benchmark não foi repetido.

A SC-003 cria o vínculo OWNER junto com a empresa/loja; listagem e detalhe exigem esse vínculo em consultas escopadas pela identidade autenticada. O identificador enviado pelo cliente nunca concede acesso sozinho. Produção exige transporte de e-mail real, HTTPS e dimensionamento específico; o adaptador DEV não deve operar em produção. SEC-003 permanece aberta, sem alteração ou aceite de risco.

## Demonstração da SC-003

1. Criar conta e confirmar o e-mail pelo fluxo local descrito acima; depois entrar.
2. Em Minha conta, informar nome da empresa, nome da loja e identificador único (ex.: minha-loja).
3. Criar a loja e abrir seus detalhes. O estado “Em configuração” não representa vitrine publicada.
4. Entrar com outro lojista: somente os vínculos dele aparecem. Um identificador de loja de outro lojista não permite consulta.

Implementação: [ADR-0006](docs/architecture/adr/0006-tenant-store-onboarding.md). Testes de isolamento usam PostgreSQL descartável e não alteram o volume local. SEC-003 permanece aberta e inalterada.

## Demonstrar o catálogo básico

1. Entre, abra sua loja e selecione Produtos.
2. Informe nome, SKU e preço maior que zero (ex.: 49,90). Custo pode ficar em branco ou ser zero.
3. Cadastre e confira os valores. Abra Editar produto, altere e salve; recarregue para conferir a persistência.
4. SKU repetido na mesma loja mostra erro. Edição desatualizada exige carregar a versão atual, sem sobrescrever dados.

Rotas: /lojas/{tenantId}/produtos e /lojas/{tenantId}/produtos/{productId}. Catálogo interno, sem publicação ou estoque. [Decisões](docs/architecture/adr/0007-basic-catalog.md) e [validação local](docs/quality/CATALOG-VALIDATION.md). SEC-003 permanece inalterada.

## Arquitetura e padrões de desenvolvimento

Backend mantém o monólito modular com camadas explícitas dentro de identity, tenant e catalog: controllers, services, repositories, entities/domain e dto/request/response. Tenant e catálogo utilizam JPA/Spring Data; identidade, sessões e locks PostgreSQL especializados mantêm JDBC em repositórios. Nenhum SQL fica nos serviços. Flyway controla o schema; Hibernate valida e Open Session in View fica desabilitado.

Padrões adaptados do project-rh: [ADR-0008](docs/architecture/adr/0008-layered-persistence-refactoring.md), [convenções](docs/architecture/CODING_STANDARDS.md) e [validação da refatoração](docs/quality/ARCHITECTURE-REFACTOR-VALIDATION.md). No perfil dev, após login no admin usando 127.0.0.1, a documentação autenticada está em http://127.0.0.1:8080/swagger-ui/index.html e http://127.0.0.1:8080/v3/api-docs. Swagger é somente consulta, sem executar operações. Fora de dev, a documentação permanece desabilitada por padrão.
