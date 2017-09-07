package bluewhale.api.counts.realtimeaggregation

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import bluewhale.api.counts.SearchDdbClient
import bluewhale.api.counts.DbRequests._
import bluewhale.statsreceiver.EventTypes
import bluewhale.{ClassifiedGuid, CustomerId}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.mock.MockitoSugar

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class AggregatedCountsCalculatorSpec extends FlatSpec with MockitoSugar {

  class TestPack {
    val guidsProvider = mock[ClassifiedGuidsProvider]
    val dbClient = mock[SearchDdbClient]
    val calculator = new AggregatedCountsCalculator(guidsProvider, dbClient)

    def verify(request: SearchRequest, customerStatsResponse: SearchResponse, expected: SearchResponse) = {
      val res = Await.result(calculator.calculate(request, customerStatsResponse), Duration(1, TimeUnit.SECONDS))
      res should equal(expected)
    }
  }

  val day1 = LocalDate.of(2016, 10, 15)
  val day2 = LocalDate.of(2016, 10, 16)
  val day3 = LocalDate.of(2016, 10, 17)

  val customerId: CustomerId = CustomerId(10)
  val emailSent: String = "EmailSent"
  val classifiedEmailSent: String = s"${EventTypes.ClassifiedEventPrefix}$emailSent"

  it should "not request classifieds stats if aggregated customer stats are present" in {
    val p = new TestPack
    p.verify(
      SearchRequest(customerId, DateRange(day1, day1)),
      SearchResponse(customerId, Seq(
        DateCounts(day1, Map(classifiedEmailSent -> 10))
      )),
      SearchResponse(customerId, Seq(
        DateCounts(day1, Map(classifiedEmailSent -> 10))
      ))
    )
  }

  it should "aggregate classified stats if aggregated customer stats are missing" in {
    val p = new TestPack

    when(p.guidsProvider.get(eqTo(customerId.id), any[DateRange])) thenReturn Future.successful(Seq("aa", "bb"))
    when(p.dbClient.searchStats(SearchRequest(ClassifiedGuid("aa"), DateRange(day2, day2)))) thenReturn Future.successful(SearchResponse(ClassifiedGuid("aa"), Seq(
      DateCounts(day2, Map(emailSent -> 3))
    )))
    when(p.dbClient.searchStats(SearchRequest(ClassifiedGuid("bb"), DateRange(day2, day2)))) thenReturn Future.successful(SearchResponse(ClassifiedGuid("bb"), Seq(
      DateCounts(day2, Map(emailSent -> 4))
    )))

    p.verify(
      SearchRequest(customerId, DateRange(day1, day2)),
      SearchResponse(customerId, Seq(
        DateCounts(day1, Map(
          classifiedEmailSent -> 10,
          emailSent -> 20
        )),
        DateCounts(day2, Map(
          emailSent -> 50
        ))
      )),
      SearchResponse(customerId, Seq(
        DateCounts(day1, Map(
          classifiedEmailSent -> 10,
          emailSent -> 20
        )),
        DateCounts(day2, Map(
          emailSent -> 50,
          classifiedEmailSent -> 7
        ))
      ))
    )
  }

  it should "ignore classifieds stats before available customer stats" in {
    val p = new TestPack

    when(p.guidsProvider.get(eqTo(customerId.id), any[DateRange])) thenReturn Future.successful(Seq("aa"))
    when(p.dbClient.searchStats(SearchRequest(ClassifiedGuid("aa"), DateRange(day1, day1)))) thenReturn Future.successful(SearchResponse(ClassifiedGuid("aa"), Seq(
      DateCounts(day1, Map(emailSent -> 4))
    )))
    when(p.dbClient.searchStats(SearchRequest(ClassifiedGuid("aa"), DateRange(day3, day3)))) thenReturn Future.successful(SearchResponse(ClassifiedGuid("aa"), Seq(
      DateCounts(day3, Map(emailSent -> 3))
    )))

    val customerStatsResponse = SearchResponse(customerId, Seq(
      DateCounts(day2, Map(
        classifiedEmailSent -> 50
      ))
    ))

    val expectedFinalResponse = SearchResponse(customerId, Seq(
      DateCounts(day2, Map(
        classifiedEmailSent -> 50
      )),
      DateCounts(day3, Map(
        classifiedEmailSent -> 3
      ))
    ))

    p.verify(
      SearchRequest(customerId, DateRange(day1, day3)),
      customerStatsResponse,
      expectedFinalResponse
    )
  }

}
