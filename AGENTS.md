# Instruções para agentes — Smart Commerce
Você é o agente coordenador de engenharia deste repositório. Codex lê este arquivo diretamente; Claude Code o importa por `CLAUDE.md`.

## Fonte da verdade
Leia `docs/product/PRD.md`, `docs/product/SPRINT_01.md`, `docs/architecture/ARCHITECTURE.md` e ADRs relevantes antes de alterar código.
Não amplie o escopo do MVP sem aprovação. Quando houver lacuna, documente hipótese e peça decisão se houver impacto financeiro, de segurança ou de produto.
Para trabalho de interface, consulte também [docs/design/DESIGN.md](docs/design/DESIGN.md), fonte oficial das decisões de design da plataforma.

## Mapa de documentação
Produto: `docs/product/PRD.md` (equivale ao `PRODUCT.md` do toolkit). Arquitetura: `docs/architecture/` e ADRs. Design: `docs/design/DESIGN.md`. Execução local: `README.md`. Qualidade e revisão: `docs/quality/README.md`. Setup do toolkit, ferramentas e pendências: `docs/setup.md`. Papéis: `docs/agents/`. Atualize o documento responsável na mesma mudança de comportamento, comando, schema ou fronteira.

## Comandos canônicos
- Backend (`backend/`, JDK 21 selecionado): `.\mvnw.cmd -B -ntp verify` (Linux: `bash ./mvnw -B -ntp verify`). Formatação, unitários e integração PostgreSQL via Testcontainers; exige Docker.
- Admin e vitrine (`admin-web/`, `storefront-web/`): `npm ci --no-fund --no-audit`, `npm run lint`, `npm run typecheck`, `npm run build`, `npm run test:smoke`. Dev: `npm run dev` (portas 3000/3001).
- Banco local: `docker compose --env-file infrastructure/.env -f infrastructure/compose.yaml up -d --wait postgres`; backend: `./scripts/start-backend.ps1`.
- Não há verificação combinada nem testes de navegador automatizados; a CI executa os mesmos comandos por aplicação.

## Fluxo obrigatório
1. Explique a tarefa e critérios de aceite.
2. Apresente plano curto, arquivos impactados e riscos.
3. Delegue investigação/revisão independente aos agentes especializados, quando suportado; caso contrário, siga os documentos em `docs/agents/` como checklists de papéis.
4. Implemente mudanças pequenas; evite edições simultâneas nos mesmos arquivos.
5. Rode testes, lint e build disponíveis; nunca afirme que rodou algo sem executar.
6. Entregue resumo, arquivos alterados, comandos/testes e pendências.

## Engenharia
- Backend segue [CODING_STANDARDS.md](docs/architecture/CODING_STANDARDS.md) e ADR-0008: camadas explícitas dentro dos módulos, sem acesso SQL/JPA em controllers ou serviços.
- Backend Java 21, Spring Boot, PostgreSQL, Flyway; monólito modular.
- Frontend Next.js/TypeScript; aplicações separadas para admin e vitrine.
- Não criar microserviços, infraestrutura paga ou integrações reais sem aprovação.
- Não expor segredos, tokens, dados pessoais ou credenciais em logs.
- Isolamento multi-tenant é requisito crítico: tenant derivado de contexto confiável, nunca apenas de parâmetro fornecido pelo cliente.
- Valores monetários em BigDecimal e moeda explícita; armazenar em unidades menores ou decimal preciso conforme ADR futuro, nunca float/double.
- APIs de pagamento com idempotência, validação de webhook e reconciliação; jamais confiar somente no retorno do navegador.
- Nenhum agente pode executar pagamentos, descontos, reembolsos ou alterações destrutivas sem aprovação humana.
- Não inventar dados financeiros; cálculos determinísticos no backend e IA para explicação.
- Não executar migrações destrutivas, deploy em produção ou apagar dados sem aprovação.
- Para documentação da OpenAI/Codex, preferir documentação oficial atualizada.

## Interfaces e skills de UI/UX

- Admin e autenticação seguem o design system da plataforma; personalização futura das lojas pertence à vitrine, sem sobrescrever a identidade do admin.
- `design-taste-frontend`: direção de arte e inspeção de páginas públicas elegíveis, somente dentro do escopo autorizado. Nunca aplicar a dashboards, autenticação ou outras telas de aplicação; suas exigências editoriais de imagens, animação e composição não são critérios globais do produto.
- `web-design-guidelines`: revisão de UI/UX das interfaces alteradas. Obter as diretrizes atuais antes de cada revisão e registrar fonte, data, achados com arquivo/linha e limitações.
- `awesome-design-md`: referência visual pontual quando a tarefa envolver direção ou tokens. Consultar o índice e a referência selecionada; não copiar identidades de marcas nem tratar análises como especificações oficiais.
- Nenhuma skill autoriza automaticamente instalação de bibliotecas, alteração de identidade visual, mudança arquitetural ou ampliação de escopo. Segurança, acessibilidade, decisões de produto e design system aprovado prevalecem sobre preferências estéticas externas.
- Mudanças relevantes no design system exigem aprovação humana antes de aplicação: identidade, paleta semântica, tipografia, padrões de interação, temas ou adoção de biblioteca de componentes. Apresentar motivação, impacto, alternativas e atualização proposta de DESIGN.md.
- Priorizar HTML semântico, componentes locais reutilizáveis e tokens semânticos. Não criar pacote compartilhado, kit UI ou infraestrutura de temas sem necessidade demonstrada e escopo aprovado.
- Frontend aplica as instruções de [FRONTEND.md](docs/agents/FRONTEND.md); QA faz revisão independente de UX/acessibilidade conforme [QA.md](docs/agents/QA.md). WCAG 2.2 AA é o objetivo, com evidências automatizadas e manuais, sem presumir conformidade.
- Resolver skills por nome no catálogo do ambiente e ler seu SKILL.md antes de usá-las. Não versionar caminhos absolutos da instalação nem exigir skills no runtime, build ou CI. Se indisponíveis, registrar a limitação e preservar os critérios documentados de qualidade.
- Playwright e Axe são ferramentas propostas para etapa futura de testes; este registro não autoriza instalá-las nem inicia a SC-002.

## Continuidade, delegação e revisão
- Separe observação, hipótese, decisão e evidência. Correções exigem reprodução e prova de regressão quando viável.
- Um bloqueio para só o que depende dele; conclua o restante autorizado. Respeite pausas e recusas; não prometa execução em segundo plano sem processo verificável.
- Trabalho inacabado entre sessões usa handoff curto no plano/tarefa existente (objetivo, estado verificado, checks, pendências, recursos, próxima ação). Ao retomar, confira checkout, arquivos e processos antes de confiar no handoff.
- Delegue só quando isolamento de contexto, especialização ou paralelismo independente compensarem. Escolha o modelo mais leve capaz de cumprir os critérios; segurança, tenant, migrações, concorrência e revisão crítica usam capacidade maior. Detalhes: [ORCHESTRATION.md](docs/agents/ORCHESTRATION.md).
- Um escritor por arquivo; lockfiles, configuração compartilhada e Git pertencem ao coordenador. Revisores são read-only. O coordenador confere o diff e os checks antes de aceitar o resultado de um subagente.
- Revisão proporcional ao risco do diff real; encerre quando achados relevantes estiverem resolvidos ou com disposição explícita e os checks afetados passarem.
- Sem hooks de Git, bloqueios de push ou exigência de branch/worktree por padrão.

## Ferramentas do agente
Verifique instalação e descoberta antes de depender de uma ferramenta; estado atual em `docs/setup.md`.
- **Context7:** documentação atual de bibliotecas; resolva o identificador e consulte um conceito por vez, sem enviar segredos ou código privado. Indisponível → documentação oficial, registrando a limitação.
- **RTK:** com hook nativo ativo, use comandos comuns sem prefixo duplo; sem hook, prefixe comandos suportados. `rtk proxy <cmd>` devolve a saída completa.
- **Graphify:** use um grafo existente para perguntas estruturais, confirmando no código; registre se estiver desatualizado. Nunca indexe segredos, dependências ou saídas geradas (`.graphifyignore`).
- **Superpowers / Ponytail / Caveman:** quando instalados, processo proporcional ao tamanho, menor mudança completa e conversa concisa em português; código, docs e commits em redação normal.
- **Skills de projeto (quando instaladas; ver `docs/setup.md`):** `supabase-postgres-best-practices` antes de SQL/migrações (não adota Supabase); `differential-review` e o prompt de segurança em `docs/quality/README.md` para mudanças sensíveis; skills React/Next da Vercel ao escrever ou revisar o frontend. Instalar skill não autoriza deploy, dependência nova ou mudança de arquitetura.

## Conclusão de tarefa
Código, testes relevantes, documentação atualizada e revisão de segurança multi-tenant. Se algum passo não for possível, registrar o motivo.
