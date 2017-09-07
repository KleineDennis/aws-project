# General Instructions

## Kibana

### Common Queries

#### Find all Exceptions and Errors

```
(_exists_:exception || meta_name:*xception* || (meta_name:"system-log" && message:*ERROR*)) && meta_stackname: $STACK_NAME-*
```

## Kinesis Streams

### Cloudwatch Metrics

#### (Read/Write)ProvisionedThroughputExceeded

If the read/write capacity for a stream is exceeded, the producers/consumers for that stream receive an `ProvisionedThroughputExceeded` exception. Depending on the producer/consumer it may retry the put/read or fail so that number should always be near zero.

#### Maximum IteratorAgeMilliseconds

The iterator age is the difference between the timestamp of the last event that was returned by an shard iterator and the timestamp of the oldest event in the stream. A value of zero (or near zero) for the maximum age indicates that the consumers of the stream are caught up with the stream. 

### Scaling Streams

Scaling streams is only possible using the AWS API, not via AWS Console/Cloudformation. There are tools doing the heavy lifting for you:

```
git clone git@github.com:awslabs/amazon-kinesis-scaling-utils.git
```

To check the distribution/size of the keyspace us the `report` action:
 
```
java -cp dist/KinesisScalingUtils-complete.jar -Dstream-name=$STREAM_NAME -Dscaling-action=report -Dregion=eu-west-1 ScalingClient
Scaling Operation Complete
Shard shardId-000000000000 - Start: 0, End: 340282366920938463463374607431768211455, Keyspace Width: 340282366920938463463374607431768211455 (100.000%)
```

The keyspace should always be evenly distributed over the whole keyspace size. To scale up/down by a number of shards call (where `COUNT` is the number of shards to add):

```
java -cp dist/KinesisScalingUtils-complete.jar -Dstream-name=$STREAM_NAME -Dscaling-action=scaleUp -Dcount=$COUNT -Dregion=eu-west-1 ScalingClient
Aug 02, 2016 8:40:33 AM com.amazonaws.services.kinesis.scaling.StreamScaler scaleStream
INFO: Scaling Stream tracking-pixel-events from 1 Shards to 2
Aug 02, 2016 8:41:08 AM com.amazonaws.services.kinesis.scaling.StreamScaler reportProgress
INFO: Shard Modification 50% Complete, (1 Pending, 1 Completed). Current Size 2 Shards with Approx 68 Seconds Remaining
Scaling Operation Complete
Shard shardId-000000000001 - Start: 0, End: 170141183460469231731687303715884105726, Keyspace Width: 170141183460469231731687303715884105726 (50.000%)
Shard shardId-000000000002 - Start: 170141183460469231731687303715884105727, End: 340282366920938463463374607431768211455, Keyspace Width: 170141183460469231731687303715884105728 (50.000%)
```

### Debug Streams

To debug the content for a Kinesis stream install [awstools](https://github.com/sam701/awstools).
Here is the configuration (`$HOME/.config/awstools/config.toml`) for AS24:
```toml
defaultRegion = "eu-west-1"
defaultKmsKey = "arn:aws:kms:eu-west-1:149777587479:key/eb76a1bc-3ef5-425e-9488-bf2e106b9bc9"
keyRotationIntervalMinutes = 10080

[profiles]
mainAccount = "as24iam"
mainAccountMfaSession = "as24iam_mfa"

[accounts]
as24dev = "544725753551"
as24iam = "149777587479"
as24prod = "037251718545"
as24cd = "561554673749"
as24tools = "561554673749"
as24logs = "514996828637"
autoscout24 = "635593225576"
as24backup = "488824940513"
as24audit = "854699613297"
as24billing = "727077774615"
as24data = "779680513405"
as24autotrader = "555008484638"
```

After assuming the appropriate roles for the account in order to list all available streams:

```
awstools kinesis --list-streams
```

and to dump all stream events to the console:

```
awstools kinesis --search-stream $STREAM_NAME
```

you can also filter the stream if you are looking for an event containing a specific string:


```
awstools kinesis --search-stream $STREAM_NAME --pattern $CASE_SENSITIVE_TEXT
```


# Lambda

## Alarms

### TooManyThrottlesAlarm

Lambda invocation was throttled due to invocation rates exceeding the customer’s concurrent limits. For AWS Lambda limits see [Lambda Function Concurrent Executions](http://docs.aws.amazon.com/lambda/latest/dg/concurrent-executions.html)

### TooManyErrorsAlarm

To many Lambda invocations didn't return successful, for an explanation of the retry behaviour of the Lambdas see [Retries on Errors](http://docs.aws.amazon.com/lambda/latest/dg/retries-on-errors.html)
