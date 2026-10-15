# Orquestração com Codex
O agente principal coordena. Para tarefas independentes, delegar a papéis architect, backend, frontend, qa, security e reviewer quando o cliente/versão suportar subagentes. Não assumir execução paralela automática: confirmar comportamento na sessão.

## Protocolo
1. Coordenador lê escopo, cria plano e define ownership dos arquivos.
2. Arquiteto analisa riscos e ADRs.
3. Implementadores executam tarefas não conflitantes.
4. QA e segurança revisam com testes reproduzíveis.
5. Revisor consolida achados.
6. Humano aprova diffs e ações sensíveis.

## Primeiro prompt
Leia AGENTS.md, PRD.md, ARCHITECTURE.md e SPRINT_01.md. Não altere código ainda. Analise a tarefa SC-001, identifique decisões pendentes e proponha plano de execução em passos pequenos, incluindo arquivos a criar, comandos de verificação e riscos. Consulte papéis especializados se suportados. Aguarde minha aprovação antes de implementar.
