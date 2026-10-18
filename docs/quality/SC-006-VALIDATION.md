# Validação local — SC-006 Configuração de entrega da loja

Data: 2026-10-17. Escopo: [SPRINT_03](../product/SPRINT_03.md) SC-006 e [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md) seção 4 (D3 aprovada: retirada + frete fixo único por loja, sem CEP). Branch `claude/sc006-fulfillment-1db145`; commit local, sem push nem CI remota.

## Entrega

- **V12 aditiva** (`store_fulfillment`): tabela 1:1 com a loja, PK `store_id` e FK composta `(store_id, tenant_id) → store(id, tenant_id)` com `ON DELETE CASCADE` (configuração pertence à loja). Campos `pickup_enabled`, `pickup_instructions` (até 500), `fixed_shipping_enabled`, `fixed_shipping_amount` (`NUMERIC` sem typmod com os CHECKs do catálogo: `>= 0`, `< 10^17`, `scale <= 2`; zero = grátis), `currency` (`BRL`), `fixed_shipping_area` (até 500), `version`, `updated_at`. CHECKs: opção ativa exige seus textos/valor. `store` e migrações anteriores não foram alteradas; lojas existentes ficam sem linha, o que equivale a tudo desativado e versão 0.
- **Admin** `GET`/`PUT /api/tenants/{tenantId}/fulfillment` (módulo tenant, camadas ADR-0008): identidade ativa/verificada e vínculo OWNER, CSRF e origem pela cadeia existente, 404 uniforme para ausência/acesso cruzado. Gravação otimista em um único comando condicional: primeira gravação `INSERT … ON CONFLICT DO NOTHING` só com versão 0; depois `UPDATE … WHERE version = ?`; versão divergente → `409 VERSION_CONFLICT`. O lock `FOR SHARE` de loja/vínculo/identidade é mantido até o fim da transação.
- **Contrato textual**: valor como string decimal (`"12.50"`), booleanos e versão com tipos JSON estritos; textos sem caracteres de controle/formatação/separadores de linha, até 500 code points após `strip`.
- **Pública** `GET /api/public/stores/{slug}/fulfillment` em classes novas do módulo storefront (`PublicFulfillment*`); `StorefrontRepository`, `StorefrontService` e as views públicas existentes não foram tocadas. A consulta só devolve valores de opções ativas (CASE no SQL) de loja `PUBLISHED`; rascunho, slug inexistente ou inválido → 404 idêntico; publicada sem configuração → `{"options":[]}`. Resposta `no-store`, sem cookies. Na cadeia pública de `BootstrapSecurityConfiguration` entrou apenas a linha desse GET; na cadeia administrativa, os matchers GET/PUT do novo caminho.
- **admin-web**: seção "Entrega" nos detalhes da loja (`store-fulfillment.tsx`), com fieldsets nativos, checkbox para ativar retirada/frete fixo, valor aceitando vírgula ou ponto pelo parser textual existente (`decimalInput`, sem float), instruções e área com label, ajuda e erro associados, foco no primeiro erro, bloqueio de envio duplo, resumo do valor salvo por `formatBRL`, recarregar e tratamento de conflito ("Carregar configuração atual", sem sobrescrever). Regras de validação em `src/lib/fulfillment.ts`. storefront-web não foi alterado.

### Hipóteses registradas

- Opção ativa exige texto: retirada sem endereço/instruções ou frete sem área não oferecem informação útil ao comprador. Opções desativadas preservam textos/valor para reativação, mas nunca são expostas publicamente.
- Textos são uma linha (sem quebras), coerente com "sem controles"; a interface orienta usar vírgulas.
- `tsconfig.json` do admin recebeu `allowImportingTsExtensions` (válido com `noEmit`) para `src/lib/fulfillment.ts` importar `./catalog.ts` e ser carregado diretamente pelo smoke em Node. Sem dependência nova.

## Testes adicionados

- `FulfillmentSettingsTest` (17 casos): zero permitido e escala exata; negativos, escala > 2, vírgula, notação científica, espaços e 10^17 rejeitados; `\n`, `\t`, NUL, U+200B e U+2028 rejeitados; limite de 500 code points (inclusive caracteres fora do BMP); opção ativa sem texto/valor rejeitada; opção inativa preserva dados.
- `FulfillmentIsolationIT` (PostgreSQL/Testcontainers, 6 casos): salvar, recarregar e ver o mesmo valor (`0` → `"0.00"`, `12.5` → `"12.50"`, conferido também no banco); conflito de versão (antiga e futura); 400 para negativos, escala 3, vírgula, controles, U+202E, 501 caracteres, valor numérico JSON, booleano textual, versão ausente/negativa e JSON malformado, sem alterar o estado salvo; CHECKs e FK composta rejeitam valor negativo, escala 3, retirada ativa sem texto e linha cruzada (loja de A com tenant B) via SQL direto; seis gravações concorrentes com a mesma versão têm exatamente um vencedor (as demais 409). **A/B:** OWNER B recebe 404 idêntico ao de tenant inexistente ao ler ou alterar A (inclusive com versão 0), sem vazar textos e sem alterar A; CSRF ausente/incorreto e origem estrangeira → 403; anônimo → 401; identidade desativada ou vínculo removido perdem acesso. **Pública:** rascunho → 404 igual ao de slug inexistente; após publicar, A mostra só suas opções ativas e B só as suas (texto de retirada inativo de B e textos de A nunca aparecem); sem configuração → lista vazia; PUT público → 403; ocultar a loja volta a 404.

Durante o desenvolvimento, o teste de concorrência revelou um índice único redundante em `tenant_id` que fazia o `ON CONFLICT (store_id)` falhar com 503; o índice foi removido da V12 (`store.tenant_id` já é único) antes da execução final.

## Verificações executadas

JAVA_HOME = `C:\Users\abrah\.jdks\corretto-21.0.9`; Docker local para Testcontainers.

| Comando | Resultado |
| --- | --- |
| `backend: mvnw.cmd -B -ntp verify` (inclui Spotless check) | BUILD SUCCESS em 2026-10-18 07:57:02 -03:00 (PostgreSQL via Testcontainers). Spotless check aprovado; 50 testes unitários/arquiteturais (Surefire) e 58 integrações (Failsafe), sem falhas, erros ou ignorados, incluindo `ArchitectureTest`, `FulfillmentSettingsTest`, `FulfillmentIsolationIT` e as suítes existentes de tenant, catálogo, estoque, vitrine e identidade. Log local ignorado: `backend/target/sc006-verify.log`. |
| `admin-web: npm ci --no-audit --no-fund` | Concluído; npm avisou scripts de instalação não aprovados (`unrs-resolver`), sem falha. |
| `admin-web: npm run lint` | Aprovado, zero avisos. |
| `admin-web: npm run typecheck` | Aprovado. |
| `admin-web: npm run build` | Aprovado (Next.js 16.4.1); rotas inalteradas. Build na própria worktree: não havia `next start` neste diretório. |
| `admin-web: SMOKE_PORT=3106 npm run test:smoke` | Aprovado, com novos asserts de `fulfillmentPayload`/`fulfillmentText`/`fulfillmentFields` (normalização `12,5` → `12.50`, `0` → `0.00`, rejeição de negativos, escala 3, milhares, quebras de linha, U+200B, 501 caracteres e texto vazio com opção ativa). Aviso benigno `MODULE_TYPELESS_PACKAGE_JSON`, já existente. |
| `git diff --check` | Sem problemas. |

Servidores locais em 8080, 3000 e 3001 não foram parados nem reiniciados; nenhum comando usou o banco de desenvolvimento.

## Revisão de segurança multi-tenant

- Tenant do caminho é apenas seletor: leitura junta loja, vínculo OWNER e identidade ativa/verificada; escrita repete a autorização com lock compartilhado até o commit e grava com `store_id` e `tenant_id` derivados da linha autorizada.
- FK composta impede configuração apontando para loja de outro tenant mesmo por SQL direto (testado).
- API pública resolve a loja pelo slug publicado no servidor e projeta somente colunas das opções ativas; nenhum ID, versão, tenant ou texto de opção desativada sai na resposta.
- Mensagens de erro genéricas; nada de dados da configuração em logs. Textos são texto puro: consumidores (vitrine/SC-007) devem renderizá-los escapados, como o React faz por padrão.

## Limitações e pendências

- A interface "Entrega" não foi exercitada em navegador contra o backend desta branch: o admin reescreve `/api` para o backend em execução na 8080 (código da `main`, sem V12), e rodar esta branch contra o banco de desenvolvimento compartilhado aplicaria a V12 fora da ordem de integração. O fluxo salvar → recarregar → mesmo valor e o conflito foram verificados pela API real (IT) e a interface por lint/typecheck/build/smoke. Verificação manual no navegador fica para o coordenador após integrar as branches.
- Axe, leitor de tela, zoom 200%/reflow 320px e WCAG completa não executados; sem declaração de conformidade. `web-design-guidelines` não consultada nesta sessão; a revisão de UI seguiu DESIGN.md em código (labels persistentes, ajuda/erro associados, alvos de 44px, foco no erro, estados de carregamento/erro/conflito).
- "Loja sem opção ativa não aceita checkout" é verificado em SC-008. ARCHITECTURE/DATA_MODEL/ADR-0012 não foram atualizados (fora do escopo desta tarefa; SC-011).
