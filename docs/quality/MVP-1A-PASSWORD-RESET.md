# Bloco 1a do MVP — Recuperação de senha: validação

Data: 2026-10-26. Base: [RELEASE_PLAN.md](../product/RELEASE_PLAN.md) (bloco 1a), [ADR-0005](../architecture/adr/0005-identity-authentication.md) e o padrão de prova de e-mail já existente (V3).

## Comportamento

**Pedido** (`POST /api/auth/password-reset/request`, `{ email }`, com CSRF):
- **Resposta:** sempre 202 com a mesma mensagem ("Se houver uma conta ativa com este e-mail, enviaremos um link…"), para endereço desconhecido, conta pendente, e-mail inválido, limite atingido ou falha de entrega. Nada revela se a conta existe.
- **Quem recebe o link:** só uma conta **ativa, verificada e com senha**.
- **O link:** `{origem do admin}/redefinir-senha#token=…`, com 32 bytes aleatórios. O token vai no fragmento da URL e não chega a servidores nem a logs.
- **No banco:** fica só o SHA-256 do token (tabela `identity_password_reset`, V28). O link vale **30 minutos**.
- **Limites:**
  - no máximo 1 link por minuto por conta;
  - 3 pedidos por hora por endereço;
  - 10 pedidos por hora por IP;
  - teto global.

  Os limites usam os mesmos contadores pseudonimizados da identidade.
- **Falha de entrega:** desfaz a troca e mantém o link anterior, se houver.

**Confirmação** (`POST /api/auth/password-reset/confirmation`, `{ token, password }`, com CSRF):
- **Senha:** segue a mesma política da ativação (15 a 128 caracteres, NFC, senhas comuns recusadas). Uma senha recusada **não consome** o link.
- **Uso único:** sob o lock do usuário, o link é consumido uma vez, a credencial é trocada e os outros links do usuário são apagados.
- **Sessões:** o `auth_version` muda, então **todas as sessões abertas terminam**.
- **Link inválido, vencido ou já usado:** 400 com "Peça um novo link". Limite por IP e global.

**E-mail:** a mensagem tem assunto e texto próprios ("Redefina sua senha"), tanto no Brevo quanto na caixa local de desenvolvimento. O destinatário e o link nunca vão para log.

**Admin:**
- "Esqueci minha senha" na tela de entrada leva a `/esqueci-senha`.
- `/redefinir-senha` lê o token do fragmento e o tira da URL na hora, como `/confirmar-email` já fazia.
- **Na página de redefinição:**
  - nova senha e confirmação, com validação no campo e foco;
  - mensagem de sucesso que avisa do encerramento das sessões;
  - link "Pedir um novo link".

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `PasswordResetIT.resetReplacesThePasswordOnceAndEndsEverySession` | <ul><li>a resposta é genérica</li><li>o link tem o token no fragmento e o banco guarda só o digest</li><li>senha fora da política dá 400 sem consumir o link</li><li>a troca dá 204, a sessão antiga é derrubada e a senha antiga para de valer</li><li>o link não é reutilizável, a nova senha entra e os links são apagados</li></ul> |
| `PasswordResetIT.requestsNeverRevealWhetherAnAccountExists` | <ul><li>endereço desconhecido, conta pendente, e-mail inválido e vazio: mesma resposta e nenhum e-mail</li><li>falha de entrega: mesma resposta e nada gravado</li><li>um link por minuto</li></ul> |
| `PasswordResetIT.invalidExpiredOrUnprotectedConfirmationsAreRefused` | <ul><li>sem CSRF, 403 nas duas rotas</li><li>token malformado ou inexistente, 400</li><li>link vencido, 400 com "Peça um novo link", e a senha antiga continua valendo</li></ul> |
| `IdentityAuthenticationIT` (existente) | sem regressão |
| `mvnw clean verify` (2026-10-26) | 92 unitários e 159 de integração, todos passando. O 1 ignorado é a avaliação paga da SC-029, que é opcional |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre `/esqueci-senha`, `/redefinir-senha` e o link na tela de entrada |
| Navegador (backend sintético) | <ul><li>o link aparece em "Entrar"</li><li>o pedido mostra a mensagem genérica com foco</li><li>o token sai da URL ao abrir a página</li><li>senhas diferentes são apontadas no campo</li><li>salvar mostra o sucesso</li><li>as requisições têm o formato esperado (token de 43 caracteres)</li></ul> |

## Observações

- **Maiúsculas no endereço:** a parte do endereço antes do @ diferencia maiúsculas, como já acontece no login. Só o domínio é normalizado. Um pedido com outra grafia antes do @ não encontra a conta e recebe a mesma resposta genérica.
- **Produção:** usa o Brevo já configurado (`IDENTITY_MAIL_*`). Não há variável nova.
