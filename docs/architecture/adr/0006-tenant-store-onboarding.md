# ADR-0006 — Empresa e loja do proprietário

Status: adotado no escopo da SC-003 autorizado pelo responsável em 2026-10-17.

## Decisão

- Manter o monólito modular; novo pacote tenant, Spring MVC/serviço/JDBC e PostgreSQL existente. Sem nova dependência.
- Criar empresa, loja e membership OWNER em uma transação. UUIDs e papel são definidos pelo backend; usuário vem exclusivamente da identidade autenticada.
- Uma loja por empresa no MVP, garantida por unicidade de store.tenant_id. Não há restrição comercial nova de empresas por proprietário; consulta lista somente seus vínculos. Sem seletor global de tenant em sessão.
- Nome da empresa e nome da loja: texto obrigatório até120 caracteres, sem controles. São nomes informados pelo lojista, sem cadastro fiscal/CNPJ ou comprovação jurídica nesta entrega.
- Slug global único:3–63 caracteres ASCII, letras minúsculas/números separados por hífens simples. Normalizar caixa; rejeitar formato inválido. Unicidade garantida pelo banco, inclusive na concorrência. Conflito409 não deixa empresa/membership órfãos.
- Loja criada DRAFT, apresentada como “Em configuração”. Não existe publicação de vitrine, catálogo, pagamentos ou métricas de negócio nesta tarefa.

## Autorização e contrato

POST /api/tenants recebe companyName/storeName/slug. GET /api/tenants lista os vínculos próprios. GET /api/tenants/{id} verifica membership do ator em consulta escopada; ID/slug/header/payload do cliente não concedem autorização. Tentativa A→B e recurso inexistente retornam404 uniforme. API exige sessão válida; mutações exigem CSRF, origem permitida e corpo limitado.

Identidade permanece global; não gravar empresa/role fornecidos pelo cliente na autenticação. Nesta entrega só existe OWNER criado pelo servidor, sem convites, funcionários, edição, exclusão ou endpoints de gestão de permissões. RLS e observabilidade ampliada ficam para avaliação específica; nenhuma garantia sobre módulos futuros é inferida dos testes atuais.

## Aceite

Proprietário autenticado cria e visualiza sua empresa/loja no admin. Dois lojistas veem somente seus próprios registros. Cadastro inválido, duplicidade/concorrência de slug, ausência de sessão, CSRF e manipulação de IDs são cobertos por testes relevantes. Migração V5 aditiva, V1–V4 preservadas. SEC-003 inalterada e sem aceite de risco.
