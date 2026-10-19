# Validação local — SC-010 Expiração, cancelamento e pedidos no admin

Data: 2026-10-18. Branch `feat/sc-010-orders-admin`, a partir da `main` em `3b442ca` (SC-008 e SC-009 integradas). Escopo: [SPRINT_03.md](../product/SPRINT_03.md) e [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md), com as decisões da SC-010 aprovadas em 2026-10-18:
- rotina a cada 1 minuto;
- anonimização completa após 90 dias;
- motivo de cancelamento obrigatório.

## Entrega

### Backend

**V15, aditiva**
- Novas colunas `expired_at`, `cancelled_at`, `cancelled_by`, `cancel_reason` e `anonymized_at`.
- Os campos do comprador aceitam nulo somente em pedido anonimizado.
- CHECKs de consistência para cancelamento, anonimização e endereço.
- Índices para os dois lotes e para a lista filtrada.
- Nenhum dado é alterado pela migração.

**Rotinas (`OrderMaintenanceJob`)**
- Expiração a cada minuto, em lotes de 100 com `FOR UPDATE SKIP LOCKED` e troca de estado condicional. Fecha as reservas com `InventoryReservationService.expireOrder`, que não trava o estoque porque reserva vencida já não conta no disponível. Assim a rotina nunca trava linhas de estoque fora de ordem.
- Anonimização diária D2, que remove nome, e-mail, chave, telefone e endereço e substitui a impressão digital.
- Teto de 20 lotes por execução. Falhas são registradas só com o tipo do erro, sem dados.
- `ORDERS_MAINTENANCE_ENABLED=false` desliga as rotinas.

**API do OWNER**
- `GET /api/tenants/{id}/orders?status=&after=`: situação efetiva, número decrescente, 20 por página.
- `GET .../{orderId}`: itens com SKU e custo do snapshot, valores, comprador, endereço, cancelamento e `anonymizedAt`.
- `POST .../{orderId}/cancel` com `{reason}`.
- Todas as queries juntam o vínculo OWNER e uma identidade ativa e verificada.
- Respostas `no-store`. 404 uniforme para outra loja ou falta de vínculo, e 409 `ORDER_EXPIRED` para pedido vencido.
- O cancelamento de um pedido já cancelado devolve o mesmo resultado, sem novo efeito.
- As rotas foram incluídas na lista explícita da cadeia de segurança. O POST exige CSRF.

### Admin-web

- Item "Pedidos" no menu da loja.
- `/lojas/{id}/pedidos`:
  - filtro por situação num formulário GET (funciona sem JavaScript), com filtro e página na URL (`?situacao=`, `?apos=`), validados antes de chegar à API;
  - tabela de 5 colunas, empilhada abaixo de 64rem.
- `/lojas/{id}/pedidos/{orderId}`:
  - situação e prazo em America/Sao_Paulo, itens, valores, comprador com links `mailto`/`tel` e endereço formatado;
  - dados removidos indicados com a data;
  - cancelamento em duas etapas, com motivo validado como no backend, foco no erro e mensagem de resultado focável.
- Botões nas variantes destrutiva e secundária previstas no DESIGN.md, sem nova cor.

## Verificações executadas

**Backend** (Corretto 21.0.9, PostgreSQL 18.6 em Testcontainers)
- `mvnw.cmd -B -ntp verify -Dspring-boot.repackage.skip=true`: BUILD SUCCESS. **157 testes** (64 unitários/arquiteturais e 93 de integração), zero falhas, erros ou ignorados. ArchUnit e Spotless limpos.
- Log local: `backend/target/sc010-full-verify.log`.

**Novos testes**
- `OrderLifecycleIT`, com 9 cenários e a rotina desligada para que o teste conduza os lotes:
  1. listar, ler e cancelar (motivo inválido rejeitado com 400; estoque liberado uma vez; repetição sem efeito; comprador vê `CANCELLED`);
  2. pedido vencido não cancela, e a rotina o grava uma vez;
  3. quatro rotinas concorrentes dividem 30 pedidos sem duplicar;
  4. corrida entre cancelamento e rotina;
  5. corrida entre cancelamento e checkout real pelas unidades liberadas, sem venda acima do estoque;
  6. isolamento A/B (lista, leitura e cancelamento de B sobre A dão 404 com corpo igual ao de inexistente; sem CSRF dá 403; anônimo dá 401; filtro e cursor inválidos dão 400; vínculo removido dá 404);
  7. filtros por situação efetiva e cursor sem repetição;
  8. anonimização só de pedidos fechados com mais de 90 dias, preservando valores e itens;
  9. CHECKs da V15 via SQL direto.
- `OrderAdminDomainTest`: filtro, cursor e motivo.

**Admin-web**
- `npm run lint`, `typecheck`, `build` e `SMOKE_PORT=3052 npm run test:smoke` aprovados.
- O smoke ganhou testes puros (situação, cursor, caminhos, motivo, data, telefone, CEP) e as duas rotas novas.
- As portas padrão estavam ocupadas por servidores locais.

## CI remota: teste instável corrigido

A primeira execução do PR #11 falhou no job `backend` em um único teste, que já existia desde a SC-008: `OrderCheckoutIT.attemptsPerSignedClientIpAreLimitedAndUnsignedHeadersAreIgnored`.

- A assertiva exigia que nenhuma chave de `order_rate_limit` contivesse as substrings `"203"` ou `"198"`. Só que as chaves são HMAC-SHA256 em hexadecimal, com chave aleatória a cada inicialização.
- Uma simulação com 100 mil rodadas de 3 chaves aleatórias falhou em 8,7% das vezes. Por isso o teste passou localmente e na CI do #8.
- A assertiva agora verifica a propriedade real: toda chave tem exatamente 64 caracteres hexadecimais (o que exclui os pontos de qualquer IP) e não contém os IPs literais.
- O código de produção não mudou.
- `verify` completo novamente: BUILD SUCCESS, 157 testes, zero falhas.

## Validação em navegador (painel integrado)

**Ambiente descartável**
- PostgreSQL em contêiner (porta 55433, senha aleatória fora do repositório).
- Backend da branch no perfil `dev` na porta 8081, com a V15 aplicada.
- Admin `next dev` na porta 3010. O destino da API foi trocado temporariamente para 8081 porque o 8080 estava em uso pelo backend local do usuário. A troca foi revertida antes do commit (`next.config.ts` sem diff).
- Conta sintética criada pelo fluxo real: cadastro, e-mail da caixa DEV, ativação e login. Loja criada pela API, catálogo e entrega preenchidos via SQL e três pedidos criados pelo checkout público real.
- Tudo foi encerrado ao final: contêiner, processos e arquivos de senha.

**Resultados**
- **Lista:** três pedidos com situação efetiva, comprador, entrega e total. Filtros "Cancelado" e "Aguardando pagamento" pela URL, com o select refletindo a escolha.
- **Rotina agendada:** o pedido nº 3, com prazo vencido via SQL, foi gravado sozinho como `EXPIRED`, com `expired_at` preenchido e a reserva `EXPIRED`. O disponível voltou de 4 para 7.
- **Cancelamento:**
  - motivo "ab" é rejeitado com mensagem associada, `aria-invalid` e foco no campo;
  - um motivo válido cancela o pedido, mostra a mensagem focada e mostra data e motivo;
  - no banco: `CANCELLED`, `cancelled_by` igual ao lojista e reserva `RELEASED`. O disponível foi a 9.
- **Larguras sem rolagem horizontal:** 375, 768, 800, 1024, 1100 e 1500 px.

**Ajustes feitos durante a validação**
- Tabela da lista: de 7 para 5 colunas (data na célula do pedido, unidades junto da entrega) e layout empilhado abaixo de 64rem. Antes, a página rolava na horizontal entre 800 e 1100 px e as palavras quebravam no meio.
- Detalhe: duas colunas só a partir de 90rem. A 1024 px o painel lateral se sobrepunha à tabela de itens.

## Revisão de UI ([web-design-guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md), regras obtidas em 2026-10-18)

**Atendido**
- Rótulos clicáveis; `autocomplete="off"` no motivo.
- Foco no primeiro erro e na mensagem de resultado.
- Ação destrutiva com confirmação em duas etapas.
- Estado da lista na URL; links reais.
- Números tabulares nos valores; datas e moeda com `Intl`.
- `translate="no"` no SKU; reticências tipográficas nos estados de carregamento.
- Controles de pelo menos 44px.

**Disposições**
- "Title Case" não se aplica: caixa de frase em português ([DESIGN.md](../design/DESIGN.md)).
- O filtro exige o botão "Filtrar pedidos" em vez de navegar ao trocar o select. Assim funciona sem JavaScript e evita navegação inesperada.
- Sem aviso ao sair com um motivo não enviado: o formulário tem um campo só.

## Isolamento multi-tenant e privacidade

- O tenant da rota só vale pelo vínculo OWNER juntado na própria query. Ler, cancelar ou listar pedidos de outra loja dá 404 com corpo idêntico ao de inexistente, e o pedido de A pedido pelo caminho de B também dá 404.
- As rotinas processam todos os tenants, mas cada escrita usa o tenant da própria linha.
- O lojista vê dados pessoais só dos pedidos da própria loja, com `Cache-Control: no-store`. O motivo do cancelamento não aparece ao comprador.
- Logs: as rotinas registram só o tipo do erro, e nenhuma resposta ecoa dados do comprador em erro.
- Anonimização: irreversível por decisão aprovada (D2). Mantém valores, itens, datas e texto da entrega da loja.

## Limitações e pendências

- **Várias instâncias:** as rotinas rodam no processo do backend; com várias instâncias, todas executam, e o `SKIP LOCKED` divide o trabalho.
- **Sem notificação:** não há aviso ao comprador sobre cancelamento ou expiração, porque não existe e-mail ao comprador nesta sprint.
- **Página do comprador:** continua dependendo da loja publicada (limitação da SC-009, mantida).
- **Testes de interface:** sem Axe nem leitor de tela automatizados. WCAG 2.2 AA é objetivo, não conformidade comprovada.
- **Próximos passos:** a FK `NOT VALID` da reserva e a revisão integrada de segurança, LGPD e QA ficam para a SC-011.
- **CI remota:** depende do push desta branch, que aguarda autorização.
