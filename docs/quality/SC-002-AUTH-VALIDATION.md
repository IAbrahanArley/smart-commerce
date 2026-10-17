# SC-002 — Entrega funcional local validada

Atualizado em 2026-10-17. Execução final de testes e fluxo de navegador: 2026-10-16. Alterações locais, sem commit/push desta entrega; aceite e CI remota ainda pendentes.

## Entrega

Solicitação por e-mail → prova de uso único (30 minutos) → definição explícita de senha/ativação sem login automático → login → identidade autenticada → logout. Spring Security com Spring Session JDBC 4.1.1, migrações aditivas V3/V4, sessão ociosa de30min/absoluta8h, CSRF, rotação e validação de status/auth_version. Telas Next.js conforme DESIGN.md, sem novas dependências frontend. Sem recuperação de senha, tenant, loja ou roles.

## Evidências finais

- **Backend: BUILD SUCCESS, 41 testes aprovados (19 unitários +22 integrados), zero falhas, erros ou ignorados.** Spotless, compilação, empacotamento, Surefire e Failsafe passaram; conclusão em 16/10/2026 às20:08:44 BRT. Log local ignorado: backend/target/auth-functional-final.log; XML em target/surefire-reports e target/failsafe-reports.
- Oito integrações HTTP de autenticação aprovadas: fluxo, CSRF/origem/corpo limitado, expiração/reuso/digest da prova, reenvio suprimido, confirmação concorrente, sessão/rotação/idle/teto absoluto/revogação, enumeração/abuso e falha do transporte preservando a prova anterior. PostgreSQL18.6 real via Testcontainers2.0.5, isolado do volume Compose.
- Na primeira execução com Docker disponível, uma asserção da fundação ainda proibia spring_session. Foi atualizada para exigir as novas tabelas autorizadas, preservando a proibição de tenant/store/membership/roles; a suíte completa foi executada novamente e passou. O erro intencional do teste de rollback Flyway é esperado e verificado.
- O bloqueio anterior do Docker foi superado: Engine29.1.3 disponível, Compose saudável e migrações aditivas aplicadas ao banco local. Nenhum volume foi apagado. Avisos existentes de Mockito/agent e API depreciada no BootstrapIT permanecem.
- **Admin: lint, typecheck, build e smoke aprovados.** A execução inicial sofreu restrição do sandbox no SWC; a execução autorizada passou. Nenhum TSX mudou depois dessa validação.
- **Navegador in-app: fluxo completo aprovado com identidade sintética local.** Cadastro gerou mensagem no diretório DEV protegido; link abriu com fragmento removido; confirmação explícita definiu senha sem autenticar; senha incorreta produziu mensagem genérica; login correto abriu Minha conta; recarregar preservou sessão; logout retirou o acesso à identidade. Nenhum dado de usuário real ou transporte externo foi usado.
- Verificações anteriores de UI: reflow a320CSSpx, labels, erro de senhas diferentes, link ausente e foco no feedback. Playwright/Axe não instalados; leitor de tela e avaliação WCAG completa não executados. Contrastes documentados em DESIGN.md não equivalem a auditoria integral.
- Benchmark e parâmetros de hashing da Etapa1 preservados, sem repetição. CI anterior da main não comprova estas alterações locais; não houve nova execução remota.

Comando final, em backend/ com JDK21 e Docker (cache local já preenchido):

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) 'target/maven-user'
$taskRepository = Join-Path (Get-Location) 'target/maven-repository'
.\mvnw.cmd -o -B -ntp "-Dmaven.repo.local=$taskRepository" com.diffplug.spotless:spotless-maven-plugin:3.10.4:apply verify
```

Para baixar dependências em instalação nova, retirar -o; Maven verify normal também é descrito no README.

## Revisões e limites

Backend e frontend implementaram em arquivos separados; QA criou integrações críticas. Segurança identificou e acompanhou as correções de caminho real da caixaDEV (junction/symlink ancestral) e resposta genérica durante falha do transporte. A raiz Git inteira é protegida mesmo com execução a partir de backend/. Regressões correspondentes passaram.

Mensagens DEV ficam fora do repositório/OneDrive, com ACL/POSIX restritos. Banco guarda somente digest da prova. Proxy local compartilha quota por origem; proxy confiável, HTTPS, e-mail real e capacidade operacional exigem configuração antes de produção. Blocklist inicial permanece limitada conforme fundação.

**SEC-003 permanece aberta e inalterada, sem aceite de risco.** Git diff --check, links e Gitleaks verificados no snapshot local; nenhum segredo identificado. Índice Git vazio; nenhum commit/push desta entrega.

## Executar e próximos passos

Iniciar PostgreSQL pelo Compose do [README](../../README.md), backend por scripts/start-backend.ps1 (perfil dev) e admin com npm run dev. Abrir http://127.0.0.1:3000/cadastro; localizar a mensagem em %LOCALAPPDATA%/SmartCommerce/mail no Windows e abrir o link completo. Fazer login após a confirmação.

Próximos passos: revisão/aceite humano, autorização de publicação e CI remota desta entrega. Não iniciar SC-003 automaticamente. Decisões: [ADR-0005](../architecture/adr/0005-identity-authentication.md).
