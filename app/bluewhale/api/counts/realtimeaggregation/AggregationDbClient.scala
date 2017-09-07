package bluewhale.api.counts.realtimeaggregation

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import bluewhale.api.classifiedstate.ClassifiedState
import AggregationDbClient.ClassifiedStateData
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ProvisionedThroughputExceededException, QueryRequest, QueryResult}
import com.google.inject.Inject

import scala.collection.JavaConversions._
import scala.concurrent.{Future, Promise}
import scala.util.Try
class AggregationDbClient @Inject()(
                                     asyncClient: AmazonDynamoDBAsync
                                   ) {

  def loadCustomerGuids(customerId: Long): Future[Seq[ClassifiedStateData]] =
    loadCustomerGuids(customerId, Promise[Seq[ClassifiedStateData]])

  private def loadCustomerGuids(customerId: Long, promise: Promise[Seq[ClassifiedStateData]]): Future[Seq[ClassifiedStateData]] = {
    asyncClient.queryAsync(new QueryRequest()
      .withTableName("customer-classifieds")
      .withKeyConditionExpression("CustomerId = :hashKey")
        .withExpressionAttributeValues(Map(
          ":hashKey" -> new AttributeValue().withN(customerId.toString)
        )),

      new AsyncHandler[QueryRequest, QueryResult] {
        override def onError(exception: Exception): Unit =
          exception match {
            case e: ProvisionedThroughputExceededException => loadCustomerGuids(customerId, promise)
            case e: ClientExecutionTimeoutException => loadCustomerGuids(customerId, promise)
            case e => promise.failure(e)
          }

        override def onSuccess(request: QueryRequest, result: QueryResult): Unit = {
          promise.success(extractClassifiedData(result))
        }
      })

    promise.future
  }

  private def extractClassifiedData(result: QueryResult): Seq[ClassifiedStateData] = {
    def getData(map: Map[String, AttributeValue]): Option[ClassifiedStateData] = {
      for (
        guid <- map.get("ClassifiedGuid").map(_.getS);
        date <- map.get("Date").map(_.getS).flatMap(s => Try(LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)).toOption);
        state <- map.get("State").map(_.getS).map(ClassifiedState.apply)
      ) yield ClassifiedStateData(guid, date, state)
    }
    result.getItems.toList.map(_.toMap).flatMap(getData)
  }

}

object AggregationDbClient {

  case class ClassifiedStateData(guid: String, date: LocalDate, state: ClassifiedState)

}
