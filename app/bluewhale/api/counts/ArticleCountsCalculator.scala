package bluewhale.api.counts

import java.time.LocalDate

import bluewhale.api.counts.realtimeaggregation.AggregatedCountsCalculator
import DbRequests._
import bluewhale.IdType
import bluewhale.statsreceiver.{EventsUseCase, EventTypes}
import com.google.inject.Inject
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future


class ArticleCountsCalculator @Inject()(
                                         dynamoDbClient: SearchDdbClient,
                                         aggregatedCountsCalculator: AggregatedCountsCalculator
                                       ) {

  def calculate(id: IdType, start: LocalDate, end: LocalDate): Future[CountsResponse] = {
    val eventTypesToSearch = EventTypes.allowedFor(id, EventsUseCase.CountsApi)
    val eventTypesToSum = EventTypes.toSumFor(id)
    val zeroEventTypes = eventTypesToSearch.map(_ -> 0L).toMap

    def toWebResponse(dbResponse: SearchResponse): CountsResponse =
      CountsResponse(
        dbResponse.idType,
        dbResponse.dateMap.map(dateCounts => {
          val initialCounts = zeroEventTypes ++ dateCounts.attributeCounts
          val allEventTypesMap =
            initialCounts
              .map { case (countType, countValue) =>
                (
                  countType,
                  countValue +
                    eventTypesToSum
                      .get(countType)
                      .flatMap(dateCounts.attributeCounts.get)
                      .getOrElse(0L)
                )
              }
              .filter(count => eventTypesToSearch.contains(count._1))
          DateCount(
            dateCounts.date,
            allEventTypesMap.map(et => EventTypeCount(et._1, et._2)).toSeq
          )
        })
      )


    val request: SearchRequest = SearchRequest(id, DateRange(start, end))
    dynamoDbClient.searchStats(request)
      .flatMap(result => aggregatedCountsCalculator.calculate(request, result))
      .map(toWebResponse)
  }

}
