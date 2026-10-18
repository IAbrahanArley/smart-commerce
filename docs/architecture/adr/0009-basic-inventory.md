# ADR-0009 — Estoque básico por produto

Status: adotado em 2026-10-17 após autorização do responsável para entradas, ajustes, saldo e histórico, bloqueando saldo negativo.

## Decisão

Novo módulo inventory no monólito, com controllers/services/repositories/entities/domain/DTOs conforme ADR-0008. Sem dependências novas. Unidade inteira de produto simples: não implementar variantes, reservas, pedidos, baixa automática, vitrine ou integrações externas.

- Produto começa com saldo zero. Consulta não cria registro. Entrada exige delta positivo; ajuste usa delta inteiro diferente de zero, positivo ou negativo. Motivo obrigatório, normalizado nas extremidades, de 1 a 240 pontos Unicode sem controles/quebras de linha ou Unicode malformado.
- Saldo de 0 a 2.147.483.647 unidades (limite técnico INTEGER). Calcular soma em long antes de converter; negar saldo negativo ou overflow sem gravar efeitos parciais.
- V7 aditiva cria inventory_balance e inventory_movement; chave composta produto/tenant e referências preservam isolamento. Saldo e registro auditável são confirmados na mesma transação. Ledger guarda ator autenticado, motivo, delta, saldo posterior, data, UUID público e sequência técnica. Não há endpoints de edição/exclusão do histórico; @Immutable impede atualização JPA. Isso não equivale a imutabilidade contra um administrador do banco.
- Ator vem da sessão e acesso ao produto exige identidade ativa/verificada e membership OWNER. CatalogService.requireProductAccess participa da transação e mantém locks de autorização; IDs enviados são seletores, sem conceder acesso. Repositório escopa operações por tenant/produto. Recursos ausentes e não autorizados retornam 404 uniforme.
- Serializar escritas por requestId e produto. Advisory lock transacional por tenant/request antecede lock exclusivo do saldo; colisões do hash apenas serializam solicitações independentes, sem conferir autorização. Inicialização concorrente do saldo usa UPSERT em repositório; JPA mantém saldo e ledger.
- Toda movimentação exige requestId UUID, único por tenant. Mesmo ator/produto/tipo/delta/motivo normalizado retorna o mesmo movimento com replayed=true, sem repetir efeitos. Reutilização com outros dados retorna REQUEST_CONFLICT (409). Chave só fica registrada em operação confirmada; falhas transacionais não consomem a chave.
- Tela preserva chave e conteúdo durante resultado ambíguo e oferece repetir a mesma solicitação. Avisar antes de sair; nenhuma persistência de payload em browser storage. Se o usuário abandonar a tela, conferir saldo/histórico antes de iniciar outra operação.
- GET usa snapshot REPEATABLE_READ para saldo/histórico coerentes; história ordenada por sequência decrescente, páginas de 20 com cursor textual. POST usa locks e nível padrão. Sequência transportada como string evita perda de precisão no browser.

## Contrato

GET /api/tenants/{tenantId}/products/{productId}/inventory?before={sequence} retorna quantity/movements/nextCursor. Movimento contém id/type/delta/balanceAfter/reason/createdAt/sequence. POST recebe requestId/type/delta/reason e retorna movement/replayed: 201 novo, 200 repetição. INSUFFICIENT_STOCK, BALANCE_LIMIT e REQUEST_CONFLICT retornam 409; dados inválidos 400, ausência de sessão 401, CSRF/origem inválidos 403 e persistência indisponível 503 sanitizado.

Frontend usa unidades inteiras, confirma o resultado antes de permitir nova operação e atualiza saldo separadamente. Falha da consulta após POST confirmado não reenviará automaticamente a movimentação. Datas exibidas com fuso America/Sao_Paulo; dados persistidos com instante UTC.

## Aceite e limites

Entrar, abrir estoque do produto, registrar entrada e ajuste com motivo, conferir histórico e saldo após reload. Testes devem comprovar saldo não negativo na concorrência, idempotência, rejeição de payload alterado, isolamento A/B, sessão/CSRF/origem e integridade PostgreSQL. Resultados reais ficam no registro de validação da entrega.

Reservas e seu prazo exigem decisão antes do checkout; não estão antecipadas. Sem mudança no design system, autenticação, dependências ou SEC-003. Nenhuma autorização de commit/push ou deploy é inferida da implementação desta etapa.
