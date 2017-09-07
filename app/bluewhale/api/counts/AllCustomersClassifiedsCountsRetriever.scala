package bluewhale.api.counts

import bluewhale.api.classifiedstate.{ClassifiedState, ClassifiedStateCalculator, ClassifiedStates}
import bluewhale.api.counts.AllCustomersClassifiedsCountsRetriever._
import bluewhale.ddb.{DdbClassifiedStateClient, AwsCaller, DbConstants}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryRequest, QueryResult}
import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json, Writes}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class AllCustomersClassifiedsCountsRetriever @Inject()(asyncClient: AmazonDynamoDBAsync,
                                                       classifiedStateClient: DdbClassifiedStateClient) {

  def getAllClassifiedsCountsAsJson(customerId: Long): Future[JsValue] =
    getAllClassifiedsCounts(customerId).map(customerClassifiedsTotalsWrites.writes)

  private def getAllClassifiedsCounts(customerId: Long): Future[CustomerClassifiedsTotals] =
    getCustomerClassifieds(customerId)
      .flatMap(getAllClassifiedsTotals)
      .map(totals => CustomerClassifiedsTotals(customerId, totals))


  private def getCustomerClassifieds(customerId: Long): Future[Set[String]] =
    AwsCaller.callWithDefaultRetryCount(getCustomerClassifiedsRequest(customerId), queryCall)
      .map(extractClassifiedGuids)


  private def extractClassifiedGuids(result: QueryResult): Set[String] =
    result
      .getItems
      .map(_.toMap)
      .filterNot(isDeleted)
      .map(_ ("ClassifiedGuid").getS)
      .toSet

  private def isDeleted(attributes: Map[String, AttributeValue]): Boolean =
    attributes
      .get("State")
      .map(_.getS)
      .map(ClassifiedState.apply)
      .exists(state => state == ClassifiedStates.MarkedForDeletion || state == ClassifiedStates.Deleted)

  private def queryCall(r: QueryRequest, h: AsyncHandler[QueryRequest, QueryResult]): Unit = asyncClient.queryAsync(r, h)

  private def getCustomerClassifiedsRequest(customerId: Long): QueryRequest =
    new QueryRequest()
      .withTableName("customer-classifieds")
      .withKeyConditionExpression("CustomerId = :cid")
      .withExpressionAttributeValues(Map(
        ":cid" -> new AttributeValue().withN(customerId.toString)
      ))

  private def getClassifiedTotals(classifiedGuid: String): Future[ClassifiedsTotalCounts] = {
    var counts = Map.empty[String, Long]
    var activeDaysInStock = 0
    Future.sequence(Seq(
      getClassifiedTotalCounts(classifiedGuid).map(v => counts = v),
      getClassifiedActiveDaysInStock(classifiedGuid).map(v => activeDaysInStock = v)
    )).map(_ =>
      ClassifiedsTotalCounts(classifiedGuid, counts, activeDaysInStock)
    )
  }

  private def getClassifiedTotalCounts(classifiedGuid: String): Future[Map[String, Long]] =
    AwsCaller.callWithDefaultRetryCount(getClassifiedTotalsRequest(classifiedGuid), queryCall)
      .map(_.getItems.map(_.toMap))
      .map(calculateClassifiedsTotals)

  private def getClassifiedActiveDaysInStock(classifiedGuid: String): Future[Int] =
    classifiedStateClient
      .getHistoryRows(classifiedGuid)
      .map(history => ClassifiedStateCalculator.getDateStates(history, None, None))
      .map(states => states.count(_.state == ClassifiedStates.Active))

  private val requiredFieldNames = Set(
    DbConstants.ColumnListPageView,
    DbConstants.ColumnListViewMobile,
    DbConstants.ColumnDetailPageView,
    DbConstants.ColumnDetailViewMobile,
    DbConstants.ColumnAddedToWatchlist,
    DbConstants.ColumnEmailSent
  )

  private def calculateClassifiedsTotals(attributes: Iterable[Map[String, AttributeValue]]): Map[String, Long] = {
    val initialMap = mutable.Map.empty[String, Long].withDefaultValue(0)
    attributes
      .map(extractClassifiedCounts)
      .foldLeft(initialMap) { (finalMap, rowMap) =>
        rowMap.foreach { case (key, value) => finalMap(key) += value }
        finalMap
      }
      .toMap
  }

  private def extractClassifiedCounts(attributes: Map[String, AttributeValue]): Map[String, Long] = {
    def convertMapEntry(input: (String, AttributeValue)): (String, Long) =
      (
        input._1,
        Try(input._2.getN.toLong).toOption.getOrElse(0L)
      )

    attributes
      .filter(entry => requiredFieldNames.contains(entry._1))
      .map(convertMapEntry)
  }


  private def getClassifiedTotalsRequest(classifiedGuid: String): QueryRequest =
    new QueryRequest()
      .withTableName("classified-trackstat")
      .withKeyConditionExpression("ClassifiedGuid = :guid")
      .withExpressionAttributeValues(Map(
        ":guid" -> new AttributeValue().withS(classifiedGuid)
      ))

  private def getAllClassifiedsTotals(classifiedGuids: Set[String]): Future[Seq[ClassifiedsTotalCounts]] = {
    val initialEmptySeq = Future(Seq.empty[ClassifiedsTotalCounts])

    def addNextTotals(groupOfClassifiedGuids: Set[String])(finalSet: Seq[ClassifiedsTotalCounts]): Future[Seq[ClassifiedsTotalCounts]] =
      Future
        .sequence(groupOfClassifiedGuids.map(getClassifiedTotals))
        .map(totals => finalSet ++ totals)

    classifiedGuids
      .grouped(500)
      .foldLeft(initialEmptySeq)((acc, group) =>
        acc.flatMap(addNextTotals(group))
      )
  }
}

object AllCustomersClassifiedsCountsRetriever {

  case class CustomerClassifiedsTotals(customerId: Long, totalCounts: Seq[ClassifiedsTotalCounts])

  case class ClassifiedsTotalCounts(classifiedGuid: String, counts: Map[String, Long], activeDaysInStock: Int)

  implicit val classifiedsTotalCountsWrites = new Writes[ClassifiedsTotalCounts] {
    override def writes(o: ClassifiedsTotalCounts): JsValue = Json.obj(
      "classifiedGuid" -> o.classifiedGuid,
      "counts" -> o.counts,
      "activeDaysInStock" -> o.activeDaysInStock
    )
  }

  implicit val customerClassifiedsTotalsWrites = new Writes[CustomerClassifiedsTotals] {
    override def writes(o: CustomerClassifiedsTotals): JsValue = Json.obj(
      "customerId" -> o.customerId,
      "totalCounts" -> o.totalCounts
    )
  }
}
