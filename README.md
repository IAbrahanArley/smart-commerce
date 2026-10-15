# Smart Commerce

SaaS multi-tenant de lojas virtuais independentes com gestão financeira e operacional inteligente.

## Estado e escopo

SC-001 prepara backend Spring Boot, shell administrativo Next.js, PostgreSQL local, Flyway e CI. Autenticação funcional, usuários, empresas/lojas, catálogo, pagamentos e IA ainda não existem. A vitrine permanece reservada.

Decisões: [ADR-0004](docs/architecture/adr/0004-project-bootstrap.md). Resultados e limitações: [validação SC-001](docs/quality/SC-001-VALIDATION.md). Isolamento entre empresas depende de SC-003/004 e ainda não foi implementado.

SC-001 publicada no repositório privado confirmado pelo usuário. A [CI remota](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235) aprovou backend e admin no commit `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`: seis testes backend sem falhas/erros/ignorados, lint, tipos, build e smoke HTTP do admin aprovados. A primeira execução falhou no seletor Corretto; foi corrigido para Java `21`, sem alterar dependências da aplicação. SEC-003 permanece aberta, sem exceção aprovada: a última auditoria registrou cinco alertas altos na cadeia de desenvolvimento e zero com `--omit=dev`; ESLint 9 está fora de suporte. CI verde não aceita esse risco nem autoriza SC-002. Detalhes em [validação](docs/quality/SC-001-VALIDATION.md), [segurança](docs/security/SC-001-REVIEW.md) e [publicação](docs/quality/SC-001-PUBLICATION.md).

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

Somente esses GETs operacionais são permitidos no backend. As demais requisições são negadas. Essa barreira não implementa login. O admin contém apenas uma página técnica sem dados de negócio.

Para encerrar, Ctrl+C nos terminais e parada do banco preservando volume:

```powershell
docker compose --env-file infrastructure/.env -f infrastructure/compose.yaml stop postgres
```

## Outros shells

O backend aceita `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` pelo ambiente. Exporte-as localmente sem colocar senhas em comandos versionados ou logs. `SERVER_ADDRESS` e `SERVER_PORT` são opcionais, assumindo `127.0.0.1` e `8080`.

Com JDK 21 e essas variáveis presentes, em `backend/`:

```sh
bash ./mvnw spring-boot:run
```

Comandos npm/Compose são equivalentes nos demais sistemas. O helper documentado é para Windows; outros shells devem fornecer o ambiente diretamente.

## Banco e migrações

Compose publica PostgreSQL somente em loopback. Na primeira inicialização de um volume vazio, cria uma conta de aplicação sem privilégios de superusuário, criação de bancos ou roles. A conta administrativa é separada. O usuário local é proprietário do banco para permitir Flyway; separar usuários de migração e runtime fica para a configuração futura de produção.

V1 cria `app_metadata` e registra `bootstrap=SC-001`. Essa tabela global guarda metadados técnicos. A SC-002 Etapa 1 adiciona V2 com `identity_user` e `identity_local_credential`, sem outras entidades, autenticação funcional ou geração de schema por ORM. `flyway_schema_history` controla ambas; V1 não foi reescrita. Contrato: [fundação de identidade](docs/architecture/IDENTITY_FOUNDATION.md).

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

Origin confirmado: `https://github.com/IAbrahanArley/smart-commerce.git`, repositório privado, branch `main`. A etapa documental UI/UX foi publicada no SHA `c1014ed38fc13d56ed00c64acb34674baf60c17a`, com CI backend/admin aprovada. Main possui proteção e exige os checks backend/admin. Essa evidência anterior não comprova a implementação local da SC-002 Etapa 1, que ainda não tem commit/push autorizado. SEC-003 continua aberta, sem aceite de risco.

## Próxima tarefa

Somente a SC-002 Etapa 1 foi autorizada: persistência, política de e-mail/senha, encoder, testes e benchmark. [Contrato](docs/architecture/IDENTITY_FOUNDATION.md) e [evidências de validação](docs/quality/SC-002-STAGE-01-VALIDATION.md) descrevem o estado local. Não existem endpoints de identidade, ativação, sessão, tokens, envio, recuperação ou UI nesta etapa. Aceite, commit/push e Etapas 2–6 exigem nova autorização.

O tenant futuro deverá derivar de identidade autenticada e vínculo autorizado; parâmetros, headers ou slug enviados pelo frontend não bastam. Nenhuma empresa, loja, membership ou role foi criada.
