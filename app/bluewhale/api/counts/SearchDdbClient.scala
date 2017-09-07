package bluewhale.api.counts

import java.time.LocalDate

import bluewhale._
import bluewhale.api.counts.DbRequests._
import bluewhale.ddb.{AwsCaller, DbConstants}
import bluewhale.statsreceiver.EventTypes
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import com.google.inject.{Inject, Singleton}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

@Singleton
class SearchDdbClient @Inject()(
                                 asyncClient: AmazonDynamoDBAsync,
                                 config: BluewhaleConfiguration
                               ) {


  private def tableName(id: IdType): List[String] = id match {
    case ClassifiedGuid(_) => List(config.classifiedStatisticsTableName)
    case CustomerId(_) => List(config.customerStatisticsTableName)
  }

  private def hashKeyMapping(id: IdType): (String, AttributeValue) = id match {
    case ClassifiedGuid(guid) => (DbConstants.ColumnClassifiedGuid, new AttributeValue().withS(guid))
    case CustomerId(cid) => (DbConstants.ColumnCustomerId, new AttributeValue().withN(cid.toString))
  }

  private def queryCall(r: QueryRequest, h: AsyncHandler[QueryRequest, QueryResult]): Unit = asyncClient.queryAsync(r, h)

  def searchStats(request: SearchRequest): Future[SearchResponse] = {
    val hashMapping = hashKeyMapping(request.id)

    val queryRequest: QueryRequest = new QueryRequest()
      .withTableName(tableName(request.id).head)
      .withKeyConditionExpression(s"${hashMapping._1} = :hashKey AND #CD BETWEEN :start AND :end")
      .withExpressionAttributeNames(Map("#CD" -> DbConstants.ColumnDate))
      .withExpressionAttributeValues(Map(
        ":hashKey" -> hashMapping._2,
        ":start" -> new AttributeValue().withS(request.dateRange.start.format(DbConstants.DateFormatter)),
        ":end" -> new AttributeValue().withS(request.dateRange.end.format(DbConstants.DateFormatter))
      ))

    AwsCaller.callWithDefaultRetryCount(queryRequest, queryCall).map(
      result =>
        SearchResponse(
          request.id,
          result.getItems.flatMap(row => getClassifiedDateData(row.toMap))
        )
    )

  }

  private def getClassifiedDateData(row: Map[String, AttributeValue]): Option[DateCounts] =
    getRowDateOpt(row)
      .map(DateCounts(_, extractCounts(row)))
      .map(modifyCounts)
      .map(dateCounts =>
        DateCounts(
          dateCounts.date,
          dateCounts.attributeCounts ++ miaCounts(dateCounts.attributeCounts, extractMiaTier(row))
        )
      )

  private def getRowDateOpt(row: Map[String, AttributeValue]) = {
    Try(LocalDate.parse(row(DbConstants.ColumnDate).getS, DbConstants.DateFormatter)).toOption
  }

  private val BluewhaleStatsIsLiveDate = LocalDate.of(2017, 2, 22)

  private def modifyCounts(dateCounts: DateCounts): DateCounts =
    if (dateCounts.date.isBefore(BluewhaleStatsIsLiveDate)) {
      dateCounts
    } else {
      DateCounts(dateCounts.date, addMobileToDetailCounts(dateCounts.attributeCounts))
    }

  // This is required to match the previous $-stack behaviour.
  private def addMobileToDetailCounts(counts: Map[String, Long]): Map[String, Long] =
    Iterator(counts)
      .map(addCounts(
        DbConstants.ColumnDetailPageView,
        DbConstants.ColumnDetailViewMobile
      ))
      .map(addCounts(
        EventTypes.ClassifiedEventPrefix + DbConstants.ColumnDetailPageView,
        EventTypes.ClassifiedEventPrefix + DbConstants.ColumnDetailViewMobile
      ))
      .next()


  private def addCounts(fieldNameToIncrement: String, countFieldName: String)(counts: Map[String, Long]) =
    counts
      .get(countFieldName)
      .map(countsToAdd =>
        Map(
          fieldNameToIncrement -> (counts.getOrElse(fieldNameToIncrement, 0L) + countsToAdd)
        )
      )
      .map(counts ++ _)
      .getOrElse(counts)

  private val MiaFieldMapping: Map[MiaTier, String] = Map(
    MiaTier.MiaPlus -> DbConstants.ColumnDetailPageViewPlus,
    MiaTier.MiaPremium -> DbConstants.ColumnDetailPageViewPremium
  )

  private def extractCounts(row: Map[String, AttributeValue]) =
    row.filter(p => DbConstants.isClassifiedTrackstatCountField(p._1))
      .flatMap { case (key, value) =>
        Option(value.getN).map(count => key -> count.toLong)
      }

  private def extractMiaTier(row: Map[String, AttributeValue]) =
    row.get(DbConstants.ColumnMiaTier)
      .map(_.getS)
      .flatMap(x => MiaTier.apply(x))
      .getOrElse(MiaTier.MiaBasic)

  private def miaCounts(row: Map[String, Long], miaTier: MiaTier): Map[String, Long] =
    MiaFieldMapping
      .get(miaTier)
      .map(fieldToAdd =>
        Map(fieldToAdd -> (row.getOrElse(DbConstants.ColumnDetailPageView, 0L) + row.getOrElse(DbConstants.ColumnDetailViewMobile, 0L)))
      )
      .getOrElse(Map())

}
