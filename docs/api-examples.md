# Example requests

Use `scripts/smoke.sh` for an executable booking/refund walkthrough. These examples assume `.env` has been generated and
the stack is running. Do not paste JWTs or secrets into logs or tickets.

```sh
set -a; . ./.env; set +a
TOKEN=$(curl -fsS localhost:8080/local/token -H 'Content-Type: application/json' \
  -d "{\"username\":\"alice\",\"password\":\"$LOCAL_CUSTOMER_PASSWORD\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
curl -fsS localhost:8080/api/movies -H "Authorization: Bearer $TOKEN"
curl -fsS 'localhost:8080/api/shows?city=Bengaluru&size=20' -H "Authorization: Bearer $TOKEN"
curl -fsS localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: example-reservation-001' -H 'Content-Type: application/json' \
  -d '{"showId":"00000000-0000-0000-0000-000000000004","seats":["A1","A2"]}'
# Set BOOKING_ID from the response:
curl -fsS "localhost:8080/api/bookings/$BOOKING_ID/payment" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"mode":"SUCCESS"}'
curl -fsS "localhost:8080/api/bookings/$BOOKING_ID/tickets" -H "Authorization: Bearer $TOKEN"
curl -N localhost:8080/api/live/bookings -H "Authorization: Bearer $TOKEN"
```

Admin tokens use username `admin` and `$LOCAL_ADMIN_PASSWORD`. Create resources in movie → cinema → screen/seats → show
order. Example screen body: `{"cinemaId":"<uuid>","name":"Screen 2","seats":["A1","A2","A3"]}`. Show body contains
movieId, screenId, startsAt/endsAt in RFC3339 UTC, priceMinor and currency. Intervals on one screen cannot overlap.
Existing show/booking history prevents destructive layout changes.

Local callback verification signs `timestamp + "." + exact UTF-8 body` with HMAC-SHA256 using PAYMENT_CALLBACK_SECRET
and supplies hexadecimal `X-Signature` and epoch-seconds `X-Timestamp`. Timestamps outside ±300 seconds are rejected.
Example Python:

```python
import json,time,hmac,hashlib,os,urllib.request
body=json.dumps({'bookingId':os.environ['BOOKING_ID'],'success':True},separators=(',',':')).encode()
timestamp=str(int(time.time()))
signature=hmac.new(os.environ['PAYMENT_CALLBACK_SECRET'].encode(),timestamp.encode()+b'.'+body,hashlib.sha256).hexdigest()
request=urllib.request.Request('http://localhost:8080/api/simulator/callback',data=body,
 headers={'Content-Type':'application/json','X-Timestamp':timestamp,'X-Signature':signature})
print(urllib.request.urlopen(request).status)
```

Callbacks only apply to an initiated payment. The simulator first records the provider settlement independently and then
applies the booking transition, so callback-driven payments remain refundable even before the regular charge worker has
run. This endpoint is isolated to local/test profiles. It does not represent a production provider's signature contract.
Repeated verified callbacks use the same terminal state guards as reconciliation; the provider's fixed booking
idempotency key prevents duplicate charges. Never assume a generic HMAC endpoint is sufficient for a real provider
integration.
