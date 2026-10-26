# Bloco 1b do MVP — Dados da loja (nome, logo e contatos): validação

Data: 2026-10-26. Base: [RELEASE_PLAN.md](../product/RELEASE_PLAN.md) (bloco 1b) e item 10 do [PRD.md](../product/PRD.md), "configurações básicas da loja: nome, logo, contatos…". A política de frete e a disponibilidade já existiam (entrega e publicação).

## Comportamento

**Admin, `/lojas/[id]/dados`** (item "Dados da loja" no menu e link no cartão da visão geral):
- **Nome da loja:** de 1 a 120 caracteres, em uma linha. O endereço (slug) não muda.
- **Contatos opcionais, públicos por escolha:**
  - e-mail;
  - WhatsApp: número brasileiro com DDD, em qualquer formato ("(11) 98888-7777", "+55 11…"), guardado em E.164 (`5511988887777`); um código de país estrangeiro explícito é recusado;
  - Instagram: usuário, com ou sem "@", ou link do perfil.

  Em branco, o contato não aparece.
- **Logo:**
  - JPEG ou PNG de até 5 MiB, com pelo menos 64 pixels por lado (logos costumam ser pequenos ou largos);
  - passa pelo mesmo normalizador das fotos de produto, que recodifica e descarta metadados;
  - prévia, troca e remoção, com confirmação.
- **Versão:** nome, contatos e logo compartilham a versão do perfil. Uma alteração feita em outra sessão nunca é sobrescrita (409 e "Carregar dados atuais").
- **Acessibilidade:** validação no campo, com foco no primeiro erro e mensagens de status com foco.

**Vitrine:**
- **Cabeçalho:** o logo aparece ao lado do nome. Ele é decorativo (`alt=""`) porque o nome continua em texto.
- **"Fale com a loja":** fica na página da loja e na página do pedido.
  - WhatsApp: link `wa.me`, abre em nova aba, com aviso para leitor de tela;
  - e-mail: link `mailto:`;
  - Instagram: link do perfil.
- **Logo pelo próprio domínio** (`/midia/lojas/{slug}/logo`), como as fotos. Sem cookies nem cache, com `nosniff`.
- **Dados recebidos da API:** a vitrine valida contatos e logo de novo e descarta o que vier fora do formato.

**API:**
- `GET`, `PUT /api/tenants/{id}/profile`;
- `POST`, `DELETE`, `GET /api/tenants/{id}/profile/logo`.

  Todas exigem sessão do OWNER, e as escritas exigem CSRF.
- **Público:** `GET /api/public/stores/{slug}` ganhou `profile`, e `GET /api/public/stores/{slug}/logo` foi criado. Os dois só funcionam com a loja publicada.
- **Upload:** o filtro de requisições, que só permitia multipart nas fotos de produto, agora permite também o logo, com as mesmas regras (cabeçalho CSRF obrigatório e limite de 6 MB).
- **Banco:** migração V29, `store_profile`. Precisa entrar depois da V28 do bloco 1a.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `StoreProfileSettingsTest` (2) | <ul><li>normalização de nome, e-mail, WhatsApp (com e sem +55) e Instagram (@ e URL)</li><li>contatos vazios viram null</li><li>recusa de nome inválido, e-mail, telefone curto ou estrangeiro e usuário inválido</li></ul> |
| `StoreProfileIT` (2) | <ul><li>OWNER edita com versão (0 → 1 → 2)</li><li>409 em versão velha</li><li>400 legível em dado inválido</li><li>a outra loja recebe 404 em leitura e escrita, sem alterar nada</li><li>CSRF obrigatório</li><li>logo inválido dá 400 e versão velha dá 409</li><li>logo 240×80 aceito, com prévia PNG para o dono e 404 para outra loja</li><li>loja em rascunho: vitrine e logo dão 404</li><li>loja publicada mostra contatos e logo com `nosniff`</li><li>remoção some da vitrine</li><li>loja sem perfil responde sem contatos</li></ul> |
| `StorefrontIsolationIT`, `ProductImagesIT`, `IdentityAuthenticationIT`, `ImageNormalizerTest` | sem regressão. A projeção pública passou a incluir `profile`, com contatos nulos por padrão |
| `mvnw clean verify` (2026-10-26) | 94 unitários e 158 de integração, todos passando. O 1 ignorado é a avaliação paga da SC-029, que é opcional |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre a formatação e a validação dos contatos, o payload, o arquivo do logo e a rota `/dados` |
| Vitrine: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre o bloco "Fale com a loja" e os links, o proxy do logo, contatos inválidos ou maliciosos descartados e a loja sem logo |
| Navegador (backend sintético) | <ul><li>validação com foco no primeiro erro</li><li>valores normalizados após salvar</li><li>envio do logo com prévia</li><li>vitrine com logo e contatos, sem rolagem horizontal</li><li>remoção com diálogo de confirmação e foco na mensagem</li></ul> |

## Observações

- Os contatos são públicos por decisão do lojista. A plataforma não guarda nenhum dado novo do comprador.
- O normalizador de imagens ganhou um parâmetro de tamanho mínimo. As fotos de produto continuam exigindo 300 pixels por lado.
