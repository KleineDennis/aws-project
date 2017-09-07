package bluewhale.api.counts.realtimeaggregation

import java.time.LocalDate

import bluewhale.api.counts.DbRequests._
import bluewhale.api.counts.SearchDdbClient
import bluewhale.statsreceiver.EventTypes
import bluewhale.{ClassifiedGuid, CustomerId}
import com.google.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AggregatedCountsCalculator @Inject()(
                                            guidsProvider: ClassifiedGuidsProvider,
                                            ddbClient: SearchDdbClient
                                          ) {

  private def isAggregatedClassifiedCount(count: (String, Long)): Boolean =
    count._1.startsWith(EventTypes.ClassifiedEventPrefix)

  private def maxDate(maxDate: LocalDate, currentDate: LocalDate) = if (currentDate.isAfter(maxDate)) currentDate else maxDate

  private def getClassifiedAggregationMissingDateRange(request: SearchRequest, customerStatsResponse: SearchResponse): Option[DateRange] =
    customerStatsResponse
      .dateMap
      .filter(_.attributeCounts.exists(isAggregatedClassifiedCount))
      .map(_.date)
      .reduceLeftOption(maxDate)
      .orElse(Some(request.dateRange.start.minusDays(1)))
      .filter(_.isBefore(request.dateRange.end))
      .map(_.plusDays(1))
      .map(DateRange(_, request.dateRange.end))


  private def getClassifiedCounts(customerId: Long, dateRange: DateRange): Future[SearchResponse] = {
    def sumAttributeCounts(attributeCounts: Seq[(String, Long)]): Map[String, Long] =
      attributeCounts.groupBy(_._1)
        .mapValues(_.map(_._2).sum)
        .map { case (k, v) => s"${EventTypes.ClassifiedEventPrefix}$k" -> v }


    def mergeDateCounts(dateCounts: Seq[DateCounts]): Seq[DateCounts] =
      dateCounts
        .groupBy(_.date)
        .mapValues(_.flatMap(_.attributeCounts))
        .mapValues(sumAttributeCounts)
        .map { case (k, v) => DateCounts(k, v) }
        .toSeq


    guidsProvider.get(customerId, dateRange).flatMap { guids =>
      Future.sequence(guids.map(guid => ddbClient.searchStats(SearchRequest(ClassifiedGuid(guid), dateRange)))).map { responses =>
        val dayCounts = mergeDateCounts(responses.flatMap(_.dateMap)).sortBy(_.date.toEpochDay)
        SearchResponse(CustomerId(customerId), dayCounts)
      }
    }
  }

  private def mergeResults(customerResponse: SearchResponse, classifiedResponse: SearchResponse): SearchResponse = {
    SearchResponse(customerResponse.idType,
      // data from customer table only
      customerResponse.dateMap.filter(x => !classifiedResponse.dateMap.exists(_.date == x.date)) ++

        // here we merge the customer data with the classified data by removed classified data from the aggregated customer table and
        // adding classified data again but from the classified table
        customerResponse.dateMap.filter(x => classifiedResponse.dateMap.exists(_.date == x.date))
          .zip(classifiedResponse.dateMap.filter(x => customerResponse.dateMap.exists(_.date == x.date)))
          .map {
            case (cust, cla) => DateCounts(
              cust.date,
              cust.attributeCounts.filter(!isAggregatedClassifiedCount(_)) ++ cla.attributeCounts
            )
          } ++

        // data from classified table only
        classifiedResponse.dateMap.filter(x => !customerResponse.dateMap.exists(_.date == x.date)))
  }

  def calculate(request: SearchRequest, customerTableResult: SearchResponse): Future[SearchResponse] = {
    request.id match {
      case ClassifiedGuid(_) => Future.successful(customerTableResult)
      case CustomerId(customerId) =>
        getClassifiedAggregationMissingDateRange(request, customerTableResult) match {
          case Some(dateRange) => getClassifiedCounts(customerId, dateRange).map(classifiedTableResult => mergeResults(customerTableResult, classifiedTableResult))
          case None => Future.successful(customerTableResult)
        }
    }
  }


}
