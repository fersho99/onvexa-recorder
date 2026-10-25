#!/usr/bin/env bash
# Ejemplos manuales. Requieren funciones desplegadas, una sesion de usuario y cuota IA.
# Ejecutar con bash (Git Bash/WSL). No incluir claves en este archivo.
set -euo pipefail
: "${SUPABASE_URL:?Falta SUPABASE_URL}"
: "${SUPABASE_PUBLISHABLE_KEY:?Falta clave publica}"
: "${USER_JWT:?Falta JWT de usuario autenticado}"
: "${RECORDING_ID:?Falta UUID de una grabacion propia ya sincronizada}"
: "${IMAGE_PATH:?Falta ruta de una imagen propia en attachments}"

call_ai() {
  curl --fail-with-body --silent --show-error \
    "$SUPABASE_URL/functions/v1/ai" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
    -H "Authorization: Bearer $USER_JWT" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $1" \
    --data-binary "$2"
  printf '\n'
}

# Cambiar las claves de idempotencia para solicitar nuevas generaciones.
call_ai example-summary-1 "{\"task\":\"summary\",\"language\":\"es\",\"recordingId\":\"$RECORDING_ID\",\"templateId\":\"00000000-0000-4000-8000-000000000001\"}"
call_ai example-map-1 "{\"task\":\"mind_map\",\"language\":\"es\",\"recordingId\":\"$RECORDING_ID\"}"
call_ai example-vision-1 "{\"task\":\"vision\",\"language\":\"es\",\"recordingId\":\"$RECORDING_ID\",\"imagePath\":\"$IMAGE_PATH\",\"positionMs\":0}"
call_ai example-chat-1 "{\"task\":\"chat\",\"language\":\"es\",\"recordingId\":\"$RECORDING_ID\",\"question\":\"Que decisiones se tomaron?\",\"history\":[]}"
call_ai example-email-1 "{\"task\":\"followup_email\",\"language\":\"es\",\"recordingId\":\"$RECORDING_ID\"}"
