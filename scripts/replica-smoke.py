#!/usr/bin/env python3
"""Verify a live connection on replica B observes commits made through replica A."""
import os,json,uuid,pathlib,urllib.request
root=pathlib.Path(__file__).resolve().parents[1];os.chdir(root)
env=dict(line.split('=',1) for line in pathlib.Path('.env').read_text().splitlines() if line and not line.startswith('#'))
a=os.getenv('BASE_A','http://localhost:8080');b=os.getenv('BASE_B','http://localhost:8081')
def call(base,path,body=None,token=None,key=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    if key:headers['Idempotency-Key']=key
    req=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    with urllib.request.urlopen(req,timeout=10) as response:return json.load(response)
def snapshot(stream):
    lines=[]
    while True:
        raw=stream.readline()
        if not raw:raise RuntimeError('SSE closed')
        line=raw.decode().strip()
        if line.startswith('data:'):lines.append(line[5:])
        if not line and lines:return json.loads('\n'.join(lines))
        if not line and stream.closed:raise RuntimeError('SSE closed')
token=call(a,'/local/token',{'username':'alice','password':env['LOCAL_CUSTOMER_PASSWORD']})['access_token']
show=call(a,'/api/shows',token=token)[0]['id']
request=urllib.request.Request(b+'/api/live/shows/'+show,headers={'Authorization':'Bearer '+token})
booking=None
try:
    with urllib.request.urlopen(request,timeout=10) as stream:
        initial=snapshot(stream)
        seat=next(s['label'] for s in initial if s['status']=='AVAILABLE')
        booking=call(a,'/api/bookings',{'showId':show,'seats':[seat]},token,str(uuid.uuid4()))
        for _ in range(4):
            current=snapshot(stream)
            if next(s for s in current if s['label']==seat)['status']=='HELD':break
        else:raise AssertionError('replica B did not observe replica A hold')
        assert all('owner' not in s for s in current)
    call(a,'/api/bookings/'+booking['id']+'/cancel',{},token)
    request.add_header('Last-Event-ID','missed-events')
    with urllib.request.urlopen(request,timeout=10) as stream:
        refreshed=snapshot(stream)
        assert next(s for s in refreshed if s['label']==seat)['status']=='AVAILABLE'
    print('PASS: connected replica B receives replica A commits and refreshes correctly after reconnect')
finally:
    if booking:call(a,'/api/bookings/'+booking['id']+'/cancel',{},token)
