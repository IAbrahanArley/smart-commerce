# MVP, bloco 2 — Observabilidade mínima: validação

Data: 2026-10-31. Base: item "Observabilidade mínima" do bloco 2 do [RELEASE_PLAN.md](../product/RELEASE_PLAN.md), requisito não funcional do PRD. A branch parte da SC-048.

## O que mudou

- **Id de correlação** (`config/RequestIdFilter`):
  - roda antes da cadeia de segurança, então requisições recusadas também ganham id;
  - um `X-Request-Id` recebido só é reaproveitado se tiver de 8 a 64 caracteres, entre letras, números e hífens; qualquer outro valor é trocado por um UUID, e assim o cabeçalho não injeta texto no log;
  - o id volta no cabeçalho da resposta, e o comprador ou o lojista pode citá-lo;
  - o id entra no MDC e no padrão de log (`WARN [id]`) e é removido ao fim da requisição.
- **Erro do PostgreSQL sem dados:** o driver roda com `logServerErrorDetail=false`. Antes, uma restrição violada trazia a linha inteira na mensagem da exceção ("Failing row contains (…)", com e-mail, telefone e endereço do comprador), e essa mensagem chegaria ao log se a exceção escapasse.

## Auditoria dos logs

- **Backend, 25 chamadas de log explícitas:** todas registram só tipo de erro (`getClass().getSimpleName()`), status HTTP, códigos de erro do provedor, nomes de rotina ou ids de pedido e evento.
  - Nenhuma registra token, segredo, corpo de requisição ou resposta, e-mail, telefone, endereço ou IP.
  - O Mercado Pago registra só os códigos de erro e os nomes dos campos recusados, nunca valores nem a URL completa.
- **Configuração já existente:**
  - `SqlExceptionHelper` e `orm.jdbc.error` do Hibernate desligados;
  - `server.error` sem mensagem, stacktrace nem erros de binding;
  - sem log de acesso do Tomcat.
- **Frontends:** nenhum `console.*` em `admin-web/src` e `storefront-web/src`. As mensagens de erro lançadas no servidor da vitrine são genéricas ("Não foi possível consultar a vitrine").

## Testes

| Verificação | Resultado |
| --- | --- |
| `ObservabilityIT` (2, novo) | aprovado. Cobre: <ul><li>UUID gerado em rota pública e em rota recusada pela segurança</li><li>id recebido bem formado reaproveitado</li><li>ids curto, com espaços, longo demais e com `%0d%0a` trocados</li><li>linha de log com `WARN [id]`</li><li>`INSERT` recusado pelo banco sem o dado sintético na mensagem</li></ul> |
| Backend `verify` completo | aprovado: formatação, 115 testes de unidade e 196 de integração (1 ignorado, como antes), BUILD SUCCESS |

## Limitações

- Os servidores Next (admin e vitrine) não geram nem repassam o id ao backend, então a correlação começa no backend.
- Rotinas agendadas (expiração, conciliação, revalidação) não têm id.
- Não há agregador de logs nem métricas: fica para a infraestrutura de produção (bloco 4).
