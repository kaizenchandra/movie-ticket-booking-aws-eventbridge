#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a
base=${BASE_URL:-http://localhost:8080}
get_token() { curl -fsS "$base/local/token" -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["access_token"])'; }
admin=$(get_token admin "$LOCAL_ADMIN_PASSWORD")
export TOKEN=$(get_token alice "$LOCAL_CUSTOMER_PASSWORD")
create() { curl -fsS "$base/api/admin/$1" -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' -d "$2" | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])'; }
movie=$(create movies '{"title":"Load fixture","durationMinutes":100}')
cinema=$(create cinemas '{"name":"Load fixture","city":"Loadtest"}')
screen=$(create screens "{\"cinemaId\":\"$cinema\",\"name\":\"Load\",\"seats\":[\"A1\",\"A2\"]}")
body=$(python3 - "$movie" "$screen" <<'PY'
import datetime,json,sys
start=datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=1)
print(json.dumps(dict(movieId=sys.argv[1],screenId=sys.argv[2],startsAt=start.isoformat(),endsAt=(start+datetime.timedelta(hours=2)).isoformat(),priceMinor=1000,currency='INR')))
PY
)
export SHOW_ID=$(create shows "$body")
scripts/load.sh
