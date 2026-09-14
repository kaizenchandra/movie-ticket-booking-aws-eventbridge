#!/usr/bin/env python3
"""Real JVM restart test. Requires packaged jar, local keys and a running local PostgreSQL."""
import os,json,time,subprocess,urllib.request,uuid,pathlib
root=pathlib.Path(__file__).resolve().parents[1]
os.chdir(root)
env=os.environ.copy()
for line in pathlib.Path('.env').read_text().splitlines():
    if line and not line.startswith('#'):
        key,value=line.split('=',1);env.setdefault(key,value)
env.update(SPRING_PROFILES_ACTIVE='local',WORKERS_AWS_ENABLED='false',AWS_ACCESS_KEY_ID='test',AWS_SECRET_ACCESS_KEY='test',AWS_ENDPOINT='http://localhost:4566',SERVER_PORT='18080',BOOKING_HOLD_SECONDS='3')
java=str(pathlib.Path(env['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in env else 'java'
base='http://localhost:18080';process=None
def request(path,body=None,token=None,key=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    if key:headers['Idempotency-Key']=key
    req=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    with urllib.request.urlopen(req,timeout=10) as r:return json.load(r)
def start(log):
    p=subprocess.Popen([java,'-jar','target/aws-eventbridge-demo-1.0.0-SNAPSHOT.jar'],env=env,stdout=log,stderr=subprocess.STDOUT)
    for _ in range(100):
        if p.poll() is not None:raise RuntimeError('app exited; see target/restart-smoke.log')
        try:
            request('/actuator/health/readiness');return p
        except Exception:time.sleep(.2)
    p.terminate();p.wait(timeout=40);raise RuntimeError('startup timed out')
def stop(p):p.terminate();p.wait(timeout=40)
with open('target/restart-smoke.log','w') as log:
    try:
        process=start(log)
        token=request('/local/token',{'username':'alice','password':env['LOCAL_CUSTOMER_PASSWORD']})['access_token']
        show=request('/api/shows',token=token)[0]['id']
        seats=request('/api/shows/'+show+'/seats',token=token)
        seat=next(s['label'] for s in reversed(seats) if s['status']=='AVAILABLE')
        booking=request('/api/bookings',{'showId':show,'seats':[seat]},token,str(uuid.uuid4()))
        stop(process);process=None
        time.sleep(4)
        process=start(log)
        replacement=request('/api/bookings',{'showId':show,'seats':[seat]},token,str(uuid.uuid4()))
        assert replacement['id']!=booking['id']
        previous=request('/api/bookings/'+booking['id'],token=token)
        assert previous['status']=='EXPIRED',previous
        request('/api/bookings/'+replacement['id']+'/cancel',{},token)
        print('PASS: persisted hold expires across a real JVM shutdown/restart; same seat re-reserved')
    finally:
        if process is not None:stop(process)
