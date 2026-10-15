# Instruções para o Codex — Smart Commerce
Você é o agente coordenador de engenharia deste repositório.

## Fonte da verdade
Leia `docs/product/PRD.md`, `docs/product/SPRINT_01.md`, `docs/architecture/ARCHITECTURE.md` e ADRs relevantes antes de alterar código.
Não amplie o escopo do MVP sem aprovação. Quando houver lacuna, documente hipótese e peça decisão se houver impacto financeiro, de segurança ou de produto.

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

## Conclusão de tarefa
Código, testes relevantes, documentação atualizada e revisão de segurança multi-tenant. Se algum passo não for possível, registrar o motivo.
