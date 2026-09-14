import http from 'k6/http';
import {check,sleep} from 'k6';
import {Rate,Trend} from 'k6/metrics';
const conflicts=new Rate('reservation_conflicts');const limited=new Rate('rate_limited');const browseLatency=new Trend('browse_success_latency',true);const holdLatency=new Trend('hold_latency',true);
export const options={scenarios:{browse:{executor:'constant-vus',vus:10,duration:__ENV.DURATION||'30s',exec:'browse'},contention:{executor:'shared-iterations',vus:10,iterations:100,exec:'contention'}},summaryTrendStats:['avg','p(50)','p(95)','p(99)','max']};
const base=__ENV.BASE_URL||'http://localhost:8080';const headers={Authorization:'Bearer '+__ENV.TOKEN,'Content-Type':'application/json'};
export function browse(){const r=http.get(base+'/api/shows',{headers});limited.add(r.status===429);if(r.status===200)browseLatency.add(r.timings.duration);check(r,{'browse 200':r=>r.status===200});sleep(Number(__ENV.BROWSE_PAUSE||'0.2'));}
export function contention(){const r=http.post(base+'/api/bookings',JSON.stringify({showId:__ENV.SHOW_ID,seats:[__ENV.SEAT||'A1']}),{headers:{...headers,'Idempotency-Key':`load-${__VU}-${__ITER}-${__ENV.RUN_ID}`}});limited.add(r.status===429);holdLatency.add(r.timings.duration);conflicts.add(r.status===409);check(r,{'reserved or conflict':r=>r.status===200||r.status===409});}
