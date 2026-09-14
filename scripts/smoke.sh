#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a
base=${BASE_URL:-http://localhost:8080}
token=$(curl -fsS "$base/local/token" -H 'Content-Type: application/json' -d "{\"username\":\"alice\",\"password\":\"$LOCAL_CUSTOMER_PASSWORD\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
api() { curl -fsS "$base$1" -H "Authorization: Bearer $token" "${@:2}"; }
show=$(api /api/shows | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')
seat=$(api "/api/shows/$show/seats" | python3 -c 'import sys,json;print(next(s["label"] for s in json.load(sys.stdin) if s["status"]=="AVAILABLE"))')
key=$(python3 -c 'import uuid;print(uuid.uuid4())')
booking=$(api /api/bookings -H 'Content-Type: application/json' -H "Idempotency-Key: $key" -d "{\"showId\":\"$show\",\"seats\":[\"$seat\"]}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
api "/api/bookings/$booking/payment" -H 'Content-Type: application/json' -d '{"mode":"SUCCESS"}' >/dev/null
for n in {1..30}; do
  state=$(api "/api/bookings/$booking" | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')
  [[ "$state" == CONFIRMED ]] && break
  sleep 1
done
[[ "$state" == CONFIRMED ]]
api "/api/bookings/$booking/tickets" | python3 -c 'import sys,json; t=json.load(sys.stdin); assert len(t)==1 and not t[0]["revoked"];print("PASS: confirmed ticket",t[0]["id"])'
api "/api/bookings/$booking/cancel" -X POST >/dev/null
for n in {1..30}; do
  state=$(api "/api/bookings/$booking" | python3 -c 'import sys,json;print(json.load(sys.stdin)["refund"])')
  [[ "$state" == SUCCEEDED ]] && break
  sleep 1
done
[[ "$state" == SUCCEEDED ]]
printf 'PASS: cancellation and asynchronous refund\n'
