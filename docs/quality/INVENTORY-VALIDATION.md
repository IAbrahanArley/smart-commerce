# Validação local — Estoque básico

Data: 2026-10-17. Base publicada: 1a04070b0c2c0b8e4c4843a8b9de9dd0e8af4595. Escopo e contrato: [ADR-0009](../architecture/adr/0009-basic-inventory.md).

## Entrega

Módulo inventory com camadas explícitas, entidades JPA, DTOs e repositório especializado. V7 aditiva cria saldo e movimentos; FK composta produto/tenant e unicidade tenant/requestId preservam integridade. Entradas e ajustes exigem motivo, não permitem saldo negativo e persistem saldo/histórico atomicamente. Interface em /lojas/{tenantId}/produtos/{productId}/estoque, com links no catálogo, saldo e histórico paginado. Sem dependências novas, reservas, pedidos ou vitrine.

## Verificações

Frontend: npm run lint, typecheck, build e test:smoke aprovados no build final. Smoke inclui a nova rota e parsing de unidades inteiras. Revisão React e Web Interface Guidelines atualizadas em 2026-10-17, fonte https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md. Identidade visual preservada.

Backend: BUILD SUCCESS em 10:43:59 -03:00, com 67 testes aprovados (26 unitários/arquiteturais e 41 integrações), zero falhas/erros/ignorados. Maven verify/Spotless, JDK 21 e PostgreSQL 18.6/Testcontainers. Log local final: backend/target/inventory-final-verify.log. Cinco novas integrações cobrem entrada/ajuste/persistência, auditoria do ator, replay sem duplicação, chave reutilizada com payload/produto diferente, validação de quantidades/motivos, overflow, sessão/CSRF/origem, A/B e revogação, concorrência sem saldo negativo, paginação e constraints PostgreSQL.

A primeira execução detectou diferença entre data inicial em nanossegundos e replay após persistência em microssegundos. Corrigido o instante do lançamento para a precisão suportada pelo PostgreSQL, mantendo o teste de igualdade integral do movimento.

## Navegador

Produto sintético de demonstração: saldo inicial zero; entrada de dez unidades e ajuste de retirada de três confirmados; tentativa de retirada de oito rejeitada com mensagem adequada. Reload preservou saldo sete e somente dois movimentos. Datas exibidas no fuso de São Paulo. Em 320 CSS px, documento com 305px sem overflow horizontal; screenshot revisada e viewport restaurado. Página deixada disponível; backend 8080/admin 3000.

A retentativa ambígua preserva requestId e conteúdo no estado da tela, com aviso antes de sair. Isso foi revisado em código; a duplicação foi exercitada na integração HTTP concorrente. Falha de rede no navegador não foi simulada. Axe, leitor de tela e avaliação WCAG completa não executados; não há alegação de conformidade integral.

## Revisões e pendências

Backend implementou persistência/concorrência; frontend implementou tela e checks; segurança fez revisão independente sem bloqueadores; coordenador escreveu/executou integrações, revisou UI e validou o navegador. SQL permanece nos repositórios, com contrato de autorização do catálogo no serviço e locks mantidos até o commit.

Histórico é protegido contra alteração pela aplicação, sem garantia contra SQL privilegiado. Observação baixa de segurança: sequência global do cursor pode revelar intervalos de atividade agregada; considerar cursor opaco antes de requisitos de confidencialidade de volume. Não há acesso cruzado aos registros e nenhum aceite formal de risco foi inferido.

Reservas e prazo de expiração exigem decisão própria antes do checkout. SEC-003 permanece aberta e inalterada. Sem commit, push ou CI remota desta entrega; nenhuma etapa futura iniciada.

Conferência final: git diff --check, whitespace dos arquivos novos e links locais aprovados. Gitleaks sem vazamentos no conjunto de alterações locais; comparação silenciosa com credenciais locais sem correspondências. SEC-003, dependências e migrações anteriores intactas; nenhum arquivo staged.
