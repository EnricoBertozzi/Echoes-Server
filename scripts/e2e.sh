#!/usr/bin/env bash
# Validação E2E do Echoes-Server contra a stack em Docker Compose.
# Sai com código != 0 se qualquer asserção falhar.
#
# Variáveis opcionais:
#   BASE_URL       (padrão http://localhost:8080)
#   E2E_COMPOSE    comando do compose (padrão "docker compose"), ex.: "docker compose -p echoes-e2e -f docker-compose.yml -f /tmp/override.yml"
#   ADMIN_EMAIL / ADMIN_PASSWORD  (padrão: lidos do .env)
#   RATE_WINDOW    segundos de espera para o balde do rate limit encher (padrão 62)
set -uo pipefail

cd "$(dirname "$0")/.."
BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="${E2E_COMPOSE:-docker compose}"
RATE_WINDOW="${RATE_WINDOW:-62}"
WORK="$(mktemp -d)"
RUN_ID="$(date +%s)"

envval() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | tr -d '"' | tr -d "'"; }
ADMIN_EMAIL="${ADMIN_EMAIL:-$(envval ADMIN_EMAIL)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(envval ADMIN_PASSWORD)}"
ADMIN_EMAIL_LC="$(echo "$ADMIN_EMAIL" | tr 'A-Z' 'a-z')"

PASS=0; FAIL=0; PEND=0
ok()   { PASS=$((PASS+1)); printf '[OK]        %s\n' "$*"; }
fail() { FAIL=$((FAIL+1)); printf '[FALHA]     %s\n' "$*"; }
pend() { PEND=$((PEND+1)); printf '[PENDÊNCIA] %s\n' "$*"; }
eq()   { # eq "descrição" esperado obtido
  if [[ "$2" == "$3" ]]; then ok "$1 (HTTP/valor $3)"; else fail "$1 (esperado $2, obtido $3)"; fi
}

rcli() { $COMPOSE exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning "$@"' _ "$@" | tr -d '\r'; }
msql() { $COMPOSE exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -N -B "$MYSQL_DATABASE" -e "$1" 2>/dev/null' _ "$1" | tr -d '\r'; }

# http MÉTODO CAMINHO TOKEN [curl args...] -> imprime o status; corpo em $WORK/body
http() {
  local m="$1" p="$2" t="$3"; shift 3
  local auth=(); [[ -n "$t" ]] && auth=(-H "Authorization: Bearer $t")
  curl -s -o "$WORK/body" -w '%{http_code}' -X"$m" "$BASE_URL$p" "${auth[@]}" -H 'Content-Type: application/json' "$@"
}
jget() { python3 -c "import json,sys;d=json.load(open('$WORK/body'));print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

# login_user EMAIL SENHA -> imprime o JWT (vazio se falhar); cabeçalhos do /mfa em $WORK/mfa.hdr
login_user() {
  local email="$1" pass="$2" lc code
  lc="$(echo "$email" | tr 'A-Z' 'a-z')"
  curl -s -o /dev/null -XPOST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pass\"}"
  code="$(rcli GET "pending:login:$lc")"
  [[ -z "$code" ]] && return 1
  curl -s -D "$WORK/mfa.hdr" -o /dev/null -XPOST "$BASE_URL/api/v1/auth/mfa" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"code\":\"$code\"}"
  tr -d '\r' < "$WORK/mfa.hdr" | grep -i '^set-cookie: echoes_session=' | sed -E 's/^[^=]*=([^;]*).*/\1/'
}
accept_all() { local t="$1" ty; for ty in TERMS_OF_USE PRIVACY_POLICY COOKIES_POLICY; do http POST /terms/accept "$t" -d "{\"type\":\"$ty\"}" >/dev/null; done; }
wait_rate() { echo "    (aguardando ${RATE_WINDOW}s para o rate limit de IP encher o balde)"; sleep "$RATE_WINDOW"; }

# ---------- dados de teste (removidos no cleanup) ----------
ADMIN_ACCEPT_BEFORE="$(msql "select count(*) from user_terms_acceptance a join users u on u.id=a.user_id where u.email='$ADMIN_EMAIL'")"
ADMIN_REVOKED_BEFORE="$(msql "select coalesce(tokens_revoked_at,'NULL') from users where email='$ADMIN_EMAIL'")"
INST_ID=""; USERS_CREATED=(); USER_IDS=()

cleanup() {
  echo; echo "== Limpeza dos registros de teste =="
  local list="'x'"; local e
  for e in "${USERS_CREATED[@]:-}"; do [[ -n "$e" ]] && list="$list,'$e'"; done
  local idl="UNHEX('00')" uid_; for uid_ in "${USER_IDS[@]:-}"; do [[ -n "$uid_" ]] && idl="$idl,UNHEX(REPLACE('$uid_','-',''))"; done
  msql "delete a from user_terms_acceptance a join users u on u.id=a.user_id where u.email in ($list) or u.id in ($idl)" >/dev/null
  msql "delete from classroom_members where user_id in (select id from users where email in ($list) or id in ($idl))" >/dev/null
  msql "delete from users where email in ($list) or id in ($idl)" >/dev/null
  [[ -n "$INST_ID" ]] && msql "delete from institutions where id=UNHEX(REPLACE('$INST_ID','-',''))" >/dev/null
  # aceites do admin criados por este script (o admin legítimo e o trigger de auditoria não são tocados)
  msql "delete from user_terms_acceptance where user_id=(select id from users where email='$ADMIN_EMAIL') and accepted_at >= from_unixtime($RUN_ID - 5)" >/dev/null
  [[ "$ADMIN_REVOKED_BEFORE" == "NULL" ]] && msql "update users set tokens_revoked_at=NULL, password_changed_at=NULL where email='$ADMIN_EMAIL'" >/dev/null
  for e in "${USERS_CREATED[@]:-}" "$ADMIN_EMAIL_LC"; do
    [[ -n "$e" ]] && for k in attempts:login attempts:mfa attempts:password-validate attempts:password-forgot pending:login; do rcli DEL "$k:$(echo "$e" | tr 'A-Z' 'a-z')" >/dev/null; done
  done
  echo "removidos: ${#USERS_CREATED[@]} usuário(s) de teste, instituição de teste, aceites e contadores do Redis."
  echo "mantidos por desenho: linhas de audit_log (gatilhos audit_log_no_update/no_delete)."
  rm -rf "$WORK"
}
trap cleanup EXIT

mkuser() { # mkuser PAPEL EMAIL NOME SENHA [INSTITUICAO_UUID]
  local role="$1" email="$2" name="$3" pass="$4" inst="${5:-}" hash instsql="NULL"
  hash="$(python3 -c "import bcrypt,sys;print(bcrypt.hashpw(sys.argv[1].encode(),bcrypt.gensalt(10)).decode())" "$pass")"
  [[ -n "$inst" ]] && instsql="UNHEX(REPLACE('$inst','-',''))"
  msql "insert into users (role,id,active,email,login_attempts,name,password,institution_id) values ('$role',UNHEX(REPLACE(UUID(),'-','')),1,'$email',0,'$name','$hash',$instsql)" >/dev/null
  USERS_CREATED+=("$email")
  USER_IDS+=("$(uid "$email")")   # por id: o soft-delete troca o e-mail
}
uid() { msql "select BIN_TO_UUID(id) from users where email='$1'"; }

echo "=== Echoes-Server E2E — $BASE_URL ($(date -Iseconds)) ==="
[[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]] && { echo "ADMIN_EMAIL/ADMIN_PASSWORD ausentes"; exit 2; }

echo; echo "== 1. Autenticação e cookie =="
T1="$(login_user "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
[[ -n "$T1" ]] && ok "login + MFA (código lido no Redis) emitiu echoes_session" || { fail "login + MFA do admin"; exit 1; }
CK="$(tr -d '\r' < "$WORK/mfa.hdr" | grep -i '^set-cookie: echoes_session=')"
[[ "$CK" == *HttpOnly* ]] && ok "cookie com HttpOnly" || fail "cookie sem HttpOnly"
[[ "$CK" == *SameSite=Strict* ]] && ok "cookie com SameSite=Strict" || fail "cookie sem SameSite=Strict"
[[ "$CK" == *Secure* ]] && ok "cookie com Secure" || fail "cookie sem Secure"

echo; echo "== 2. LGPD =="
eq "protegido sem aceite (/api/v1/dashboard/devices) bloqueado" 403 "$(http GET /api/v1/dashboard/devices "$T1")"
[[ "$(jget "d.get('pendingTerms')")" != "None" ]] && ok "403 traz pendingTerms" || fail "403 sem pendingTerms"
accept_all "$T1"
CODE="$(http GET /api/v1/dashboard/devices "$T1")"; [[ "$CODE" != 403 && "$CODE" != 401 ]] && ok "protegido liberado após aceite (HTTP $CODE)" || fail "protegido segue bloqueado (HTTP $CODE)"
eq "L1 exportação GET /api/v1/users/me/export" 200 "$(http GET /api/v1/users/me/export "$T1")"
[[ "$(jget "len(d['termAcceptances'])")" -ge 3 ]] && ok "export traz os aceites do titular" || fail "export sem aceites"
[[ "$(jget "d['profile']['email']")" == "$ADMIN_EMAIL" ]] && ok "export é do próprio titular" || fail "export de outro titular"
eq "L2 revogação POST /terms/revoke" 204 "$(http POST /terms/revoke "$T1")"
eq "L2 token antigo após revogar" 401 "$(http GET /api/v1/users/me "$T1")"
sleep 2
T2="$(login_user "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
[[ -n "$T2" ]] && ok "L3 novo login permitido após revogação" || fail "L3 novo login"
eq "L3 protegido bloqueado até novo aceite" 403 "$(http GET /api/v1/dashboard/devices "$T2")"
[[ "$(jget "len(d['pendingTerms'])")" -ge 1 ]] && ok "L3 pendingTerms preenchido" || fail "L3 pendingTerms vazio"
accept_all "$T2"
CODE="$(http GET /api/v1/dashboard/devices "$T2")"; [[ "$CODE" != 403 && "$CODE" != 401 ]] && ok "L3 liberado após novo aceite (HTTP $CODE)" || fail "L3 segue bloqueado (HTTP $CODE)"

echo; echo "== 3. Integridade da auditoria (S15) =="
eq "GET /api/v1/audit-logs/integrity" 200 "$(http GET /api/v1/audit-logs/integrity "$T2")"
eq "tamperedCount" 0 "$(jget "d['tamperedCount']")"

echo; echo "== 4. B13 — MFA com e-mail nulo =="
eq "POST /api/v1/auth/mfa {email:null}" 400 "$(http POST /api/v1/auth/mfa "" -d '{"email":null,"code":"123456"}')"

wait_rate
echo; echo "== 5. Preparação: instituição, gestor e aluno de teste =="
INST_ID="$(msql "select UUID()")"
msql "insert into institutions (id,acronym,active,cnpj,created_at,delete_token,deleted,email,name,verification_attempts,verification_status) values (UNHEX(REPLACE('$INST_ID','-','')),'E2E',1,'00000000000191',now(),'e2e-$RUN_ID',0,'e2e-inst-$RUN_ID@test.local','E2E Instituicao $RUN_ID',0,'VERIFIED')" >/dev/null
MGR_PASS="Mg$(openssl rand -hex 6)"; BRUTE_PASS="Br$(openssl rand -hex 6)"; MFA_PASS="Mf$(openssl rand -hex 6)"; VIC_PASS="Vc$(openssl rand -hex 6)"
MGR="e2e-mgr-$RUN_ID@test.local"; BRUTE="e2e-brute-$RUN_ID@test.local"; MFAU="e2e-mfa-$RUN_ID@test.local"; VIC="e2e-vitima-$RUN_ID@test.local"
mkuser MANAGER "$MGR" "E2E Gestor" "$MGR_PASS" "$INST_ID"
mkuser STUDENT "$VIC" "Zz E2E Excluido" "$VIC_PASS" "$INST_ID"
mkuser STUDENT "$BRUTE" "E2E Brute" "$BRUTE_PASS" "$INST_ID"
mkuser STUDENT "$MFAU" "E2E Mfa" "$MFA_PASS" "$INST_ID"
[[ -n "$(uid "$MGR")" ]] && ok "usuários de teste criados" || fail "criação de usuários de teste"

echo; echo "== 6. B10 — usuário excluído não aparece para o gestor =="
TM="$(login_user "$MGR" "$MGR_PASS")"; accept_all "$TM"
VIC_ID="$(uid "$VIC")"
eq "gestor lista usuários da instituição (controle)" 200 "$(http GET "/api/v1/institutions/$INST_ID/users?page=0&size=50" "$TM")"
grep -q "$VIC_ID" "$WORK/body" && ok "controle: aluno aparece antes da exclusão" || fail "controle: aluno não aparece antes da exclusão"
DEL="$(http DELETE "/api/v1/users/student/$VIC_ID" "$TM")"
eq "GESTOR exclui aluno da PRÓPRIA instituição (DELETE /api/v1/users/student/{id})" 204 "$DEL"
http GET "/api/v1/institutions/$INST_ID/users?page=0&size=50" "$TM" >/dev/null
grep -q "$VIC_ID" "$WORK/body" && fail "B10: aluno excluído ainda aparece na listagem do gestor" || ok "B10: aluno excluído ausente na listagem"
http GET "/api/v1/institutions/$INST_ID/users/search?name=Excluido&page=0&size=50" "$TM" >/dev/null
grep -q "$VIC_ID" "$WORK/body" && fail "B10: aluno excluído aparece na busca por nome" || ok "B10: aluno excluído ausente na busca por nome"
http GET "/api/v1/institutions?name=E2E+Instituicao+$RUN_ID" "$T2" >/dev/null
grep -q "E2E Instituicao $RUN_ID" "$WORK/body" && ok "controle: instituição aparece na busca" || fail "controle: instituição não aparece"
msql "update institutions set deleted=1 where id=UNHEX(REPLACE('$INST_ID','-',''))" >/dev/null
http GET "/api/v1/institutions?name=E2E+Instituicao+$RUN_ID" "$T2" >/dev/null
grep -q "E2E Instituicao $RUN_ID" "$WORK/body" && fail "B10: instituição excluída aparece na busca" || ok "B10: instituição excluída ausente na busca"

echo; echo "== 7. B4 — auditoria da falha de login e B5 — bloqueio na 5ª falha =="
MARK="$(msql "select date_format(now() - interval 1 second, '%Y-%m-%d %H:%i:%s')")"  # audit_log grava o timestamp truncado ao segundo
eq "senha errada #1 (AuthFailedException responde 400)" 400 "$(http POST /api/v1/auth/login "" -d "{\"email\":\"$BRUTE\",\"password\":\"errada-1\"}")"
sleep 3
NF="$(msql "select count(*) from audit_log where status='FAILED' and action='LOGIN_ATTEMPT' and timestamp >= '$MARK'")"
eq "B4: exatamente 1 linha FAILED com action=LOGIN_ATTEMPT" 1 "$NF"
BAD="$(msql "select count(*) from audit_log where action like '%(%' and timestamp >= '$MARK'")"
eq "B4: nenhuma action malformada (ex.: LOGIN_SUCCESS()" 0 "$BAD"
for i in 2 3 4 5; do http POST /api/v1/auth/login "" -d "{\"email\":\"$BRUTE\",\"password\":\"errada-$i\"}" >/dev/null; done
CODE="$(http POST /api/v1/auth/login "" -d "{\"email\":\"$BRUTE\",\"password\":\"$BRUTE_PASS\"}")"
[[ "$CODE" != 204 ]] && ok "B5: após 5 falhas, login com a senha CERTA já é recusado (HTTP $CODE)" || fail "B5: bloqueio só ocorre depois da 5ª falha (senha certa ainda aceita, HTTP 204)"
echo "    contador Redis attempts:login = $(rcli GET "attempts:login:$(echo "$BRUTE" | tr 'A-Z' 'a-z')")  | lock_until no banco = $(msql "select coalesce(lock_until,'NULL') from users where email='$BRUTE'")"

wait_rate
echo; echo "== 8. B1 — lockout de MFA na 5ª falha =="
curl -s -o /dev/null -XPOST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$MFAU\",\"password\":\"$MFA_PASS\"}"
MFAKEY="pending:login:$(echo "$MFAU" | tr 'A-Z' 'a-z')"; REAL="$(rcli GET "$MFAKEY")"
[[ -n "$REAL" ]] && ok "código MFA gravado no Redis" || fail "código MFA não gravado"
WRONG="000000"; [[ "$REAL" == "000000" ]] && WRONG="111111"
for i in 1 2 3 4 5; do CODE="$(http POST /api/v1/auth/mfa "" -d "{\"email\":\"$MFAU\",\"code\":\"$WRONG\"}")"; echo "    mfa errado #$i -> HTTP $CODE"; done
eq "B1: contador de falhas persistiu no Redis" 5 "$(rcli GET "attempts:mfa:$(echo "$MFAU" | tr 'A-Z' 'a-z')")"
[[ -z "$(rcli GET "$MFAKEY")" ]] && ok "B1: código apagado do Redis na 5ª falha" || fail "B1: código continua no Redis após 5 falhas"
CODE="$(http POST /api/v1/auth/mfa "" -d "{\"email\":\"$MFAU\",\"code\":\"$REAL\"}")"
[[ "$CODE" == 429 || "$CODE" == 401 ]] && ok "B1: 6ª tentativa (inclusive com o código certo) recusada (HTTP $CODE)" || fail "B1: 6ª tentativa aceita (HTTP $CODE)"

echo; echo "== 9. S7 — exposição de portas e documentação =="
PORTS="$($COMPOSE ps --format '{{.Service}} {{.Ports}}' 2>/dev/null)"
for s in mysql redis; do
  L="$(echo "$PORTS" | grep "^$s ")"
  if echo "$L" | grep -qE '0\.0\.0\.0:(3306|6379)|\[::\]:(3306|6379)'; then fail "S7: $s publicado em 0.0.0.0 ($L)"
  elif echo "$L" | grep -q '127.0.0.1:'; then ok "S7: $s só em loopback (${L#* })"
  else ok "S7: $s sem porta publicada"; fi
done
DOCS="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/v3/api-docs")"
[[ "$DOCS" == 200 ]] && pend "S7: /v3/api-docs responde 200 (desabilitar em produção, SWAGGER_ENABLED=false)" || ok "S7: /v3/api-docs fechado (HTTP $DOCS)"

echo; echo "== 10. RNF10 — dado pessoal em audit_log.entity_id =="
EID="$(msql "select entity_id from audit_log where entity_id like '%@%' limit 1")"
if [[ -n "$EID" ]]; then pend "RNF10: entity_id contém e-mail em claro (ex.: ${EID%%@*}@…)"; else ok "RNF10: nenhum entity_id com e-mail em claro"; fi

echo; echo "== 11. RNF04 — upload de ~30 MB =="
dd if=/dev/zero of="$WORK/big.bin" bs=1M count=30 status=none
CODE="$(curl -s -o "$WORK/body" -w '%{http_code}' -XPOST "$BASE_URL/api/v1/classrooms/$(msql 'select UUID()')/materials" \
  -H "Authorization: Bearer $T2" -F 'title=e2e' -F "file=@$WORK/big.bin;type=application/pdf")"
[[ "$CODE" == 413 ]] && ok "RNF04: arquivo de 30 MB recusado com 413" || fail "RNF04: esperado 413, obtido HTTP $CODE ($(head -c 100 "$WORK/body"))"

wait_rate
echo; echo "== 12. S9 — rate limit por IP =="
LAST=0; for i in $(seq 1 12); do LAST="$(curl -s -o /dev/null -w '%{http_code}' -XPOST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"email":"nao-existe@test.local","password":"x"}')"; [[ "$LAST" == 429 ]] && break; done
[[ "$LAST" == 429 ]] && ok "S9: 429 após $i requisições do mesmo IP" || fail "S9: sem 429 em 12 requisições (último HTTP $LAST)"

echo; echo "== 13. Logout =="
eq "POST /api/v1/auth/logout" 204 "$(http POST /api/v1/auth/logout "$T2")"
eq "token após logout" 401 "$(http GET /api/v1/users/me "$T2")"

echo; echo "=== Resumo: OK=$PASS  FALHA=$FAIL  PENDÊNCIA=$PEND ==="
[[ "$FAIL" -eq 0 ]]
