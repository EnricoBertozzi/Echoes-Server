#!/usr/bin/env bash
# Gera mosquitto/config/passwd a partir de MQTT_USERNAME / MQTT_PASSWORD do .env (S5).
# Uso: ./mosquitto/create-passwd.sh   (na raiz do repositório)
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo 'ERRO: .env não encontrado.' >&2; exit 1; }
set -a; source .env; set +a
: "${MQTT_USERNAME:?MQTT_USERNAME vazio}" "${MQTT_PASSWORD:?MQTT_PASSWORD vazio}"
out=mosquitto/config/passwd
rm -f "$out"
if command -v mosquitto_passwd >/dev/null; then
  mosquitto_passwd -b -c "$out" "$MQTT_USERNAME" "$MQTT_PASSWORD"
else
  docker run --rm -v "$PWD/mosquitto/config:/work" eclipse-mosquitto:2 \
    mosquitto_passwd -b -c /work/passwd "$MQTT_USERNAME" "$MQTT_PASSWORD"
fi
# o broker roda como usuário "mosquitto" no container e precisa conseguir ler o arquivo
chmod 644 "$out"
echo "Arquivo $out gerado para o usuário $MQTT_USERNAME."
