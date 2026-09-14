#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p .local
if [[ ! -f .local/private.der ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local/private.pem 2>/dev/null
  openssl pkcs8 -topk8 -nocrypt -in .local/private.pem -outform DER -out .local/private.der
  openssl pkey -in .local/private.pem -pubout -outform DER -out .local/public.der
  rm .local/private.pem
fi
if [[ ! -f .env ]]; then
  for name in DB_PASSWORD LOCAL_CUSTOMER_PASSWORD LOCAL_ADMIN_PASSWORD PAYMENT_CALLBACK_SECRET; do
    printf '%s=%s\n' "$name" "$(openssl rand -hex 24)" >> .env
  done
fi
# Container runs as an unprivileged UID. Local keys are demo-only and directory stays private on host.
chmod 755 .local
chmod 644 .local/*.der
printf 'Local keys and random credentials ready. Export LOCALSTACK_AUTH_TOKEN before docker compose up.\n'
