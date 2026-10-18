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

## Seleção de modelo e delegação
Antes de delegar, avalie se execução direta ou ferramenta determinística é mais simples. Delegue quando isolamento de contexto, especialização ou trabalho independente justificarem a coordenação; não paralelize etapas dependentes.

Descubra os modelos e níveis de esforço disponíveis na sessão e escolha o mais leve capaz de cumprir os critérios de aceite, preservando o modelo principal, o provedor e a autenticação já autorizados. Não configure proxy, chave de API ou cobrança nova para delegar.

| Tarefa | Capacidade |
| --- | --- |
| Inventário, buscas localizadas, leitura de logs, links e mudanças mecânicas pequenas | Leve, com saída verificável |
| Implementação delimitada e revisão de comportamento | Suficiente para raciocinar sobre contratos e rodar checks |
| Arquitetura, segurança, tenant, dados pessoais, migrações, concorrência, pagamentos ou diagnóstico ambíguo | Maior desde o início; revisão crítica nunca vai para modelo inadequado por ser barato |

No Claude Code, informe modelo e esforço no próprio controle de subagentes; no Codex, use os papéis de `.codex/agents/`, que herdam o modelo da sessão. Se a qualidade for insuficiente, aumente capacidade/contexto em vez de repetir tentativas idênticas, com limite finito de tentativas.

Cada tarefa recebe objetivo, contexto mínimo, arquivos permitidos, dependências, recursos mutáveis, critérios de aceite, checks e formato de retorno (arquivos afetados, evidência, dúvidas, limitações). Portas, diretórios de build e bancos descartáveis compartilhados são isolados ou serializados. O coordenador confere diff e checks, confirma que o agente parou antes de assumir seus arquivos e registra o modelo/esforço reais quando observáveis, sem estimar economia.
