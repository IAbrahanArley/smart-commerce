# SC-049 — Funcionário da loja (`STAFF`)

Data: 2026-11-01. Primeira tarefa da Sprint 7 ([SPRINT_07.md](../product/SPRINT_07.md)). Decisão C1 e implementação no [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md); limite por plano de 1, 3 e 10 funcionários decidido pelo responsável na mesma data.

## Critérios de aceite

- O STAFF recebe erro em todo endpoint de OWNER.
- O funcionário revogado perde o acesso na próxima requisição.
- O convite não funciona para outra conta, nem duas vezes, nem depois de vencido ou cancelado.
- O limite do plano vale por dono, uma vez por pessoa, também no aceite.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `StaffIT` (Postgres real) | 5 aprovados, com cinco cenários: (1) convite, aceite, papel `STAFF` na lista e revogação imediata; (2) conta errada, vencido, substituído, cancelado e token malformado; (3) o dono não convida a si mesmo, e-mail inválido e equipe de outra loja inacessível; (4) limite do teste grátis, mesma pessoa em duas lojas e descida de plano; (5) aceite recusado depois de uma descida e limite de envio por destinatário |
| Matriz de papéis (`StaffIT`) | percorre todas as rotas mapeadas sob `/api/tenants/{tenantId}` (mais de 40) com um STAFF: nenhuma responde com sucesso. Só o `GET /api/tenants/{tenantId}` é excluído, porque mostra a empresa ao funcionário de propósito. O 503 é aceito só para a integração do Mercado Pago desligada no teste, que responde 503 a qualquer pessoa |
| `TenantIsolationIT`, `BillingIT`, `ArchitectureTest`, `BrevoVerificationMailTest` | aprovados |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração) | aprovado: 118 unitários e 207 de integração, 0 falhas (1 ignorado, já existente). Os `ERROR` do agendador do Spring Session ao derrubar o banco descartável são o ruído conhecido de [setup.md](../setup.md) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados; novo `smoke-staff.mjs` (leitores, token do fragmento, mensagens, navegação do STAFF, aviso de cobrança) |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório):
- **Funcionários (dono):** limite "2 de 3", equipe e convites. E-mail vazio mostra erro no campo com foco nele; convite enviado limpa o campo; o quarto convite mostra o limite do plano como alerta com foco.
- **Loja do funcionário:** só "Visão geral" no menu, a página "Em breve para a equipe" e nenhum aviso de cobrança.
- **Minhas lojas:** selo "Proprietário" e "Funcionário".
- **Convite:** o token sai da barra de endereço, o aceite exige clique e, depois, aparece o link para a loja.

## Achados e disposição

| Achado | Disposição |
| --- | --- |
| A contagem do limite era lida antes da trava do dono (dois convites ao mesmo tempo podiam passar) | **Corrigido:** a contagem é lida depois da trava |
| Convidar alguém que já conta em outra loja do mesmo dono ocupava vaga nova | **Corrigido** e testado |
| `/convite` em desenvolvimento: o efeito rodava duas vezes e apagava o token antes da leitura | **Corrigido:** leitura única |
| Os cards de planos anunciariam funcionário antes de o caixa existir | **Revertido:** o anúncio entra com a SC-052 |
| Revisão (média): convite aberto passava do limite depois de uma descida ou do fim do teste | **Corrigido:** o aceite confere o limite e a situação do dono |
| Revisão (média): envio de convites sem limite, com o nome da loja no assunto | **Corrigido:** 30 por dono e 5 por destinatário a cada 24 horas; nome fora do assunto |
| Revisão (baixa): convites simultâneos ao mesmo e-mail davam 500 | **Corrigido:** 409 |
| Revisão (baixa): e-mail enviado dentro da transação; retenção do e-mail; maiúsculas na parte local; prévia de descida sem aviso de funcionários | **Registrados** como limitações no ADR-0019 |

## Limitações

- **Brevo real:** o e-mail de convite não foi enviado de verdade. O formato segue o dos outros e-mails transacionais, e o envio fica para o ambiente hospedado.
- **Retorno depois do login:** quem abre o convite sem sessão precisa entrar e abrir o link de novo, porque o login ainda não volta para a página de origem.
- **Leitor de tela, Axe e Lighthouse:** não executados, como nas revisões anteriores.
