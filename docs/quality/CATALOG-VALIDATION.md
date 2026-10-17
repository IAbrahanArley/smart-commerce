# Validação local — Catálogo básico

Data: 2026-10-17. Entrega autorizada posteriormente à SC-003. Alterações locais, sem commit/push ou CI remota desta entrega.

## Evidências executadas

- Backend: Maven Wrapper com Java 21, formatação e verify; BUILD SUCCESS em 09:21:29 -03:00. 57 testes: 23 unitários e 34 de integração, sem falhas/erros/ignorados. Log local ignorado: backend/target/catalog-verify.log; relatórios Surefire/Failsafe em target.
- Catálogo acrescenta 3 testes unitários e 6 integrações HTTP/PostgreSQL 18.6 via Testcontainers. Cobrem valores exatos, custo NULL/zero, preço positivo, limites Unicode/SKU, persistência, isolamento A/B, IDs forjados, revogação, sessão/CSRF/origem, SKU concorrente, versão otimista e FK composta. Escritas SQL com casas excedentes são rejeitadas, sem arredondar o valor anterior.
- V6 aplicada sobre o banco local em V5, sem apagar dados. Migrações anteriores validadas pelo Flyway.

## Revisões

Backend implementou regras/persistência transacional. Frontend aplicou DESIGN.md e revisão React/Web Interface Guidelines. QA produziu testes independentes e apontou foco prematuro no erro de SKU, corrigido para ocorrer após habilitar o campo. Segurança revisou isolamento, precisão, CAS, CSRF e sanitização de erros: nenhum bloqueador; listagem sem paginação registrada para evolução.

Fonte de UI/UX consultada por frontend/QA em 2026-10-17: https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md. Sem bibliotecas novas. Axe, leitor de tela e avaliação WCAG completa não executados; não há alegação de conformidade integral.

## Limites

Sem variantes, categorias, imagens, estoque, vitrine, exclusão, pagamentos ou IA. Paginação deve anteceder volumes relevantes. CI remota e publicação dependem de autorização separada. SEC-003 permanece aberta, inalterada e sem aceite de risco. Benchmark de senha preservado, sem repetição.

## Frontend e navegador

Lint, TypeScript, build e smoke aprovados no build final após a correção de foco. Smoke verifica rotas de lista/edição e conversão/apresentação monetária exata, inclusive limite de 17 dígitos. Execução elevada foi necessária para permissões de geração .next e HTTP local; houve repetição aprovada, sem contornar controles da aplicação. Um aviso Node MODULE_TYPELESS_PACKAGE_JSON na importação TypeScript do smoke não impediu sua execução.

Navegador local: preço zero rejeitado com foco no campo; criação de produto sintético por 49,90 com custo em branco exibiu R$ 49,90 e Não informado; edição para preço 54,90 e custo 25,00 persistiu após reload e apareceu na lista. SKU duplicado exibiu erro associado, aria-invalid=true e foco product-sku após a resposta. Reflow em 320 CSS px: largura do documento 305px, sem overflow horizontal; screenshot inspecionada e viewport restaurado. Identidade visual preservada.

Durante a reconstrução do frontend, navegar no servidor que ainda usava .next em alteração causou erro transitório de carregamento. Servidor parado e reiniciado com build final; navegação e edição aprovadas depois. Backend e painel finais permaneceram disponíveis em 8080 e 3000.

## Verificações finais

Revisão de diff e arquivos novos, links locais e whitespace aprovados; varredura Gitleaks sobre o conjunto de alterações locais sem vazamentos, incluindo comparação sem impressão de credenciais locais. Documentos de segurança e dependências npm intactos. Nenhum arquivo staged; nenhum commit ou push realizado. O conjunto local inclui alterações anteriores de autenticação/tenant/design, além do catálogo; não representa publicação isolada.
