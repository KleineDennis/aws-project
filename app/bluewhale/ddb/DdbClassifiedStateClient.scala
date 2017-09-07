package bluewhale.ddb

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import bluewhale.api.classifiedstate.{ClassifiedHistoryRow, ClassifiedState}
import bluewhale.api.counts.MiaTier
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ProvisionedThroughputExceededException, QueryRequest, QueryResult}
import com.google.inject.Inject

import scala.collection.JavaConversions._
import scala.concurrent.{Future, Promise}
import scala.util.Try

class DdbClassifiedStateClient @Inject()(asyncClient: AmazonDynamoDBAsync) {

  private val TableName = "classified-state-history"
  private val TimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")


  def getHistoryRows(classifiedGuid: String): Future[Seq[ClassifiedHistoryRow]] =
    getHistoryRows(classifiedGuid, Promise[Seq[ClassifiedHistoryRow]])

  private def getQuery(classifiedGuid: String): QueryRequest =
    new QueryRequest(TableName)
      .withKeyConditionExpression("ClassifiedGuid = :guid")
      .withExpressionAttributeValues(Map(
        ":guid" -> new AttributeValue().withS(classifiedGuid)
      ))
      .withScanIndexForward(true)

  private def getHistoryRows(classifiedGuid: String, promise: Promise[Seq[ClassifiedHistoryRow]]): Future[Seq[ClassifiedHistoryRow]] = {
    asyncClient.queryAsync(getQuery(classifiedGuid), new AsyncHandler[QueryRequest, QueryResult] {
      override def onError(exception: Exception): Unit =
        exception match {
          case _: ProvisionedThroughputExceededException => getHistoryRows(classifiedGuid, promise)
          case _: ClientExecutionTimeoutException => getHistoryRows(classifiedGuid, promise)
          case ex => promise.failure(ex)
        }


      override def onSuccess(request: QueryRequest, result: QueryResult): Unit =
        promise.success(extractHistoryRows(result))
    })
    promise.future
  }

  private def extractHistoryRows(result: QueryResult): Seq[ClassifiedHistoryRow] =
    result.getItems.toSeq
      .map(_.toMap)
      .flatMap { attrMap =>
        for (
          guid <- attrMap.get("ClassifiedGuid").map(_.getS);
          ts <- attrMap.get("Timestamp").map(_.getS).flatMap(str => Try(OffsetDateTime.parse(str, TimestampFormatter)).toOption);
          state <- attrMap.get("State").map(_.getS);
          miaTier <- Option(attrMap.get("MiaTier").map(_.getS).flatMap(MiaTier.apply).getOrElse(MiaTier.MiaBasic))
        ) yield ClassifiedHistoryRow(guid, ts, ClassifiedState(state), miaTier)
      }
}
