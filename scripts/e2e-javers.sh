#!/usr/bin/env bash
# Validação E2E do histórico (Javers) contra a stack em Docker Compose com MySQL real.
# Sai com código != 0 se qualquer asserção falhar. Requer a stack com JAVERS_CHECKPOINT_THRESHOLD=0.
#
# Variáveis: BASE_URL, E2E_COMPOSE (como em e2e.sh), ADMIN_EMAIL/ADMIN_PASSWORD (padrão: .env)
set -uo pipefail

cd "$(dirname "$0")/.."
BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="${E2E_COMPOSE:-docker compose}"
WORK="$(mktemp -d)"
RUN_ID="$(date +%s)"

envval() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | tr -d '"' | tr -d "'"; }
ADMIN_EMAIL="${ADMIN_EMAIL:-$(envval ADMIN_EMAIL)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(envval ADMIN_PASSWORD)}"

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf '[OK]     %s\n' "$*"; }
fail() { FAIL=$((FAIL+1)); printf '[FALHA]  %s\n' "$*"; }
eq()   { if [[ "$2" == "$3" ]]; then ok "$1 ($3)"; else fail "$1 (esperado $2, obtido $3)"; fi; }
ge()   { if [[ "$3" -ge "$2" ]] 2>/dev/null; then ok "$1 ($3 >= $2)"; else fail "$1 (esperado >= $2, obtido $3)"; fi; }

rcli() { $COMPOSE exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning "$@"' _ "$@" | tr -d '\r'; }
msql() { $COMPOSE exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -N -B "$MYSQL_DATABASE" -e "$1" 2>/dev/null' _ "$1" | tr -d '\r'; }
http() {
  local m="$1" p="$2" t="$3"; shift 3
  local auth=(); [[ -n "$t" ]] && auth=(-H "Authorization: Bearer $t")
  curl -s -o "$WORK/body" -w '%{http_code}' -X"$m" "$BASE_URL$p" "${auth[@]}" -H 'Content-Type: application/json' "$@"
}
jget() { python3 -c "import json;d=json.load(open('$WORK/body'));print(eval(__import__('sys').argv[1]))" "$1" 2>/dev/null; }
login_user() {
  local email="$1" pass="$2" lc code
  lc="$(echo "$email" | tr 'A-Z' 'a-z')"
  curl -s -o /dev/null -XPOST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"$pass\"}"
  code="$(rcli GET "pending:login:$lc")"; [[ -z "$code" ]] && return 1
  curl -s -D "$WORK/mfa.hdr" -o /dev/null -XPOST "$BASE_URL/api/v1/auth/mfa" -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"code\":\"$code\"}"
  tr -d '\r' < "$WORK/mfa.hdr" | grep -i '^set-cookie: echoes_session=' | sed -E 's/^[^=]*=([^;]*).*/\1/'
}
accept_all() { local t="$1" ty; for ty in TERMS_OF_USE PRIVACY_POLICY COOKIES_POLICY; do http POST /terms/accept "$t" -d "{\"type\":\"$ty\"}" >/dev/null; done; }
uid() { msql "select BIN_TO_UUID(id) from users where email='$1'"; }
hexid() { echo "UNHEX(REPLACE('$1','-',''))"; }

USERS=(); USER_IDS=(); INST=""; CLASS=""; CHECKPOINT=""; DEVICE_UUID=""; JV_IDS=""; EXTRA_CPS=()
mkuser() { # PAPEL EMAIL NOME SENHA [INSTITUICAO]
  local role="$1" email="$2" name="$3" pass="$4" inst="${5:-}" hash instsql="NULL"
  hash="$(python3 -c "import bcrypt,sys;print(bcrypt.hashpw(sys.argv[1].encode(),bcrypt.gensalt(10)).decode())" "$pass")"
  [[ -n "$inst" ]] && instsql="$(hexid "$inst")"
  msql "insert into users (role,id,active,email,login_attempts,name,password,institution_id) values ('$role',UNHEX(REPLACE(UUID(),'-','')),1,'$email',0,'$name','$hash',$instsql)" >/dev/null
  USERS+=("$email")
  USER_IDS+=("$(uid "$email")")   # por id: o e-mail pode ser anonimizado depois
}

cleanup() {
  echo; echo "== Limpeza dos registros de teste =="
  local ids="'x'" e id
  for e in "${USERS[@]:-}"; do [[ -n "$e" ]] && { id="$(uid "$e")"; [[ -n "$id" ]] && ids="$ids,'\"$id\"'"; }; done
  [[ -n "$CLASS" ]] && ids="$ids,'\"$CLASS\"'"
  ids="$ids$JV_IDS"
  for id in "${USER_IDS[@]:-}"; do [[ -n "$id" ]] && ids="$ids,'\"$id\"'"; done
  for id in $(msql "select BIN_TO_UUID(id) from device_tb where hardware_id like 'e2e-javers-%' union select BIN_TO_UUID(id) from scenario_tb where name like 'e2e-javers%' union select BIN_TO_UUID(id) from institutions where name like 'e2e-javers-%'"); do ids="$ids,'\"$id\"'"; done
  # membros de turma (incluindo os recriados pelo rollback)
  [[ -n "$CLASS" ]] && for id in $(msql "select BIN_TO_UUID(id) from classroom_members where classroom_id=$(hexid "$CLASS")"); do ids="$ids,'\"$id\"'"; done
  [[ -n "$CLASS" ]] && msql "delete from classroom_members where classroom_id=$(hexid "$CLASS")" >/dev/null
  [[ -n "$CLASS" ]] && msql "delete from classroom_model where id=$(hexid "$CLASS")" >/dev/null
  local list="'x'" idl="UNHEX('00')"; for e in "${USERS[@]:-}"; do [[ -n "$e" ]] && list="$list,'$e'"; done
  for id in "${USER_IDS[@]:-}"; do [[ -n "$id" ]] && idl="$idl,$(hexid "$id")"; done
  msql "delete a from user_terms_acceptance a join users u on u.id=a.user_id where u.email in ($list) or u.id in ($idl)" >/dev/null
  msql "delete from users where email in ($list) or id in ($idl)" >/dev/null
  msql "delete from device_tb where hardware_id like 'e2e-javers-%'" >/dev/null
  msql "delete from scenario_tb where name like 'e2e-javers%'" >/dev/null
  $COMPOSE exec -T -u root spring sh -c 'rm -f /uploads/*e2e-javers*' >/dev/null 2>&1
  msql "delete from institutions where name like 'e2e-javers-%'" >/dev/null
  [[ -n "$INST" ]] && msql "delete from institutions where id=$(hexid "$INST")" >/dev/null
  [[ -n "$CHECKPOINT" ]] && msql "delete from javers_checkpoint where id=$(hexid "$CHECKPOINT")" >/dev/null
  msql "delete from javers_checkpoint where created_at >= from_unixtime($RUN_ID - 5)" >/dev/null
  # histórico do Javers dos registros de teste (na ordem das chaves estrangeiras)
  msql "delete from jv_snapshot where global_id_fk in (select global_id_pk from jv_global_id where local_id in ($ids))" >/dev/null
  msql "delete from jv_global_id where local_id in ($ids) and global_id_pk not in (select global_id_fk from jv_snapshot)" >/dev/null
  msql "delete from jv_commit_property where commit_fk not in (select commit_fk from jv_snapshot)" >/dev/null
  msql "delete from jv_commit where commit_pk not in (select commit_fk from jv_snapshot) and commit_pk not in (select commit_fk from jv_commit_property)" >/dev/null
  [[ -n "$DEVICE_UUID" ]] && rcli DEL "device:tokens-revoked-at:$DEVICE_UUID" >/dev/null
  for e in "${USERS[@]:-}" "$(echo "$ADMIN_EMAIL" | tr 'A-Z' 'a-z')"; do
    [[ -n "$e" ]] && for k in attempts:login attempts:mfa pending:login; do rcli DEL "$k:$(echo "$e" | tr 'A-Z' 'a-z')" >/dev/null; done
  done
  msql "delete a from user_terms_acceptance a join users u on u.id=a.user_id where u.email='$ADMIN_EMAIL' and a.accepted_at >= from_unixtime($RUN_ID - 5)" >/dev/null
  msql "update users set tokens_revoked_at=NULL, password_changed_at=NULL where email='$ADMIN_EMAIL'" >/dev/null
  echo "removidos: usuários, turma, instituição, checkpoint, histórico Javers e contadores dos registros de teste."
  echo "mantidos por desenho: linhas de audit_log (gatilhos de imutabilidade)."
  rm -rf "$WORK"
}
trap cleanup EXIT

echo "=== Echoes-Server — Javers E2E (MySQL real) — $BASE_URL ($(date -Iseconds)) ==="
[[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]] && { echo "ADMIN_EMAIL/ADMIN_PASSWORD ausentes"; exit 2; }

echo; echo "== 1. Estado inicial =="
eq "políticas de retenção semeadas" 10 "$(msql "select count(*) from javers_retention_policy")"
ge "baseline criou snapshots" 1 "$(msql "select count(*) from jv_snapshot")"
eq "senha fora do histórico (nenhum estado com hash BCrypt)" 0 "$(msql "select count(*) from jv_snapshot where state like '%\$2a\$%' or state like '%\"password\"%'")"
eq "nenhum LazyInitializationException nos logs do Spring" 0 "$($COMPOSE logs spring 2>&1 | grep -c 'LazyInitializationException')"

echo; echo "== 2. Preparação (SQL) e baseline por reinício =="
INST="$(msql "select UUID()")"
msql "insert into institutions (id,acronym,active,cnpj,created_at,delete_token,deleted,email,name,verification_attempts,verification_status) values ($(hexid "$INST"),'JV',1,'00000000000191',now(),'jv-$RUN_ID',0,'jv-inst-$RUN_ID@test.local','Javers Inst $RUN_ID',0,'VERIFIED')" >/dev/null
MGR_PASS="Mg$(openssl rand -hex 6)"
MGR="jv-mgr-$RUN_ID@test.local"; S1="jv-s1-$RUN_ID@test.local"; S2="jv-s2-$RUN_ID@test.local"; S3="jv-s3-$RUN_ID@test.local"
mkuser MANAGER "$MGR" "JV Gestor" "$MGR_PASS" "$INST"
for s in "$S1" "$S2" "$S3"; do mkuser STUDENT "$s" "Aluno ${s%%@*}" "x$(openssl rand -hex 4)" "$INST"; done
CLASS="$(msql "select UUID()")"
msql "insert into classroom_model (id,archived,created_at,deleted,period,title,updated_at,institution_id) values ($(hexid "$CLASS"),0,now(),0,'2026/2','Turma Javers $RUN_ID',now(),$(hexid "$INST"))" >/dev/null
for s in "$S1" "$S2" "$S3"; do
  U="$(uid "$s")"
  msql "insert into classroom_members (id,joined_at,student_id,classroom_id,user_id) values (UNHEX(REPLACE(UUID(),'-','')),now(),$(hexid "$U"),$(hexid "$CLASS"),$(hexid "$U"))" >/dev/null
done
echo "    reiniciando o Spring para o baseline registrar os dados semeados..."
$COMPOSE restart spring >/dev/null 2>&1
for i in $(seq 1 40); do [[ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/v3/api-docs")" != "000" ]] && break; sleep 2; done
sleep 5
S1ID="$(uid "$S1")"
ge "baseline registrou o aluno S1 com e-mail original" 1 "$(msql "select count(*) from jv_snapshot s join jv_global_id g on g.global_id_pk=s.global_id_fk where g.local_id='\"$S1ID\"' and s.state like '%$S1%'")"
ge "baseline registrou os 3 membros da turma" 3 "$(msql "select count(*) from jv_global_id where type_name like '%ClassroomMember' and global_id_pk in (select global_id_fk from jv_snapshot where state like '%$CLASS%')")"

echo; echo "== 3. Login =="
TA="$(login_user "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"; [[ -n "$TA" ]] && ok "admin autenticado" || { fail "login do admin"; exit 1; }
accept_all "$TA"
TM="$(login_user "$MGR" "$MGR_PASS")"; [[ -n "$TM" ]] && ok "gestor autenticado" || { fail "login do gestor"; exit 1; }
accept_all "$TM"

echo; echo "== 4. Autorização: gestor não acessa os endpoints administrativos =="
eq "gestor: prune/run" 403 "$(http POST /api/v1/admin/javers/prune/run "$TM")"
eq "gestor: revoke-tokens" 403 "$(http POST /api/v1/admin/devices/$(msql 'select UUID()')/revoke-tokens "$TM")"
eq "sem token: prune/run" 401 "$(http POST /api/v1/admin/javers/prune/run "")"

echo; echo "== 5. Anonimização do histórico após a exclusão lógica =="
eq "gestor exclui o aluno S1" 204 "$(http DELETE "/api/v1/users/student/$S1ID" "$TM")"
for i in $(seq 1 20); do
  C="$(msql "select count(*) from jv_snapshot s join jv_global_id g on g.global_id_pk=s.global_id_fk where g.local_id='\"$S1ID\"' and s.state like '%$S1%'")"
  [[ "$C" == 0 ]] && break; sleep 1
done
eq "nenhum snapshot de S1 guarda o e-mail original" 0 "$C"
eq "nenhum snapshot de S1 guarda o nome original" 0 "$(msql "select count(*) from jv_snapshot s join jv_global_id g on g.global_id_pk=s.global_id_fk where g.local_id='\"$S1ID\"' and s.state like '%Aluno jv-s1%'")"
ge "snapshots de S1 com e-mail anonimizado" 1 "$(msql "select count(*) from jv_snapshot s join jv_global_id g on g.global_id_pk=s.global_id_fk where g.local_id='\"$S1ID\"' and s.state like '%deleted_user_${S1ID}@anon.invalid%'")"
ge "snapshots dos outros alunos intactos" 1 "$(msql "select count(*) from jv_snapshot where state like '%$S2%'")"
ge "auditoria JAVERS_ANONYMIZED registrada" 1 "$(msql "select count(*) from audit_log where action='JAVERS_ANONYMIZED' and status='SUCCESS'")"

echo; echo "== 6. Checkpoint automático, aprovação e rollback em lote =="
eq "gestor limpa os alunos da turma" 204 "$(http DELETE "/api/v1/classrooms/$CLASS/students" "$TM")"
eq "membros removidos" 0 "$(msql "select count(*) from classroom_members where classroom_id=$(hexid "$CLASS")")"
CHECKPOINT="$(msql "select BIN_TO_UUID(id) from javers_checkpoint order by created_at desc limit 1")"
[[ -n "$CHECKPOINT" ]] && ok "checkpoint criado automaticamente ($(msql "select affected_count from javers_checkpoint where id=$(hexid "$CHECKPOINT")") entidades)" || fail "checkpoint não criado"
eq "checkpoint aguardando aprovação" PENDING_APPROVAL "$(msql "select status from javers_checkpoint where id=$(hexid "$CHECKPOINT")")"
eq "rollback antes da aprovação é recusado" 400 "$(http POST "/api/v1/admin/javers/rollback/checkpoint/$CHECKPOINT" "$TA")"
eq "approvedBy diferente do autenticado é recusado" 400 "$(http POST "/api/v1/admin/javers/checkpoint/$CHECKPOINT/approve" "$TA" -d '{"approvedBy":"outra-pessoa"}')"
eq "admin aprova o checkpoint" 204 "$(http POST "/api/v1/admin/javers/checkpoint/$CHECKPOINT/approve" "$TA" -d '{}')"
eq "aprovar de novo é recusado" 400 "$(http POST "/api/v1/admin/javers/checkpoint/$CHECKPOINT/approve" "$TA" -d '{}')"
eq "rollback em lote" 200 "$(http POST "/api/v1/admin/javers/rollback/checkpoint/$CHECKPOINT" "$TA")"
eq "resultados do rollback" 2 "$(jget "len(d)")"
eq "membros recriados" 2 "$(msql "select count(*) from classroom_members where classroom_id=$(hexid "$CLASS")")"
eq "os alunos restaurados são S2 e S3" 2 "$(msql "select count(*) from classroom_members m join users u on u.id=m.user_id where m.classroom_id=$(hexid "$CLASS") and u.email in ('$S2','$S3')")"
eq "checkpoint executado" EXECUTED "$(msql "select status from javers_checkpoint where id=$(hexid "$CHECKPOINT")")"
eq "novo rollback do mesmo checkpoint é recusado" 400 "$(http POST "/api/v1/admin/javers/rollback/checkpoint/$CHECKPOINT" "$TA")"
eq "histórico da turma acessível ao admin" 200 "$(http GET "/api/v1/admin/javers/history/ClassroomModel/$CLASS" "$TA")"
ge "histórico lista snapshots" 1 "$(jget "len(d)")"
eq "tipo sem política no histórico" 400 "$(http GET "/api/v1/admin/javers/history/UserTermAcceptance/$CLASS" "$TA")"

echo; echo "== 7. Poda no MySQL real =="
MID="$(uid "$MGR")"
for i in $(seq 1 14); do http PATCH "/api/v1/users/manager/$MID" "$TA" -d "{\"name\":\"JV Gestor v$i\"}" >/dev/null; done
GID="$(msql "select global_id_pk from jv_global_id where local_id='\"$MID\"' limit 1")"
BEFORE="$(msql "select count(*) from jv_snapshot where global_id_fk=$GID")"
ge "gestor tem histórico para podar" 14 "$BEFORE"
msql "update jv_commit set commit_date_instant='2015-01-01T00:00:00.000000000Z' where commit_pk in (select commit_fk from jv_snapshot where global_id_fk=$GID)" >/dev/null
COMMITS_BEFORE="$(msql "select count(*) from jv_commit")"
eq "poda manual" 200 "$(http POST /api/v1/admin/javers/prune/run "$TA")"
echo "    removidos: $(jget "d['removed']")"
ge "linhas removidas" 4 "$(jget "d['removed']")"
eq "restam os 10 snapshots mais recentes do gestor (mínimo por entidade)" 10 "$(msql "select count(*) from jv_snapshot where global_id_fk=$GID")"
eq "jv_global_id do gestor preservado" 1 "$(msql "select count(*) from jv_global_id where global_id_pk=$GID")"
ge "commits órfãos removidos" 4 "$(( COMMITS_BEFORE - $(msql "select count(*) from jv_commit") ))"
eq "nenhum jv_commit órfão" 0 "$(msql "select count(*) from jv_commit where commit_pk not in (select commit_fk from jv_snapshot where commit_fk is not null) and commit_pk not in (select commit_fk from jv_commit_property where commit_fk is not null)")"
ge "auditoria JAVERS_RETENTION_PRUNED registrada" 10 "$(msql "select count(*) from audit_log where action='JAVERS_RETENTION_PRUNED' and status='SUCCESS'")"
ge "auditoria JAVERS_PRUNED_MANUAL registrada" 1 "$(msql "select count(*) from audit_log where action='JAVERS_PRUNED_MANUAL'")"

echo; echo "== 8. Revogação manual de tokens de dispositivo =="
DEVICE_UUID="$(msql "select UUID()")"
eq "POST /api/v1/admin/devices/{id}/revoke-tokens" 204 "$(http POST "/api/v1/admin/devices/$DEVICE_UUID/revoke-tokens" "$TA")"
ge "corte gravado no Redis" 1 "$(rcli EXISTS "device:tokens-revoked-at:$DEVICE_UUID")"
ge "auditoria DEVICE_TOKENS_REVOKED registrada" 1 "$(msql "select count(*) from audit_log where action='DEVICE_TOKENS_REVOKED'")"

echo; echo "== 9. Logs e integridade da auditoria =="
LOGS="$($COMPOSE logs spring 2>&1)"
ge "log 'Rollback aplicado'" 2 "$(echo "$LOGS" | grep -c 'Rollback aplicado')"
ge "log de poda concluída" 1 "$(echo "$LOGS" | grep -c 'Poda do Javers concluída')"
ge "log de anonimização" 1 "$(echo "$LOGS" | grep -c 'anonimizado')"
eq "nenhum LazyInitializationException" 0 "$(echo "$LOGS" | grep -c 'LazyInitializationException')"
eq "GET /api/v1/audit-logs/integrity" 200 "$(http GET /api/v1/audit-logs/integrity "$TA")"
eq "tamperedCount" 0 "$(jget "d['tamperedCount']")"

echo; echo "== 14. Rollback em MySQL real (User, Device, Scenario, Institution) =="
J="e2e-javers"
track() { JV_IDS="$JV_IDS,'\"$1\"'"; }
newid() { msql "select UUID()"; }
mkinst14() { # NOME -> id
  local id; id="$(newid)"; track "$id"
  msql "insert into institutions (id,acronym,active,cnpj,created_at,delete_token,deleted,email,name,verification_attempts,verification_status) values ($(hexid "$id"),'EJ',1,'0000000000$(printf '%04d' $((RANDOM % 10000)))',now(),'$J-$id',0,'$J-$id@test.local','$1',0,'VERIFIED')" >/dev/null
  echo "$id"
}
mkdev14() { # NOME INST -> id
  local id; id="$(newid)"; track "$id"
  msql "insert into device_tb (id,created_at,hardware_id,name,public_key,status,institution_id) values ($(hexid "$id"),now(),'$J-hw-$id','$1','k','ACTIVE',$(hexid "$2"))" >/dev/null
  echo "$id"
}
mksc14() { # NOME DESCRICAO -> id (com arquivo de áudio no container)
  local id file; id="$(newid)"; track "$id"; file="$id-$J.mp3"
  $COMPOSE exec -T -u root spring sh -c "mkdir -p /uploads && chmod 777 /uploads && echo x > /uploads/$file && chmod 666 /uploads/$file" >/dev/null 2>&1
  msql "insert into scenario_tb (id,audio_url,description,name) values ($(hexid "$id"),'$file','$2','$1')" >/dev/null
  echo "$id"
}
ids_json() { python3 -c "import sys,json;print(json.dumps(sys.argv[1:]))" "$@"; }
new_checkpoint() { # TIPO ids... -> imprime o id do checkpoint (aprovado por outro admin)
  local type="$1"; shift
  local code; code="$(http POST /api/v1/admin/javers/checkpoint "$TA" -d "{\"entityType\":\"$type\",\"entityIds\":$(ids_json "$@")}")"
  [[ "$code" == 201 ]] || { echo "ERRO-$code"; return; }
  local cp; cp="$(jget "d['id']")"
  [[ "$(http POST "/api/v1/admin/javers/checkpoint/$cp/approve" "$TA2" -d '{}')" == 204 ]] || { echo "ERRO-approve"; return; }
  echo "$cp"
}

# segundo admin: quem aprova precisa ser diferente de quem pediu
ADM2="$J-admin2-$RUN_ID@test.local"; ADM2_PASS="Ad$(openssl rand -hex 6)"
mkuser ADMIN "$ADM2" "EJ Admin 2" "$ADM2_PASS"
TA2="$(login_user "$ADM2" "$ADM2_PASS")"; [[ -n "$TA2" ]] && accept_all "$TA2" && ok "segundo admin autenticado" || fail "login do segundo admin"
TA="$(login_user "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"; accept_all "$TA"

I14="$(mkinst14 "$J-inst-$RUN_ID")"
M14="$J-mgr-$RUN_ID@test.local"; M14_PASS="Mg$(openssl rand -hex 6)"
mkuser MANAGER "$M14" "EJ Gestor" "$M14_PASS" "$I14"
TM14="$(login_user "$M14" "$M14_PASS")"; accept_all "$TM14"
DEV14="$(mkdev14 "$J-dev-base" "$I14")"

echo "-- 14.1 User (11 alvos, checkpoint em lote)"
UIDS=(); UEMAILS=()
for n in $(seq 1 11); do mkuser STUDENT "$J-u$n-$RUN_ID@test.local" "EJ Aluno $n" "x$(openssl rand -hex 4)" "$I14"; UEMAILS+=("$J-u$n-$RUN_ID@test.local"); UIDS+=("$(uid "$J-u$n-$RUN_ID@test.local")"); track "${UIDS[-1]}"; done
PW_BEFORE="$(msql "select password from users where id=$(hexid "${UIDS[0]}")")"
CP_U="$(new_checkpoint User "${UIDS[@]}")"
[[ "$CP_U" == ERRO* ]] && fail "checkpoint de User ($CP_U)" || ok "checkpoint de 11 usuários criado e aprovado por outro admin"
eq "checkpoint registra 11 alvos" 11 "$(msql "select affected_count from javers_checkpoint where id=$(hexid "$CP_U")")"
eq "gestor altera o nome de U1" 200 "$(http PATCH "/api/v1/users/student/${UIDS[0]}" "$TM14" -d '{"name":"EJ Nome Alterado"}')"
eq "gestor exclui U2 (soft-delete)" 204 "$(http DELETE "/api/v1/users/student/${UIDS[1]}" "$TM14")"
sleep 6
eq "U1 foi alterado" "EJ Nome Alterado" "$(msql "select name from users where id=$(hexid "${UIDS[0]}")")"
eq "U2 foi inativado" 0 "$(msql "select active+0 from users where id=$(hexid "${UIDS[1]}")")"
eq "rollback do checkpoint de User" 200 "$(http POST "/api/v1/admin/javers/rollback/checkpoint/$CP_U" "$TA")"
eq "11 usuários restaurados" 11 "$(jget "len(d)")"
ge "candidateDevices traz o dispositivo da instituição" 1 "$(jget "len(d[0]['candidateDevices'])")"
eq "candidateDevices contém o dispositivo de teste" True "$(jget "'$DEV14' in d[0]['candidateDevices']")"
eq "manualActionRequired instrui verificação manual" True "$(jget "'manualmente' in d[0]['manualActionRequired']")"
eq "U1 voltou ao nome original" "EJ Aluno 1" "$(msql "select name from users where id=$(hexid "${UIDS[0]}")")"
eq "senha de U1 preservada" "$PW_BEFORE" "$(msql "select password from users where id=$(hexid "${UIDS[0]}")")"
eq "U2 voltou a ficar ativo" 1 "$(msql "select active+0 from users where id=$(hexid "${UIDS[1]}")")"
eq "U2 volta anonimizado (anonimização é irreversível)" "deleted_user_${UIDS[1]}@anon.invalid" "$(msql "select email from users where id=$(hexid "${UIDS[1]}")")"
eq "passwordChangedAt atualizado nos 11" 11 "$(msql "select count(*) from users where id in ($(printf "$(hexid %s)," "${UIDS[@]}" | sed 's/,$//')) and password_changed_at is not null")"
eq "tokensRevokedAt atualizado nos 11" 11 "$(msql "select count(*) from users where id in ($(printf "$(hexid %s)," "${UIDS[@]}" | sed 's/,$//')) and tokens_revoked_at is not null")"
eq "checkpoint de User executado" EXECUTED "$(msql "select status from javers_checkpoint where id=$(hexid "$CP_U")")"
echo "-- 14.1b User (rollback individual, auditoria JAVERS_ROLLBACK)"
CP_U1="$(new_checkpoint User "${UIDS[2]}")"
eq "gestor altera o nome de U3" 200 "$(http PATCH "/api/v1/users/student/${UIDS[2]}" "$TM14" -d '{"name":"EJ Outro Nome"}')"
eq "rollback individual de U3" 200 "$(http POST /api/v1/admin/javers/rollback "$TA" -d "{\"checkpointId\":\"$CP_U1\",\"entityType\":\"User\",\"entityId\":\"${UIDS[2]}\"}")"
eq "U3 voltou ao nome original" "EJ Aluno 3" "$(msql "select name from users where id=$(hexid "${UIDS[2]}")")"
ge "RollbackResult.candidateDevices presente" 1 "$(jget "len(d['candidateDevices'])")"

echo "-- 14.2 Device (11 alvos)"
DIDS=(); for n in $(seq 1 11); do DIDS+=("$(mkdev14 "$J-dev-$n" "$I14")"); done
CP_D="$(new_checkpoint DeviceModel "${DIDS[@]}")"
[[ "$CP_D" == ERRO* ]] && fail "checkpoint de Device ($CP_D)" || ok "checkpoint de 11 dispositivos criado e aprovado"
eq "admin desativa D1" 2 "$(http PATCH "/devices/${DIDS[0]}/deactivate" "$TA" | cut -c1-1)"
eq "D1 INACTIVE" INACTIVE "$(msql "select status from device_tb where id=$(hexid "${DIDS[0]}")")"
eq "rollback do checkpoint de Device" 200 "$(http POST "/api/v1/admin/javers/rollback/checkpoint/$CP_D" "$TA")"
eq "D1 voltou a ACTIVE" ACTIVE "$(msql "select status from device_tb where id=$(hexid "${DIDS[0]}")")"
eq "Device não preenche candidateDevices" 0 "$(jget "len(d[0]['candidateDevices'])")"

echo "-- 14.3 Scenario (11 alvos; rename quebra o arquivo)"
SIDS=(); for n in $(seq 1 11); do SIDS+=("$(mksc14 "$J-sc-$n" "descricao original $n")"); done
CP_S="$(new_checkpoint ScenarioModel "${SIDS[@]}")"
[[ "$CP_S" == ERRO* ]] && fail "checkpoint de Scenario ($CP_S)" || ok "checkpoint de 11 cenários criado e aprovado"
eq "admin altera a descrição de SC1 (sem renomear)" 200 "$(http PATCH "/api/v1/scenarios/${SIDS[0]}" "$TA" -d "{\"name\":\"$J-sc-1\",\"description\":\"descricao modificada\"}")"
eq "rollback do checkpoint de Scenario" 200 "$(http POST "/api/v1/admin/javers/rollback/checkpoint/$CP_S" "$TA")"
eq "SC1 voltou à descrição original" "descricao original 1" "$(msql "select description from scenario_tb where id=$(hexid "${SIDS[0]}")")"
CP_S2="$(new_checkpoint ScenarioModel "${SIDS[1]}")"
eq "admin renomeia SC2 (o arquivo é movido)" 200 "$(http PATCH "/api/v1/scenarios/${SIDS[1]}" "$TA" -d "{\"name\":\"$J-sc-novo\",\"description\":\"descricao original 2\"}")"
sleep 1
eq "rollback de SC2 é recusado: o arquivo antigo não existe mais" 409 "$(http POST /api/v1/admin/javers/rollback "$TA" -d "{\"checkpointId\":\"$CP_S2\",\"entityType\":\"ScenarioModel\",\"entityId\":\"${SIDS[1]}\"}")"
eq "mensagem orienta reupar o áudio" True "$(jget "'reupar' in d['message']")"
eq "SC2 continua com o nome novo (nada foi alterado)" "$J-sc-novo" "$(msql "select name from scenario_tb where id=$(hexid "${SIDS[1]}")")"

echo "-- 14.4 Institution (11 alvos; não reativa sozinha)"
INS=(); for n in $(seq 1 11); do INS+=("$(mkinst14 "$J-inst-b$n-$RUN_ID")"); done
CP_I="$(new_checkpoint InstitutionModel "${INS[@]}")"
[[ "$CP_I" == ERRO* ]] && fail "checkpoint de Institution ($CP_I)" || ok "checkpoint de 11 instituições criado e aprovado"
eq "admin inativa I1 (toggle-status)" 204 "$(http PATCH "/api/v1/institutions/${INS[0]}/toggle-status" "$TA")"
eq "I1 inativa" 0 "$(msql "select active+0 from institutions where id=$(hexid "${INS[0]}")")"
msql "update institutions set verification_status='VERIFIED' where id=$(hexid "${INS[0]}")" >/dev/null
eq "rollback do checkpoint de Institution" 200 "$(http POST "/api/v1/admin/javers/rollback/checkpoint/$CP_I" "$TA")"
eq "I1 não foi reativada automaticamente" 0 "$(msql "select active+0 from institutions where id=$(hexid "${INS[0]}")")"
eq "I1 ficou pendente de verificação (RF21)" PENDING_VERIFICATION "$(msql "select verification_status from institutions where id=$(hexid "${INS[0]}")")"
CP_I1="$(new_checkpoint InstitutionModel "${INS[2]}")"
eq "admin inativa I3" 204 "$(http PATCH "/api/v1/institutions/${INS[2]}/toggle-status" "$TA")"
eq "rollback individual de I3" 200 "$(http POST /api/v1/admin/javers/rollback "$TA" -d "{\"checkpointId\":\"$CP_I1\",\"entityType\":\"InstitutionModel\",\"entityId\":\"${INS[2]}\"}")"

echo "-- 14.5 Auditoria dos rollbacks"
sleep 4
eq "GET /api/v1/audit-logs?action=JAVERS_ROLLBACK" 200 "$(http GET '/api/v1/audit-logs?action=JAVERS_ROLLBACK&size=50' "$TA")"
ge "eventos JAVERS_ROLLBACK (rollback individual)" 2 "$(jget "len(d['content'])")"
eq "há rollbacks concluídos (SUCCESS)" True "$(jget "sum(1 for x in d['content'] if x['status']=='SUCCESS') >= 2")"
eq "o rollback recusado do cenário foi registrado como FAILED" True "$(jget "any(x['status']=='FAILED' for x in d['content'])")"
eq "GET /api/v1/audit-logs?action=JAVERS_CHECKPOINT_ROLLBACK" 200 "$(http GET '/api/v1/audit-logs?action=JAVERS_CHECKPOINT_ROLLBACK&size=50' "$TA")"
ge "eventos JAVERS_CHECKPOINT_ROLLBACK (lote)" 4 "$(jget "len(d['content'])")"
ge "eventos JAVERS_CHECKPOINT_CREATED" 6 "$(msql "select count(*) from audit_log where action='JAVERS_CHECKPOINT_CREATED' and status='SUCCESS'")"
eq "tamperedCount após os rollbacks" 0 "$(http GET /api/v1/audit-logs/integrity "$TA" >/dev/null; jget "d['tamperedCount']")"

echo; echo "=== Resumo: OK=$PASS  FALHA=$FAIL ==="
[[ "$FAIL" -eq 0 ]]
