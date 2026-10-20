# SC-012 — Conexão da conta Mercado Pago: validação

Data: 2026-10-20. Base: [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md) (P1, P3, P10) e [SPRINT_04.md](../product/SPRINT_04.md).

**Estado:** implementada e validada com um **servidor OAuth falso** em loopback. A conexão com uma conta de teste real do Mercado Pago ainda não foi feita: ela depende da aplicação e dos usuários de teste que o responsável está criando.

## O que foi entregue

**Módulo `payments` no backend:**
- **Migração `V17__payment_accounts.sql`** (aditiva), com duas tabelas:
  - `payment_oauth_state`: tentativas pendentes. O `state` é guardado só como SHA-256 e o verificador PKCE vai cifrado.
  - `payment_account`: uma conta por loja. Os tokens ficam cifrados e só existem enquanto a conta está `CONNECTED`; as restrições `CHECK` garantem isso.
- **Cifra (`TokenCipher`):** AES-256-GCM, com nonce aleatório de 96 bits por cifragem, versão de chave e dados associados `smart-commerce:<propósito>:<tenant>`. Um texto cifrado copiado para outra loja ou coluna não decifra.
- **OAuth (`MercadoPagoOAuthClient`):**
  - URL de autorização com `client_id`, `response_type=code`, `platform_id=mp`, `state`, `redirect_uri`, `code_challenge` e `code_challenge_method=S256`;
  - troca e renovação em `POST /oauth/token`, com `code_verifier` e `test_token=true` (P10);
  - sem seguir redirecionamentos, com timeout de 10 s e validação estrita da resposta.
- **Serviço (`PaymentConnectionService`):**
  - **Início:** só o OWNER inicia, e uma tentativa nova substitui a anterior.
  - **Retorno:**
    1. o `state` é consumido numa transação própria (uso único);
    2. a troca do código roda fora de lock;
    3. o vínculo ao dono da loja é conferido de novo antes de gravar.
  - **Renovação:** sob lock da linha, com o refresh token rotacionado gravado na mesma transação. `invalid_grant` passa a conta para `REVOKED` e apaga os tokens.
  - **Desconexão:** apaga os tokens.
  - **Uso interno:** `accessToken(tenant)` é o ponto de uso para a SC-013.
- **Rotina diária (`PaymentTokenRefreshJob`):** renova tokens a menos de 30 dias do vencimento.
- **Rotas:**
  - `GET` e `DELETE /api/tenants/{id}/payments/mercado-pago`, e `POST …/authorization`, com sessão e CSRF nas mutações;
  - `GET /api/payments/mercado-pago/callback` (sessão), que sempre redireciona com `303` para uma página fixa do admin e leva só uma palavra de resultado.

**Admin:**
- página **Pagamentos** (`/lojas/{id}/pagamentos`) e item no menu da loja;
- estado da conexão, exibindo a conta só pelo id numérico;
- "Conectar Mercado Pago", que só navega para `https://auth.mercadopago.com/authorization`;
- "Desconectar", com diálogo de confirmação;
- mensagens de retorno (conectado, negado, expirado, indisponível, erro), mostradas uma vez.

## Variáveis de ambiente (nenhum valor versionado)

| Variável | Uso |
| --- | --- |
| `MERCADO_PAGO_CLIENT_ID` | APP_ID numérico da aplicação. **Liga a integração**; sem ele, a página mostra "Indisponível neste ambiente" |
| `MERCADO_PAGO_CLIENT_SECRET` | segredo da aplicação |
| `MERCADO_PAGO_REDIRECT_URI` | origem do admin + `/api/payments/mercado-pago/callback`, idêntica à URL de redirecionamento cadastrada na aplicação |
| `PAYMENTS_TOKEN_ENCRYPTION_KEY` | 32 bytes aleatórios em Base64 |
| `MERCADO_PAGO_TEST_TOKEN` | `true` por padrão (sandbox, P10) |

Para gerar a chave de cifra:

```bash
python -c "import base64, secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

Com o `client_id` definido, o backend recusa iniciar se o segredo, a URL de retorno (HTTPS, ou HTTP só em loopback) ou a chave estiverem ausentes ou inválidos.

## Testes

| Teste | Cobertura |
| --- | --- |
| `TokenCipherTest` (4) | <ul><li>nonce único em 200 cifragens, sem texto claro no resultado</li><li>texto cifrado ligado a tenant, propósito, chave e integridade</li><li>validação da chave</li><li>PKCE pelo vetor da RFC 7636 (apêndice B)</li><li>formato de `state` e verificador</li></ul> |
| `PaymentConnectionIT` (7), com PostgreSQL via Testcontainers e servidor OAuth falso | <ul><li>**conexão:** parâmetros da URL; o `code_verifier` enviado corresponde ao `code_challenge`; `client_secret`, `redirect_uri` e `test_token`; tokens cifrados no banco; resposta sem token ou segredo; logs sem token, código, segredo, `state` ou verificador</li><li>**`state` adulterado, malformado, reutilizado, expirado, substituído ou usado pela sessão de outro dono:** nunca chega ao provedor, e a tentativa alheia não consome o `state`</li><li>**falhas de troca:** autorização negada e quatro falhas do provedor (`invalid_grant`, 500, resposta incompleta, JSON inválido), sem conectar</li><li>**isolamento (A/B):** o dono B recebe 404 ao ler, iniciar e desconectar a loja de A; sem CSRF a resposta é 403, e sem sessão, 401</li><li>**desconexão:** apaga os tokens e permite reconectar</li><li>**renovação:** rotação do refresh token; renovação em lote; falha do provedor mantém a conexão; `invalid_grant` vira `REVOKED` e apaga os tokens</li><li>**token vencido:** aparece como `EXPIRED`</li></ul> |
| `ArchitectureTest`, `BootstrapSecurityTest` | camadas e cadeia de segurança com as rotas novas |
| Smoke do admin | <ul><li>destino de navegação aceito só para a página HTTPS de autorização do Mercado Pago (rejeita HTTP, domínio parecido, credenciais na URL, outro caminho e `javascript:`)</li><li>análise da resposta, que descarta campos desconhecidos como um token</li><li>mensagens de retorno</li><li>rota `/lojas/{id}/pagamentos`</li></ul> |

**Comandos executados:**
- `mvnw verify` no backend completo: 78 testes unitários e 102 de integração, todos passando;
- no admin: `lint`, `typecheck`, `build` e `test:smoke`.

**Verificação no navegador**, com o admin em desenvolvimento e uma API sintética local (sem backend real nem Mercado Pago):
- estado conectado, com a conta exibida pelo id e as datas;
- desconexão pelo diálogo, que abre com o foco em "Cancelar" e, ao confirmar, leva a "Não conectada" com o botão "Conectar Mercado Pago";
- mensagens de retorno exibidas, e o parâmetro `mercadoPago` removido da URL logo depois.

**Não verificado:** o foco programático no aviso de retorno. O documento do painel de testes não recebe foco.

## Revisão de segurança (multi-tenant)

- **Tenant:** a loja vem da rota, mas só vale pelo vínculo OWNER ativo e verificado; qualquer outra situação recebe o mesmo 404. No retorno, a loja vem do `state` guardado, nunca da requisição, e o vínculo é conferido de novo antes de gravar.
- **CSRF e login CSRF:**
  - as mutações exigem o token CSRF;
  - o retorno é um GET de navegação, protegido pelo `state` de uso único ligado ao usuário da sessão;
  - um `state` iniciado por outra conta não conecta nada, o que impede ligar a conta Mercado Pago de um atacante à loja da vítima.
- **Redirecionamento aberto:** o destino do 303 é montado a partir de `IDENTITY_PUBLIC_ORIGIN` e do tenant guardado. O admin só navega para `auth.mercadopago.com` por HTTPS.
- **Segredos:** os tokens ficam cifrados e ligados ao tenant; o `state` fica com hash; o verificador vai cifrado. `toString` é redigido nos records sensíveis, e as mensagens de erro são códigos. Os testes confirmam que os logs ficam limpos.
- **Concorrência na renovação:** o lock da linha serializa as renovações, então o refresh token rotacionado nunca se perde nem é usado em duplicidade.

## Desvios e limites

- **`state` "assinado" (ADR-0013 P3):** a implementação usa um valor aleatório de 256 bits guardado no servidor (só o hash), de uso único, com validade de 10 min e ligado a usuário e loja. Isso cumpre o objetivo da assinatura sem expor uma chave a mais. O ADR registra essa equivalência.
- **"Callback sem PKCE":**
  - o backend sempre envia o `code_verifier`, e o teste prova que ele corresponde ao `challenge` enviado;
  - a recusa de um código sem PKCE é feita pelo Mercado Pago quando o PKCE está ativo na aplicação;
  - um retorno sem `code` ou sem `state` é recusado aqui.
- **Desconectar** apaga os tokens localmente. A documentação consultada não descreve um endpoint de revogação; o vendedor pode retirar a autorização no painel dele, e a próxima renovação marca `REVOKED`.
- **Retorno sem sessão:** se a sessão do admin expirou enquanto o vendedor estava no Mercado Pago, o retorno responde 401 (o `state` não é consumido). A tentativa precisa ser refeita depois de entrar.
- **`live_mode`:** é guardado, mas não bloqueia nada, porque não se sabe como o Mercado Pago o informa para usuários de teste. Isso deve ser conferido na primeira conexão real em sandbox.
- **Ambiente de produção:** continua exigindo aprovação separada (P10, D6).

## Próximo passo

O responsável cria a aplicação, com PKCE ativo, e os usuários de teste. Depois cadastra a URL de retorno e define as variáveis acima, localmente ou no Render. Feito isso, valida a conexão com a conta de teste do vendedor e registra o resultado aqui.
