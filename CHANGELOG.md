# Changelog

Todas as mudanças relevantes do Echoes-Server são registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [Não lançado]

## [2026-11-23]

### Segurança
- S1: o token de redefinição de senha passa a ser de uso único e atômico. O Redis compara e remove a chave num único script Lua; token errado não apaga o válido e requisições simultâneas resultam em uma única troca de senha.
- S2: tokens JWT emitidos antes de troca de senha, redefinição ou revogação de consentimento são rejeitados. No mesmo segundo da revogação o token continua sendo rejeitado (comportamento conservador).
- S5: o broker Mosquitto exige autenticação (`allow_anonymous false` e arquivo de senhas); o cliente MQTT do servidor se conecta autenticado.
- S9: limite de requisições por IP em login, MFA, recuperação de senha e entrada em turma por código (429 ao exceder).
- S10: a sessão é entregue no cookie `echoes_session` (`HttpOnly`, `Secure`, `SameSite=Strict`); mutações autenticadas só por cookie exigem o cabeçalho `X-Echoes-Client`. A troca de senha também limpa o cookie.
- S15: registros de auditoria assinados com HMAC (`AUDIT_HMAC_KEY`) e protegidos por gatilhos `audit_log_no_update` e `audit_log_no_delete`; `GET /api/v1/audit-logs/integrity` lista registros adulterados.
- S17: comparação em tempo constante (`MessageDigest.isEqual`) para códigos MFA, códigos de cadastro, token de redefinição e assinatura de auditoria.
- E-mail normalizado (caixa e espaços) no login, MFA e fluxo de redefinição de senha, evitando chaves duplicadas no Redis.
- `ADMIN_PASSWORD` fora da política de senha aborta a subida em vez de criar um administrador fraco.

### Adicionado
- Bloqueio de força bruta no Redis: login e MFA bloqueiam na 5ª falha (429) e liberam ao expirar a janela de 15 minutos, que não é renovada por novas falhas.
- LGPD: exportação dos dados do titular (`GET /api/v1/users/me/export`), revogação de consentimento que preserva o histórico e encerra as sessões, e bloqueio (403) até o novo aceite dos termos obrigatórios.
- Serviço `mosquitto` no `docker-compose.yml` e script `mosquitto/create-passwd.sh` para gerar o arquivo de senhas a partir do `.env`.
- Auditoria de redefinição de senha com o e-mail do titular em `details`.
- Testes de integração contra Redis real (reset concorrente, bloqueio e TTL, revogação de JWT) e teste de isolamento da exportação entre usuários.
- Testes de `RateLimitFilter`, `AuditLogController`, `SuperUserInitializer` e `PasswordController`.
- `-DrequireRedis=true` faz os testes de integração com Redis falharem, em vez de serem pulados, quando o Redis não está acessível.

### Corrigido
- A falha de auditoria (`recordFailure`) é gravada de forma assíncrona, liberando a conexão do pool Hikari da transação externa; antes, falhas concorrentes podiam esgotar o pool.
- `./mvnw -P coverage verify` não gerava o relatório JaCoCo porque o `argLine` do surefire sobrescrevia o agente.
- `create-passwd.sh` falhava ao criar o arquivo de senhas e gerava um arquivo ilegível para o usuário do broker no container.
- `.env.example`: URL do broker coerente com o Mosquitto local e remoção da variável sem uso `MATERIAL_EXTENSIONS_ALLOWED`.

### Removido
- `env.example` duplicado; `.env.example` é a única referência.

### Pendente
- Política `1.1.0`: depende de revisão e aprovação do texto jurídico e não é ativada automaticamente.
- Rotação dos segredos do `.env` (ver checklist de deploy) e uso de `ADMIN_PASSWORD` compatível com a política de senha.
