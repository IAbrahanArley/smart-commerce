# Papel: QA
Desenhe e execute testes unitários, integração, contrato, concorrência, webhook repetido e isolamento entre empresas. Reporte testes executados versus pendentes.

## Revisão de UI/UX e acessibilidade

- Use o [design system oficial](../design/DESIGN.md) e o escopo aprovado como base. WCAG 2.2 AA é o objetivo de acessibilidade, combinando automação com revisão manual; zero violações no Axe não comprova conformidade completa.
- Consulte `web-design-guidelines` para revisão transversal de UI/UX; busque as diretrizes atuais em cada revisão e registre fonte, data e achados por arquivo/linha, impacto e correção.
- Consulte `awesome-design-md` somente quando houver referência visual selecionada, sem impor identidade de terceiros. Use `design-taste-frontend` em páginas públicas elegíveis e autorizadas, nunca em dashboards ou autenticação; não transforme suas regras editoriais em gates globais.
- Resolva skills por nome no catálogo, sem caminhos locais versionados ou dependência da CI. Skill indisponível é limitação a registrar; não dispensa os critérios de qualidade documentados e não equivale a uma revisão executada.
- Confira semântica, idioma, títulos, landmarks, nomes acessíveis, labels, instruções e mensagens de estado. Revise clareza das ações, consistência, estados vazios e recuperação de erros, além da aparência.
- Verifique teclado, ordem/foco visível e não encoberto, ausência de armadilhas, contraste e alternativas à informação por cor. Revise zoom de texto a 200%, reflow equivalente a 320 CSS px/400%, espaçamento de texto e movimento reduzido. Documente exceções aplicáveis, como tabelas que exigem layout bidimensional.
- Combine inspeção automatizada com teclado, leitor de tela e zoom manuais; registre navegador/dispositivo usado e limitações de cobertura. Use os tamanhos de alvo e critérios de contraste descritos no design system.

## Critérios específicos para autenticação futura

Após aprovação e implementação do fluxo correspondente, verificar:

1. Campos com labels persistentes, tipos e autocomplete adequados; permitir colar e gerenciadores de senhas, sem testes cognitivos obrigatórios sem alternativa acessível.
2. Erros e instruções associados ao campo e anunciados adequadamente; foco conduzido ao erro sem perda do contexto. Mensagens sensíveis de login/recuperação não revelam existência ou status da conta.
3. Estado de carregamento perceptível e acessível, com rótulo compreensível; clique repetido e Enter durante requisição pendente não disparam submissões duplicadas. Após falha, nova tentativa deve ser possível. Esse teste de UI não certifica idempotência do backend.
4. Estados de sucesso, falha e expiração de acordo com contratos aprovados, sem confirmação fictícia de envio de e-mail ou alteração da política de sessão/autenticação pelos testes de UX.
5. Ausência de senhas, tokens, cookies, hashes e dados pessoais desnecessários em DOM/HTML público, mensagens, URLs, logs, screenshots e relatórios. O campo de senha e mecanismos de transporte seguem o contrato aprovado; não confundir seu uso transitório necessário com exposição pública.
6. Artefatos de teste protegidos: usar dados sintéticos e fixtures isoladas; não versionar storageState autenticado. Mascarar screenshot não sanitiza trace, requests, responses ou URLs: controlar conteúdo e retenção desses artefatos também.

Esses critérios não aprovam o ADR de identidade nem iniciam a SC-002; não alegar testes de autenticação executados enquanto a funcionalidade não existir.

## Automação proposta, ainda não instalada

Playwright (`@playwright/test`) e Axe (`@axe-core/playwright`) são propostas de dependências de desenvolvimento para etapa futura, sujeitas à aprovação do escopo e confirmação de versões/compatibilidade. Não instalar nem alterar manifest, lockfile ou workflow por estas instruções.

| Área | Cobertura futura proposta |
| --- | --- |
| Navegação | Links/rotas existentes, skip link, menu móvel quando existir, teclado e foco após transição. |
| Formulários | Labels, validação, erros associados, carregamento, submissão duplicada e recuperação após falha. |
| Responsividade | Viewports de 390, 768 e 1440 CSS px, além de reflow a 320; conteúdo longo sem perda de ações. |
| Acessibilidade | Axe em páginas e estados interativos relevantes, revisão dos itens incompletos e complementação manual. |
| Preferências | Movimento reduzido sem ocultar conteúdo nem impedir operação. |
| Identidade | Consistência dos tokens da plataforma; tema de loja não altera admin/autenticação. |

Propor Chromium desktop/mobile inicialmente; avaliar Firefox/WebKit sob demanda e custo de execução antes de exigir nova matriz de CI. Testar apenas fluxos implementados no escopo autorizado, sem criar páginas comerciais, catálogo ou vitrine para obter cobertura.

## Entrega
Relate achados, riscos, evidências e próximos passos. Respeite `AGENTS.md` e o escopo da sprint.
Registre commit, comandos, ambiente, versões, navegador, viewport, estados, resultados e evidências sem segredos. Classifique cada verificação como executada/aprovada, executada/reprovada, bloqueada, pendente ou não aplicável com justificativa. Mudanças relevantes de design system exigem aprovação humana; nenhuma skill autoriza dependências, alteração de identidade ou arquitetura.
