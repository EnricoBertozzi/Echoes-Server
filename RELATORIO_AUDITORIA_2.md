# Relatório de auditoria 2 — back-end

## 1. Resumo

- **Data:** 20/11/2026.
- **Repositório:** `Echoes-Server-production`.
- **BASE:** `c66197f4ce3643d155bfdc0b93e3d34ffd971129` (`git rev-parse HEAD` registrado na preparação).
- **Objetivo do acompanhamento:** gravar nome, papel e instituição do autor em cada evento de auditoria; confirmar que o endereço de IP continua visível somente para `ADMIN`; mascarar o e-mail nas listagens; e criar uma leitura do e-mail completo que seja auditada como `VIEW_EMAIL`.
- **Estado prévio:** antes de qualquer edição, `git status --short` foi registrado e o original de cada arquivo foi copiado para `/tmp/audit2-original/Echoes-Server-production/`. Todos os diffs descritos abaixo foram obtidos por comparação com essas cópias.
- **Schema:** o projeto usa `spring.jpa.hibernate.ddl-auto=update` em `src/main/resources/application.properties`; não há Flyway nem Liquibase. Por isso não foi criada migration versionada nesta entrega.
- **Idioma:** nomes de campos, métodos e anotações são identificadores técnicos; textos, comentários e mensagens inseridos estão em português brasileiro.

## 2. Arquivos alterados

| Arquivo | Classe/Componente | Método/Função | Tipo | Resumo |
| --- | --- | --- | --- | --- |
| `src/main/java/com/n0hana/echoes_server/infra/logs/AuditLog.java` | `AuditLog` | `AuditLogBuilder` / `build()` | Alteração | Três campos novos para o autor, com `@Column`, e respectivos métodos do builder. |
| `src/main/java/com/n0hana/echoes_server/infra/logs/AuditAspect.java` | `AuditAspect` | `logSuccess()`, `logFailure()`, `resolveActor()`, record `Actor` | Alteração | Resolve o autor autenticado e preenche os três campos nos eventos de sucesso e de falha. |
| `src/main/java/com/n0hana/echoes_server/infra/logs/AuditLogService.java` | `AuditLogService` | `saveLog()` | Alteração | Copia os três campos do evento para a entidade persistida. |
| `src/main/java/com/n0hana/echoes_server/infra/logs/AuditLogDTO.java` | `AuditLogDTO` | `from()` | Alteração | Expõe os três campos no contrato HTTP, mantendo a regra do IP. |
| `src/main/java/com/n0hana/echoes_server/user/UserUtils.java` | `UserUtils` | `toMaskedDTO()`, `maskEmail()` | Alteração | Converte usuário para DTO com e-mail mascarado na listagem. |
| `src/main/java/com/n0hana/echoes_server/user/service/UserInfoService.java` | `UserInfoService` | listagens, `findEmail()` | Alteração | Listagens passam a usar o DTO mascarado; nova leitura auditada do e-mail completo. |
| `src/main/java/com/n0hana/echoes_server/user/dto/UserEmailDTO.java` | `UserEmailDTO` | — | Novo | DTO de resposta com `id`, `name`, `email` e `institutionId`. |
| `src/main/java/com/n0hana/echoes_server/user/controller/UserEmailController.java` | `UserEmailController` | `findEmail()` | Novo | `GET /api/v1/users/{id}/email`, protegido pelo guard e auditado. |

Não houve arquivo no Step 2: não existe classe de evento separada. `AuditLog` é ao mesmo tempo a entidade persistida e o evento publicado por `AuditAspect` e consumido por `AuditLogService`; portanto os três campos do autor entraram no Step 1 e foram propagados nos passos seguintes.

## 3. Submódulos

### 3.1 Registro do autor no evento e na persistência

**Antes.** `AuditLog` guardava apenas `userId` como identificação de quem agiu. `AuditAspect` publicava o evento com `userId(...)` e os dados da ação; `AuditLogService.saveLog()` montava a entidade sem nenhuma informação legível sobre o autor. Para saber quem era o usuário, era preciso cruzar o `userId` com o cadastro atual — o que perde a instituição e o papel vigorantes na época do evento.

**Depois.** `AuditLog` passou a ter `actorName` (`@Column(length = 255)`), `actorRole` (`@Column(length = 32)`) e `actorInstitutionName` (`@Column(length = 255)`), todos anuláveis, mais os três métodos no builder e a cópia em `build()`. `AuditAspect` chama `resolveActor()` no início de `logSuccess()` e de `logFailure()` e repassa `actor.name()`, `actor.role()` e `actor.institutionName()` aos dois builders. `resolveActor()` usa o mesmo `SecurityContextHolder` de `getUserId()`: exige autenticação e principal do tipo `User`; obtém o nome com `user.getName()`, o papel com `user.getUserRole().name()` e a instituição de `Manager`, `Teacher` ou `Student`; `ADMIN` e eventos sem principal ficam sem instituição. Qualquer exceção devolve `new Actor(null, null, null)` para não interromper a operação auditada. `AuditLogService.saveLog()` copia os três valores do evento para a entidade antes de persistir.

**Como funciona agora.** A cada ação anotada com `@Auditable`, o aspecto captura uma foto do autor no momento da chamada. A foto viaja no evento, é gravada junto com o log e sobrevive a alterações posteriores no cadastro do usuário. Falhas na leitura do autor degeneram para nulos sem afetar a operação de negócio nem o registro do evento.

**Impacto.** Os logs passam a ser legíveis sem consulta ao cadastro e preservam o contexto histórico do autor. Nenhuma asserção de teste existente precisou mudar; nenhuma assinatura pública de método foi alterada além do próprio builder interno.

### 3.2 Contrato HTTP dos logs

**Antes.** `AuditLogDTO` continha `id`, `userId`, ação, entidade, resultado, detalhes, `ip`, `entityId`, `institutionId` e `timestamp`, e `from(log, includeIp)` decidia se o IP seria preenchido.

**Depois.** O record passou a declarar `actorName`, `actorRole` e `actorInstitutionName` logo após `userId`, e `from()` passa a ler `log.getActorName()`, `log.getActorRole()` e `log.getActorInstitutionName()`. A linha do IP permanece exatamente como era: `includeIp ? log.getIp() : null`.

**Como funciona agora.** `AuditLogController.list` continua chamando `AuditLogDTO.from(log, user.getUserRole() == UserRole.ADMIN)`. O front-end recebe os dados do autor em todas as respostas, enquanto o IP continua chegando como `null` para `MANAGER`.

**Impacto.** Mudança aditiva no contrato de resposta: consumidores antigos ignoram os campos novos. Nenhuma permissão nova foi criada e a regra do IP não foi flexibilizada.

### 3.3 Mascaramento do e-mail nas listagens

**Antes.** `UserInfoService` mapeava todos os resultados de repositório com `UserUtils::toDTO`, devolvendo `UserDTO` com o e-mail completo em administradores, gestores, professores, alunos, buscas por nome e listas por instituição.

**Depois.** `UserUtils.toMaskedDTO(User)` chama `toDTO(user)` e devolve um novo `UserDTO` com `maskEmail(dto.email())` no lugar do e-mail original. `maskEmail()` preserva o domínio e mostra apenas a primeira letra da parte local: `maria@escola.com` vira `m***@escola.com`; parte local de um caractere vira `*@dominio`; e-mail nulo permanece nulo. As sete listagens de `UserInfoService` (`findAdmins`, `findManagers`, `findTeachers`, `findStudents`, `findAdminByName`, `findUsersByInstitution` e `findUsersByNameInInstitution`) agora usam `UserUtils::toMaskedDTO`.

**Como funciona agora.** As rotas de listagem e busca continuam com a mesma paginação e os mesmos filtros, mas nunca mais devolvem o e-mail completo. A leitura individual existente (`findUser`) não foi alterada; o e-mail completo passa a ser obtido somente pelo endpoint específico descrito em 3.4, que gera auditoria.

**Impacto.** Redução da exposição passiva de e-mails em telas de administradores e gestores. Nenhum repositório, consulta ou DTO de entrada foi modificado.

### 3.4 Leitura auditada do e-mail completo

**Antes.** Não havia endpoint dedicado: para mostrar o e-mail completo, o front-end teria de usar o e-mail devolvido pela própria listagem, sem nenhum registro de auditoria da visualização.

**Depois.** `UserEmailDTO` é um record com `id`, `name`, `email` e `institutionId`. `UserInfoService.findEmail(UUID)` busca o usuário com `findById`, exige `isActive()`, obtém a instituição com `UserUtils.institutionOf(user)` e devolve o DTO. O método está anotado com `@Auditable(action = "VIEW_EMAIL", entity = "User", entityId = "#id", entityName = "#result.name()", institutionId = "#result.institutionId()", details = "'E-mail visualizado'")`. `UserEmailController` expõe `GET /api/v1/users/{id}/email` com `@SecurityRequirement(name = SecurityConfig.SECURITY)` e `@PreAuthorize("@guard.canAccess(#id)")`, injetando `UserInfoService` no mesmo estilo dos controllers existentes.

**Como funciona agora.** Cada chamada ao endpoint valida o acesso pelo guard, recupera o usuário ativo e, ao sair do método, o aspecto publica um evento `VIEW_EMAIL` com nome do registro, instituição e dados do autor resolvidos em 3.1. Usuário inexistente ou inativo continua lançando `UserNotFoundException`.

**Impacto.** A revelação do e-mail deixa de ser uma consequência silenciosa da listagem e passa a ter trilha auditável, com autor e instituição gravados. O endpoint segue o mesmo mecanismo de autorização dos demais endpoints de usuário; não foi criado papel nem regra de acesso nova.

## 4. Impacto geral

- Quatro arquivos da área de logs (`AuditLog`, `AuditAspect`, `AuditLogService`, `AuditLogDTO`) e quatro da área de usuários (`UserUtils`, `UserInfoService`, `UserEmailDTO`, `UserEmailController`) foram afetados; nenhum outro pacote foi tocado.
- O contrato HTTP de auditoria ganhou três campos; o contrato de listagens de usuários passou a devolver e-mail mascarado; surgiu um endpoint novo para o e-mail completo.
- A regra de exibição do IP e a autorização dos endpoints de auditoria permanecem inalteradas.
- As colunas novas serão criadas pelo `ddl-auto=update`. Em ambientes que usam validação de schema ou migrações externas, será necessário reproduzir essa alteração no mecanismo próprio daquele ambiente.
- Nenhum teste foi alterado nem acrescentado nesta entrega.

## 5. Step 0 — confirmação da regra de IP

A verificação foi feita antes de qualquer edição e não gerou arquivo:

- `AuditLogController.list` calcula `includeIp` com `user.getUserRole() == UserRole.ADMIN` e passa esse valor a `AuditLogDTO.from(log, includeIp)`. A classe exige `hasAnyRole('ADMIN', 'MANAGER')`, então `MANAGER` consegue consultar logs, mas recebe `ip` nulo.
- A única atribuição do campo `ip` no DTO é `includeIp ? log.getIp() : null`; não há outro caminho que preencha o campo.
- O `@AuthenticationPrincipal User` é o usuário real carregado por `JwtFilter` por meio de `userRepository.findById(...)`, e não um valor recebido do cliente.
- Não foi encontrado furo: a regra "somente `ADMIN` vê o IP" já era correta e foi mantida. Nenhuma linha foi editada nesta etapa.

## 6. Verificação

Comandos executados de verdade no repositório do back-end:

- `./mvnw -q -DskipTests compile` → exit 0, sem saída.
- `./mvnw -q test` → a primeira execução atingiu o limite de 600 segundos em razão de chamadas externas à BrasilAPI; a reexecução concluiu com exit 0. Os relatórios Surefire registram 74 suítes e 535 testes, com 0 falhas e 0 erros. Os stack traces de `DataIntegrityViolationException` no console pertencem a testes que exercitam deliberadamente esse cenário e não indicam falha.
- Não havia asserções de e-mail em testes de listagem que precisassem ser ajustadas.

Não foi afirmado nenhum resultado que não tenha sido medido.

## 7. Roteiro manual

1. Entrar como `MANAGER` ou `ADMIN`, executar uma ação auditada (por exemplo, aprovar um cadastro) e consultar `GET /api/v1/audit-logs`: conferir que nome, papel e instituição do autor aparecem no evento recém-criado.
2. Repetir a consulta como `ADMIN` e como `MANAGER`: o campo `ip` deve vir preenchido apenas na resposta do `ADMIN`.
3. Abrir a listagem de administradores e a de membros da instituição: os e-mails devem aparecer mascarados, com o domínio intacto.
4. No front-end, acionar o botão de olho em um usuário: deve ser feita a chamada a `GET /api/v1/users/{id}/email`, o e-mail completo aparece no card e um evento `VIEW_EMAIL` é registrado com o autor correto.
5. Ocultar o e-mail e tornar a mostrar: omitir não deve gerar requisição nem log; cada revelação deve gerar um novo `VIEW_EMAIL`.
6. Tentar chamar o endpoint de e-mail sem permissão ou com um id inexistente: a resposta deve ser recusada pelo guard ou pelo `UserNotFoundException`, conforme o caso.

## 8. Pendências

- Incluir filtros por nome/id e por intervalo de datas na consulta de logs.
- Levar o botão de olho também às listas de turma e aos endpoints de escopo de turma, hoje fora do escopo desta entrega.
- Registrar autoria em eventos que ocorrem antes da autenticação, quando não há principal no contexto.
- Registrar auditoria também na abertura do modal de detalhes, se esse acesso precisar ser rastreado.
- Melhorar o texto dos eventos `UPDATE` para registrar "antes → depois", além de tratar `ACCESS_DENIED`, exportação em CSV, retenção/expurgo dos logs e convites.
- Padronizar a classe de exceção e a causa raiz registradas nos detalhes de falha.
- Corrigir o rótulo legado `LOGIN_SUCCESS)` e o comportamento do `fallbackExecution` apontados na auditoria anterior.
- Criar uma migração versionada das três colunas caso o ambiente de destino desative `ddl-auto=update`.

## 9. Commits sugeridos

1. `feat(audit): registra o autor nos eventos de auditoria` — `AuditLog.java`, `AuditAspect.java`, `AuditLogService.java`, `AuditLogDTO.java`.
2. `feat(users): mascara o e-mail nas listagens` — `UserUtils.java`, `UserInfoService.java` (parte de mascaramento).
3. `feat(users): endpoint auditado para visualizar o e-mail` — `UserEmailDTO.java`, `UserEmailController.java`, `UserInfoService.java` (método `findEmail`).
4. `docs(audit): relatório da auditoria 2 no back-end` — `RELATORIO_AUDITORIA_2.md`.

O `RELATORIO_AUDITORIA.md` original não foi sobrescrito.
