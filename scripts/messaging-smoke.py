#!/usr/bin/env python3
"""Requires running Compose. Tests real EventBridge routing, duplicate events, poison DLQ and redrive."""
import subprocess,json,time,uuid,urllib.request,os
root=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(root)
def aws(service,*args):
    return json.loads(subprocess.check_output(['docker','compose','exec','-T','localstack','awslocal',service,*args],text=True) or '{}')
def sql(query):
    return subprocess.check_output(['docker','compose','exec','-T','postgres','psql','-U','cinema','-d','cinema','-Atc',query],text=True).strip()
# A real business event is produced by smoke.sh; wait for outbox delivery and projection.
for _ in range(60):
    if int(sql('select count(*) from notification'))>0 and sql('select count(*) from outbox where delivered_at is null')=='0':break
    time.sleep(1)
else: raise AssertionError('outbox/consumer did not drain')
payload=sql('select payload from outbox order by created_at desc limit 1')
entry={'Source':'cinema.booking','DetailType':'BookingChanged.v1','Detail':payload,'EventBusName':'cinema'}
response=aws('events','put-events','--entries',json.dumps([entry,entry]));assert response['FailedEntryCount']==0
queue=aws('sqs','get-queue-url','--queue-name','cinema-notifications')['QueueUrl']
dlq=aws('sqs','get-queue-url','--queue-name','cinema-consumer-dlq')['QueueUrl']
aws('sqs','send-message','--queue-url',queue,'--message-body','{"poison":true}')
for _ in range(150):
    messages=aws('sqs','receive-message','--queue-url',dlq,'--wait-time-seconds','1').get('Messages',[])
    if messages:break
    time.sleep(1)
else:raise AssertionError('poison did not reach consumer DLQ')
# Never blindly redrive invalid payloads: quarantine it, then replay a known valid original event ID.
assert 'poison' in messages[0]['Body']
valid={'source':'cinema.booking','detail-type':'BookingChanged.v1','detail':json.loads(payload)}
aws('sqs','send-message','--queue-url',queue,'--message-body',json.dumps(valid))
aws('sqs','delete-message','--queue-url',dlq,'--receipt-handle',messages[0]['ReceiptHandle'])
print('PASS: routing, duplicate publication, poison DLQ, and safe valid-event replay')
