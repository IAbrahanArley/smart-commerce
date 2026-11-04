# ADR-0007 — Catálogo básico

Status: adotado no escopo autorizado pelo responsável em 2026-10-17, após a criação de empresa/loja. Esta entrega posterior não altera o escopo histórico da Sprint 1.

## Decisão

Módulo catalog no monólito existente, com Spring MVC, serviço transacional e JDBC. Produto simples com nome, SKU, preço e custo opcional; sem variantes, categorias, imagens, estoque, exclusão ou publicação de vitrine. Nenhuma dependência nova.

- Nome obrigatório, até 120 pontos Unicode; rejeitar controles e Unicode inválido.
- SKU de 1–64 caracteres ASCII: letras, números, ponto, hífen, barra e sublinhado. Remover espaços nas extremidades, preservar caixa; comparação sensível a maiúsculas/minúsculas (COLLATE C). Unicidade por tenant, garantida pelo PostgreSQL inclusive na concorrência. Uma loja por tenant conforme ADR-0006.
- BRL explícito. Preço estritamente maior que zero, conforme aprovação humana; custo ausente é NULL, distinto de zero. Custo informado não negativo. Até 17 dígitos inteiros e duas casas decimais.
- API recebe e devolve dinheiro como strings decimais, sem expoente, sinal ou separador de milhares; Java usa BigDecimal. UI aceita vírgula ou ponto decimal e converte textualmente, sem float ou arredondamento.
- V6 usa NUMERIC sem typmod com CHECK de faixa e escala: NUMERIC(19,2) arredondaria entradas SQL excedentes antes de validar. A migração rejeita essas entradas. Saída textual sempre com duas casas.
- V6 cria catalog_product e chave composta store(id, tenant_id). FK composta impede associar produto à loja de outro tenant. Migrações anteriores preservadas.
- ID do ator deriva da sessão. Todas as consultas exigem vínculo OWNER e identidade ativa/verificada; ID de tenant recebido é apenas seletor. Serviço mantém locks compartilhados sobre vínculo/identidade durante a transação. Consultas SQL escopadas reforçam a autorização, sem introduzir abstrações de módulos futuros.
- Atualização exige versão inteira; compare-and-swap retorna VERSION_CONFLICT (409) se desatualizada. SKU duplicado retorna SKU_CONFLICT (409). Recursos inexistentes e não autorizados retornam 404 uniforme. IDs, moeda e vínculo são definidos pelo servidor.

## Contrato e limites

GET/POST /api/tenants/{tenantId}/products e GET/PUT /api/tenants/{tenantId}/products/{productId}. POST recebe name/sku/price/cost; PUT também version. DTO: id/name/sku/price/cost/currency/version. Sessão, CSRF, origem e limite de corpo existentes continuam obrigatórios.

Listagem simples sem paginação nesta entrega; dimensionar paginação antes de volumes relevantes. **Atualização 2026-11-04:** `GET /products` passou a filtrar, ordenar, contar e paginar no banco (25 por página; `q`, `category`, `visibility`, `sort`, `page`), respondendo `{products, page, pageSize, matching, catalogTotal, publishedTotal}`; detalhes em [ADMIN-PAGINATION.md](../../quality/ADMIN-PAGINATION.md). Não inferir garantia de módulos futuros dos testes atuais. SEC-003 permanece aberta e inalterada, sem aceite de risco.
