# Smart Commerce

SaaS multi-tenant de lojas virtuais independentes com gestão financeira e operacional inteligente.

## Estado e escopo

Validações locais: [autenticação](docs/quality/SC-002-AUTH-VALIDATION.md), [empresa/loja](docs/quality/SC-003-VALIDATION.md), [catálogo básico](docs/quality/CATALOG-VALIDATION.md), [estoque](docs/quality/INVENTORY-VALIDATION.md), [vitrine pública](docs/quality/STOREFRONT-VALIDATION.md) [fotos, descrição e variações](docs/quality/CATALOG-MEDIA-VARIANTS-VALIDATION.md), [reservas](docs/quality/SC-005-VALIDATION.md), [entrega](docs/quality/SC-006-VALIDATION.md), [carrinho](docs/quality/SC-007-VALIDATION.md), [integração da Sprint 3](docs/quality/SPRINT-03-INTEGRATION-VALIDATION.md), [pedido no backend](docs/quality/SC-008-VALIDATION.md), [checkout na vitrine](docs/quality/SC-009-VALIDATION.md), [pedidos no admin](docs/quality/SC-010-VALIDATION.md) e [revisão integrada da Sprint 3](docs/quality/SC-011-VALIDATION.md) ([modelo de ameaças](docs/security/SC-011-CHECKOUT-THREAT-MODEL.md)). A suíte atual possui 162 testes backend aprovados (SC-011). Publicação e CI remota das alterações desde `1a04070` permanecem pendentes.


Backend Spring Boot, admin e vitrine Next.js separados, PostgreSQL/Flyway e CI preparados. A SC-002 acrescenta o fluxo local de solicitação por e-mail, confirmação com definição de senha, login, consulta de identidade e logout. A SC-003 acrescenta criação e consulta da empresa/loja do proprietário, com isolamento por vínculo. O catálogo permite cadastrar, listar e editar produtos; o estoque registra entradas e ajustes. A vitrine pública mostra somente lojas e produtos publicados explicitamente, com preços e disponibilidade descontadas as reservas ativas. A loja configura retirada e/ou frete fixo, e o comprador monta um carrinho com cotação calculada pelo backend. Em DEV, o comprador finaliza o pedido até `PENDING_PAYMENT`, com reserva de 30 minutos e página de acompanhamento. Pagamentos, pedidos no admin, IA e recuperação de senha ainda não integram esta entrega.

Decisões: [ADR-0004](docs/architecture/adr/0004-project-bootstrap.md). Resultados e limitações: [validação SC-001](docs/quality/SC-001-VALIDATION.md). O isolamento implementado para empresa/loja e catálogo é verificado por testes A/B; observabilidade ampliada da SC-004 continua pendente.

SC-001 publicada no repositório privado confirmado pelo usuário. A [CI remota](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38797904235) aprovou backend e admin no commit `b6475731a0b5eb88b3c9c963e06fb2b78ce25b7b`: seis testes backend sem falhas/erros/ignorados, lint, tipos, build e smoke HTTP do admin aprovados. A primeira execução falhou no seletor Corretto; foi corrigido para Java `21`, sem alterar dependências da aplicação. SEC-003 permanece aberta, sem exceção aprovada: a última auditoria registrou cinco alertas altos na cadeia de desenvolvimento e zero com `--omit=dev`; ESLint 9 está fora de suporte. CI verde não aceita esse risco; a autorização da SC-002 foi concedida separadamente pelo responsável. Detalhes em [validação](docs/quality/SC-001-VALIDATION.md), [segurança](docs/security/SC-001-REVIEW.md) e [publicação](docs/quality/SC-001-PUBLICATION.md).

Agentes de IA: instruções em [AGENTS.md](AGENTS.md) (Claude via [CLAUDE.md](CLAUDE.md)); checks e revisão em [docs/quality/README.md](docs/quality/README.md); ferramentas e pendências do toolkit em [docs/setup.md](docs/setup.md).

## Estrutura

A página inicial do admin (`/`) apresenta a plataforma. `/admin/produtos` e `/loja/produto` são demonstrações locais com dados fictícios, sem API, autenticação ou persistência; `/componentes` reúne amostras dos componentes locais. Para operar lojas reais, use `/entrar` e `/lojas`; a vitrine real continua na aplicação `storefront-web`. As URLs operacionais foram preservadas. Ver [validação da landing](docs/quality/LANDING-VALIDATION.md).

```text
backend/          Spring Boot, Maven Wrapper, Flyway e testes
admin-web/        Next.js App Router, TypeScript e shell administrativo
storefront-web/   Next.js App Router, vitrine pública de consulta
infrastructure/   Compose e inicialização técnica do PostgreSQL
scripts/          execução local do backend em PowerShell
docs/             produto, arquitetura, ADRs, papéis e evidências
.github/workflows/ci.yml
```

## Ferramentas

- JDK 21. A validação local utilizou Corretto 21.0.9; a CI aprovou OpenJDK 21.0.12.1 / Corretto 21.0.12.12.1. O adaptador Corretto da action aceita somente a major `21`, selecionando seu patch disponível. Selecione o JDK no terminal; o Java global pode continuar em outra versão.
- Maven 3.10.0 obtido pelo Maven Wrapper 3.3.4, sem instalação global.
- Spring Boot 4.1.1; dependências gerenciadas pelo parent/BOM no `backend/pom.xml`.
- Node.js 24.12.0; npm local 11.21.0 e npm remoto aprovado 11.6.2 (ambos atendem ao engine 11 do projeto). Versões exatas das bibliotecas nos manifests e lockfiles de `admin-web/` e `storefront-web/`; a vitrine reutiliza as versões do admin.
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

Rotas: `/cadastro`, `/confirmar-email`, `/entrar`, `/lojas` (minhas lojas, destino após o login), `/conta`, `/lojas/{id}` e `/lojas/{id}/entrega`. API de mesma origem via Next.js: `/api/auth/csrf`, `/registration`, `/activation`, `/login`, `/me` e `/logout`, todos sob `/api/auth`. Mutações exigem CSRF; senhas e sessões não usam localStorage.

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

V1 cria `app_metadata` e registra `bootstrap=SC-001`. Essa tabela global guarda metadados técnicos. A SC-002 Etapa 1 adiciona V2 com `identity_user` e `identity_local_credential`; V3 acrescenta provas de e-mail e contadores de abuso; V4 cria o schema oficial do Spring Session JDBC; V5 adiciona empresa, vínculo e loja; V6 cria o catálogo com integridade tenant/loja e precisão monetária. V7 acrescenta saldos/movimentos de estoque; V8 permite publicação explícita de loja/produto, mantendo produtos existentes ocultos; V9/V10 criam variantes e fotos; V11 cria reservas de estoque e a função única de disponibilidade; V12 cria a configuração de entrega da loja. Flyway controla as versões; migrações anteriores não foram reescritas e a inicialização automática de sessão fica desabilitada. Contrato: [fundação de identidade](docs/architecture/IDENTITY_FOUNDATION.md).

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

GitHub Actions está configurado para executar backend, admin e storefront em push/PR. Actions são fixadas por commit, com permissões de leitura e sem deploy ou segredos de produção. Backend usa Testcontainers no runner, sem serviço PostgreSQL duplicado. Cada frontend executa lint, tipos, build e smoke. O novo job storefront ainda exige publicação e execução remota; validações locais não comprovam CI remota.

Origin confirmado: `https://github.com/IAbrahanArley/smart-commerce.git`, repositório privado, branch `main`. A etapa documental UI/UX foi publicada no SHA `c1014ed38fc13d56ed00c64acb34674baf60c17a`, com CI backend/admin aprovada. Main possui proteção e exige os checks backend/admin. A Etapa 1 da SC-002 foi publicada no SHA `adbc1b549e863745fd5f2136a4dfcb4e34d49c18`, com [backend/admin aprovados](https://github.com/IAbrahanArley/smart-commerce/actions/runs/38818169657). Essa execução histórica não comprova as alterações atuais de estoque/vitrine, ainda locais. SEC-003 continua aberta, sem aceite de risco.

## Limites da entrega

D1–D6 aprovadas no [ADR-0005](docs/architecture/adr/0005-identity-authentication.md). Fundação e benchmark: [contrato](docs/architecture/IDENTITY_FOUNDATION.md) e [validação da Etapa 1](docs/quality/SC-002-STAGE-01-VALIDATION.md). Mantido PBKDF2-HMAC-SHA256 com 600.000 iterações, salt16 e saída256; benchmark não foi repetido.

A SC-003 cria o vínculo OWNER junto com a empresa/loja; listagem e detalhe exigem esse vínculo em consultas escopadas pela identidade autenticada. O identificador enviado pelo cliente nunca concede acesso sozinho. Produção exige transporte de e-mail real, HTTPS e dimensionamento específico; o adaptador DEV não deve operar em produção. SEC-003 permanece aberta, sem alteração ou aceite de risco.

## Demonstração da SC-003

1. Criar conta e confirmar o e-mail pelo fluxo local descrito acima; depois entrar.
2. Em Minhas lojas, informar nome da empresa, nome da loja e identificador único (ex.: minha-loja).
3. Criar a loja e abrir seus detalhes. O estado “Em configuração” não representa vitrine publicada.
4. Entrar com outro lojista: somente os vínculos dele aparecem. Um identificador de loja de outro lojista não permite consulta.

Implementação: [ADR-0006](docs/architecture/adr/0006-tenant-store-onboarding.md). Testes de isolamento usam PostgreSQL descartável e não alteram o volume local. SEC-003 permanece aberta e inalterada.

## Demonstrar o catálogo básico

1. Entre, abra sua loja e selecione Produtos.
2. Informe nome, SKU e preço maior que zero (ex.: 49,90). Custo pode ficar em branco ou ser zero.
3. Cadastre e confira os valores. Abra Editar produto, altere e salve; recarregue para conferir a persistência.
4. SKU repetido na mesma loja mostra erro. Edição desatualizada exige carregar a versão atual, sem sobrescrever dados.

Rotas administrativas: /lojas/{tenantId}/produtos e /lojas/{tenantId}/produtos/{productId}. Publicação e estoque são ações separadas, descritas a seguir. [Decisões iniciais](docs/architecture/adr/0007-basic-catalog.md) e [validação do catálogo básico](docs/quality/CATALOG-VALIDATION.md). SEC-003 permanece inalterada.

## Arquitetura e padrões de desenvolvimento

Backend mantém o monólito modular com camadas explícitas dentro de identity, tenant e catalog: controllers, services, repositories, entities/domain e dto/request/response. Tenant e catálogo utilizam JPA/Spring Data; identidade, sessões e locks PostgreSQL especializados mantêm JDBC em repositórios. Nenhum SQL fica nos serviços. Flyway controla o schema; Hibernate valida e Open Session in View fica desabilitado.

Padrões adaptados do project-rh: [ADR-0008](docs/architecture/adr/0008-layered-persistence-refactoring.md), [convenções](docs/architecture/CODING_STANDARDS.md) e [validação da refatoração](docs/quality/ARCHITECTURE-REFACTOR-VALIDATION.md). No perfil dev, após login no admin usando 127.0.0.1, a documentação autenticada está em http://127.0.0.1:8080/swagger-ui/index.html e http://127.0.0.1:8080/v3/api-docs. Swagger é somente consulta, sem executar operações. Fora de dev, a documentação permanece desabilitada por padrão.

## Demonstrar estoque básico

Abra sua loja, vá a Produtos e selecione Estoque no produto. O saldo inicial é zero. Registre uma Entrada com quantidade inteira e motivo; depois faça um ajuste para adicionar ou retirar unidades. Saídas acima do saldo são bloqueadas. Confira o saldo e o histórico após recarregar; há paginação para movimentações anteriores.

Se houver falha de conexão, permaneça na tela e use Repetir mesma solicitação com segurança. O identificador preservado impede duplicação. Se sair da tela, confira o histórico antes de criar uma nova operação. Não há reservas, pedidos ou baixa automática nesta entrega. [Decisões do estoque](docs/architecture/adr/0009-basic-inventory.md) e [validação](docs/quality/INVENTORY-VALIDATION.md). SEC-003 permanece aberta e inalterada.

## Executar a vitrine pública

Com backend e admin iniciados, em outro terminal:

```powershell
Set-Location storefront-web
npm ci --no-fund --no-audit
npm run dev
```

Acesse http://localhost:3001/lojas/{slug}. Use localhost na vitrine e 127.0.0.1 no admin para manter os cookies host-only separados em desenvolvimento. O servidor da vitrine usa STOREFRONT_API_ORIGIN (padrão http://127.0.0.1:8080); não encaminha credenciais administrativas. Fora do desenvolvimento, definir origens separadas e HTTPS antes da disponibilização externa.

No admin, selecione Publicar produto na edição de cada item; depois Publicar loja nos detalhes da loja. Ambos precisam estar publicados. Ocultar produto ou loja remove o conteúdo de novas consultas públicas. A vitrine mostra nome, preço e disponibilidade, sem custos nem saldo exato. Esta entrega permite consulta: carrinho, pedidos e pagamentos são etapas posteriores.

Verificações da vitrine: npm run lint, npm run typecheck, npm run build e npm run test:smoke em storefront-web. CI inclui o job storefront além de backend/admin. [Decisões](docs/architecture/adr/0010-public-storefront.md) e [validação local](docs/quality/STOREFRONT-VALIDATION.md).

## Fotos, descrição e variações

No cadastro, escolha produto simples ou com variações. Para variações, informe até três opções (por exemplo, cor/tamanho), gere no máximo 50 combinações e confira SKU, preço e custo de cada uma antes de salvar. O tipo é fixo após a criação: produtos existentes e seus movimentos não são convertidos nem apagados automaticamente. Abra o estoque da combinação desejada para registrar entradas/ajustes.

A descrição aceita parágrafos, negrito, itálico e listas, com HTML básico sanitizado no backend. Conteúdo ativo, estilos e imagens embutidas não são permitidos. Depois de salvar o produto, envie suas fotos na edição: JPEG/PNG, até 5 MiB cada, até seis fotos, lados de 300–4096px e até 16 MP. Informe texto alternativo e use os controles de ordem/capa; o servidor normaliza orientação JPEG, dimensões e metadados. Fotos ficam no PostgreSQL local e acompanham seu backup, sem configurar serviço externo. Para atualizar fotos, salve primeiro os demais campos.

Publique produto e loja para consultar fotos, descrição e combinações na vitrine. [Contratos, limites e decisões](docs/architecture/adr/0011-catalog-media-description-variants.md) e [validação local](docs/quality/CATALOG-MEDIA-VARIANTS-VALIDATION.md). A evolução adiciona somente jsoup 1.23.2 no backend; frontend preserva dependências existentes. SEC-003 continua aberta e inalterada.

## Conexão com o Mercado Pago (SC-012, sandbox)

Em **Pagamentos**, no admin, o dono da loja conecta a própria conta Mercado Pago por OAuth com PKCE. O backend só ativa essa integração com as variáveis `MERCADO_PAGO_CLIENT_ID`, `MERCADO_PAGO_CLIENT_SECRET`, `MERCADO_PAGO_REDIRECT_URI` e `PAYMENTS_TOKEN_ENCRYPTION_KEY`; sem elas, a página informa que a conexão está indisponível. Os valores e a forma de gerar a chave estão em [SC-012-VALIDATION.md](docs/quality/SC-012-VALIDATION.md). Os testes usam um servidor OAuth falso, sem acessar o Mercado Pago.

Com a conta conectada (SC-013), "Confirmar pedido" na vitrine cria a order do Checkout Pro e leva o comprador ao Mercado Pago. Sem conta conectada, a loja não recebe pedidos novos enquanto a integração estiver configurada. A página de retorno mostra "pagamento em confirmação" e nunca confia nos parâmetros da URL; a confirmação só vem do backend (SC-014/SC-015, ainda não implementadas). Defina `STOREFRONT_PUBLIC_ORIGIN` com a origem HTTPS da vitrine para o Mercado Pago devolver o comprador. Em localhost a variável fica vazia e o comprador volta pelo link do pedido. Detalhes em [SC-013-VALIDATION.md](docs/quality/SC-013-VALIDATION.md).

A confirmação (SC-014) vem do webhook do Mercado Pago, autenticado por `MERCADO_PAGO_WEBHOOK_SECRET`. Uma rotina do backend consulta a order no provedor e, se estiver paga, marca o pedido como **Pago** e baixa o estoque uma única vez. Pagamento que chega depois do fim da reserva tenta reservar de novo; sem estoque, o admin mostra o alerta "pago sem estoque". O webhook exige URL HTTPS pública: em localhost, sem túnel (P6), a confirmação vem da conciliação (SC-015), que consulta o Mercado Pago a cada minuto enquanto a order está aberta e por 24 h depois do vencimento. O detalhe do pedido no admin mostra o pagamento e qualquer divergência encontrada. Detalhes em [SC-015-VALIDATION.md](docs/quality/SC-015-VALIDATION.md).

No pedido pago, o dono da loja pode **reembolsar** o valor total (SC-016): informa o motivo, escolhe se as unidades voltam ao estoque e confirma num segundo passo. O pedido só aparece como "Reembolsado" depois que o Mercado Pago confirma. Detalhes em [SC-016-VALIDATION.md](docs/quality/SC-016-VALIDATION.md). Detalhes em [SC-014-VALIDATION.md](docs/quality/SC-014-VALIDATION.md).

## Ambiente de teste hospedado (Render + Vercel)

Para observar o sistema fora da máquina local, o [guia de deploy](docs/deploy/HOSTED-TEST-ENVIRONMENT.md) descreve o backend no Render (imagem `backend/Dockerfile`), o admin e a vitrine na Vercel e o e-mail de cadastro pelo Brevo, com todas as variáveis.

- **Natureza do ambiente:** de teste, com dados fictícios e sem pagamento, conforme o [ADR-0014](docs/architecture/adr/0014-hosted-test-environment.md).
- **Checkout fora de DEV:** só existe com `DEPLOYMENT_ENVIRONMENT=hosted-test`, como exceção H2 ao D6.
- **Aviso:** todas as páginas mostram que se trata de um ambiente de teste.

## Entrega da loja e carrinho (Sprint 3, parcial)

Decisões aprovadas no [ADR-0012](docs/architecture/adr/0012-stock-reservation-cart-orders.md) (D1–D7) e plano em [SPRINT_03.md](docs/product/SPRINT_03.md). Entregues SC-005 a SC-007:

- **Reservas (SC-005):** disponível = físico − reservas ativas não expiradas (30 minutos). A reserva expira pelo relógio, sem depender de rotina. O estoque no admin mostra físico, reservado e disponível; um ajuste que deixaria o físico abaixo do reservado é bloqueado. Ainda não há endpoint que crie reservas: elas nascem com o pedido (SC-008).
- **Entrega (SC-006):** nos detalhes da loja, a seção Entrega ativa retirada (com instruções) e/ou frete fixo único (zero = grátis) com a área atendida. Não há cálculo por CEP.
- **Carrinho (SC-007):** na vitrine, escolha a combinação e a quantidade e selecione Adicionar ao carrinho; o carrinho fica em `/lojas/{slug}/carrinho`, num cookie HttpOnly por loja, e preços e disponibilidade vêm sempre da cotação do backend, que também lista as opções de entrega ativas da loja.
- **Checkout e pedido (SC-008, só backend):** `POST /api/public/stores/{slug}/orders` cria o pedido `PENDING_PAYMENT` com número por loja, snapshot de preços e reserva de 30 minutos; repetir o mesmo `requestId` devolve o mesmo pedido; total divergente retorna `PRICE_CHANGED` sem gravar nada. O comprador consulta o status em `GET .../orders/{id}` com o token devolvido uma única vez.
- **Checkout na vitrine (SC-009, só DEV):** com `STOREFRONT_CHECKOUT_ENABLED=true` em `storefront-web/.env.local`, o carrinho mostra Finalizar pedido. O comprador escolhe retirada ou frete fixo, informa nome, e-mail, telefone e, na entrega, o endereço, e confirma. A página `/lojas/{slug}/pedidos/{id}` mostra número, situação, prazo da reserva e valores, e só abre no navegador que fez o pedido (cookie HttpOnly por pedido). Sem a variável, não há link nem rota. Pagamento ainda não existe.
- **Pedidos no admin (SC-010):** em Pedidos, no menu da loja, o lojista lista os pedidos (filtro por situação e páginas de 20), abre o detalhe com itens, valores, comprador e entrega, e cancela um pedido pendente informando o motivo. O cancelamento libera o estoque reservado na hora. O backend marca pedidos vencidos como expirados a cada minuto e, uma vez por dia, remove os dados pessoais de pedidos não pagos com mais de 90 dias (D2). As rotinas rodam no próprio backend; `ORDERS_MAINTENANCE_ENABLED=false` as desliga.

O checkout fica **desligado por padrão** e ligado apenas no perfil `dev` (D6: não há pagamento). Fora do `dev`, o backend se recusa a iniciar com `ORDERS_CHECKOUT_ENABLED=true` sem `STOREFRONT_FORWARDING_SECRET`. Para limitar tentativas pelo IP real do visitante, defina no mesmo terminal, antes de `./scripts/start-backend.ps1`, `$env:STOREFRONT_FORWARDING_SECRET` com um valor local aleatório de pelo menos 32 caracteres e o mesmo valor em `storefront-web/.env.local`; sem ele, o limite usa o endereço da conexão. A vitrine só repassa o IP quando `STOREFRONT_TRUST_FORWARDED_FOR=true`, que deve ser usado apenas atrás de um proxy que sobrescreve `X-Forwarded-For`. Nunca versione esse valor. Validações: [SC-008](docs/quality/SC-008-VALIDATION.md) e [SC-009](docs/quality/SC-009-VALIDATION.md).
