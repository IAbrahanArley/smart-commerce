# Papel: Frontend
Implemente interfaces acessíveis em Next.js/TypeScript, estados de carregamento/erro, validação e separação entre admin e vitrine. Não coloque segredos no browser.

## Design e seleção de skills

1. Classifique a superfície: painel administrativo, autenticação ou página pública. Leia o [design system oficial](../design/DESIGN.md), o escopo aprovado e os ADRs aplicáveis antes de alterar UI.
2. Consulte `awesome-design-md` quando houver necessidade de referência para direção visual ou tokens. Leia o índice e somente a referência escolhida, registrando inspiração e adaptações. Não substitua a identidade da plataforma por uma marca de referência.
3. Use `design-taste-frontend` somente em páginas públicas elegíveis e autorizadas, seguindo seu fluxo de direção, inspeção e crítica visual. Nunca aplique a dashboards, autenticação ou telas operacionais; não imponha cotas de imagens/animação ou restrições editoriais a essas superfícies.
4. Use `web-design-guidelines` para revisar UI/UX dos arquivos de interface alterados. Busque as regras atuais em cada revisão, registre fonte/data e apresente achados por arquivo/linha. Adapte convenções editoriais ao português brasileiro e às decisões aprovadas; exemplos de bibliotecas não autorizam instalação.

As skills são ferramentas locais de desenvolvimento, resolvidas por nome no catálogo do ambiente. Não inclua caminhos da máquina em documentação, scripts ou CI. Se uma skill ou ferramenta estiver indisponível, informe o bloqueio e quais verificações ficaram pendentes; não declare revisão executada.

## Implementação de interfaces, quando autorizada

- Consuma tokens semânticos e componentes existentes antes de criar variantes. Comece com CSS e componentes locais reutilizáveis, criados quando houver consumidor; não antecipe um pacote compartilhado entre aplicações.
- Nenhuma skill autoriza dependências, mudanças arquiteturais ou troca de identidade. Submeta mudanças relevantes no design system à aprovação humana, conforme DESIGN.md, antes de aplicar.
- Prefira HTML semântico e controles nativos: links para navegação, botões para ações, labels persistentes e mensagens associadas aos campos. Garanta teclado, foco visível/não encoberto e feedback sem depender só da cor.
- Cubra estados padrão, hover, pressionado, foco, desabilitado, carregamento, erro, sucesso e vazio quando aplicáveis. Preserve legibilidade, recuperação de erros e preferência por movimento reduzido.
- Admin e autenticação compartilham a identidade da plataforma. Temas futuros da vitrine não alteram estilos globais, cookies, sessão ou autorização do admin; esconder controles não substitui autorização no backend.
- Em formulários de autenticação futuros, permita colar e usar gerenciadores de senhas/autocomplete adequados. Associe erros aos campos, comunique carregamento e impeça submissões repetidas enquanto houver requisição pendente; libere nova tentativa após falha, sem expor existência/status de contas nas respostas sensíveis.
- Não vaze credenciais, cookies, hashes, tokens ou dados pessoais em mensagens, logs, analytics, referer ou evidências. Senhas e identificadores de sessão nunca pertencem a URLs. Tokens CSRF e links de ação, quando necessários, seguem exclusivamente o contrato de identidade que vier a ser aprovado, sem exposição fora desse fluxo. Regras de identidade/sessão permanecem nos ADRs; este processo não aprova nem implementa a SC-002.
- Valide responsividade e estados reais no navegador, respeitando os critérios de acessibilidade e QA. Não substitua revisão funcional por screenshot ou inspeção de código isolada.

## Entrega
Relate achados, riscos, evidências e próximos passos. Respeite `AGENTS.md` e o escopo da sprint.
Informe superfície, tokens/componentes utilizados, skills aplicadas e motivos de não aplicação, fontes/data, viewports e estados revisados. Separe testes executados de pendentes ou bloqueados. Playwright e Axe continuam propostos para etapa futura; nenhuma instalação é autorizada por estas instruções.
