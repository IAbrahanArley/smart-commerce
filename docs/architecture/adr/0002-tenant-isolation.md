# ADR-0002 — Isolamento de tenant
Status: aceito (provisório para MVP)
Decisão: banco compartilhado, tenant_id por entidade de negócio, autorização por contexto confiável e testes negativos A/B.
Riscos: vazamento por consulta sem filtro; mitigar com abstrações, revisão e testes; avaliar RLS em decisão futura.
Não aceitar tenant_id fornecido pelo cliente como única evidência de autorização.
