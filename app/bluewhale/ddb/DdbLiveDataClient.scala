package bluewhale.ddb

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import com.google.inject.Inject

import scala.concurrent.{ExecutionContext, Future}


case class ListingLivedata(classifiedGuid: String, watchlistCount: Long )

class DdbLiveDataClient @Inject()(asyncClient: AmazonDynamoDBAsync)(implicit ec: ExecutionContext) {

  private val tablename = "listing-livedata"

  private def queryCall(r: QueryRequest, h: AsyncHandler[QueryRequest, QueryResult]): Unit = asyncClient.queryAsync(r, h)

  def read(classifiedGuid: String): Future[ListingLivedata] = {
    import scala.collection.JavaConversions._
    val queryRequest: QueryRequest = new QueryRequest(tablename)
      .withKeyConditionExpression("ListingGuid = :guid")
      .withExpressionAttributeValues(Map(":guid" -> new AttributeValue(classifiedGuid)))

    AwsCaller.callWithDefaultRetryCount(queryRequest, queryCall)
      .map(result => ListingLivedata(classifiedGuid, result.getItems.headOption.map(x => x("WatchlistCount").getN.toLong).getOrElse(0)))
  }
}

