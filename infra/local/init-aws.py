#!/usr/bin/env python3
"""Idempotent LocalStack ready hook; separate routing and processing DLQs."""
import boto3, json
boto3.setup_default_session(aws_access_key_id="test",aws_secret_access_key="test",region_name="us-east-1")
sqs=boto3.client('sqs',region_name='us-east-1',endpoint_url='http://localhost:4566')
events=boto3.client('events',region_name='us-east-1',endpoint_url='http://localhost:4566')
def queue(name,attributes):
    url=sqs.create_queue(QueueName=name)['QueueUrl']
    sqs.set_queue_attributes(QueueUrl=url,Attributes=attributes)
    arn=sqs.get_queue_attributes(QueueUrl=url,AttributeNames=['QueueArn'])['Attributes']['QueueArn']
    return url,arn
consumer_dlq,consumer_arn=queue('cinema-consumer-dlq',{'MessageRetentionPeriod':'1209600'})
target_dlq,target_arn=queue('cinema-target-dlq',{'MessageRetentionPeriod':'1209600'})
url,arn=queue('cinema-notifications',{'VisibilityTimeout':'60','ReceiveMessageWaitTimeSeconds':'5','MessageRetentionPeriod':'345600','RedrivePolicy':json.dumps({'deadLetterTargetArn':consumer_arn,'maxReceiveCount':'5'})})
try: events.create_event_bus(Name='cinema')
except events.exceptions.ResourceAlreadyExistsException: pass
rule=events.put_rule(Name='booking-notifications',EventBusName='cinema',EventPattern=json.dumps({'source':['cinema.booking'],'detail-type':['BookingChanged.v1']}))['RuleArn']
for q,a in [(url,arn),(target_dlq,target_arn)]:
    sqs.set_queue_attributes(QueueUrl=q,Attributes={'Policy':json.dumps({'Version':'2012-10-17','Statement':[{'Effect':'Allow','Principal':{'Service':'events.amazonaws.com'},'Action':'sqs:SendMessage','Resource':a,'Condition':{'ArnEquals':{'aws:SourceArn':rule}}}]})})
sqs.set_queue_attributes(QueueUrl=consumer_dlq,Attributes={'RedriveAllowPolicy':json.dumps({'redrivePermission':'byQueue','sourceQueueArns':[arn]})})
r=events.put_targets(Rule='booking-notifications',EventBusName='cinema',Targets=[{'Id':'notifications','Arn':arn,'DeadLetterConfig':{'Arn':target_arn},'RetryPolicy':{'MaximumRetryAttempts':10,'MaximumEventAgeInSeconds':3600}}])
assert r['FailedEntryCount']==0,r
print('Cinema EventBridge and SQS initialized')
