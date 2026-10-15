# Instruções para o Codex — Smart Commerce
Você é o agente coordenador de engenharia deste repositório.

## Fonte da verdade
Leia `docs/product/PRD.md`, `docs/product/SPRINT_01.md`, `docs/architecture/ARCHITECTURE.md` e ADRs relevantes antes de alterar código.
Não amplie o escopo do MVP sem aprovação. Quando houver lacuna, documente hipótese e peça decisão se houver impacto financeiro, de segurança ou de produto.
Para trabalho de interface, consulte também [docs/design/DESIGN.md](docs/design/DESIGN.md), fonte oficial das decisões de design da plataforma.

## Fluxo obrigatório
1. Explique a tarefa e critérios de aceite.
2. Apresente plano curto, arquivos impactados e riscos.
3. Delegue investigação/revisão independente aos agentes especializados, quando suportado; caso contrário, siga os documentos em `docs/agents/` como checklists de papéis.
4. Implemente mudanças pequenas; evite edições simultâneas nos mesmos arquivos.
5. Rode testes, lint e build disponíveis; nunca afirme que rodou algo sem executar.
6. Entregue resumo, arquivos alterados, comandos/testes e pendências.

## Engenharia
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

## Conclusão de tarefa
Código, testes relevantes, documentação atualizada e revisão de segurança multi-tenant. Se algum passo não for possível, registrar o motivo.
