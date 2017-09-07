package bluewhale.api.counts.realtimeaggregation

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import bluewhale.api.classifiedstate.ClassifiedStates
import AggregationDbClient.ClassifiedStateData
import bluewhale.api.counts.DbRequests.DateRange
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.mock.MockitoSugar

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class ClassifiedGuidsProviderSpec extends FlatSpec with MockitoSugar {

  class TestPack {
    val aggregationDbClient = mock[AggregationDbClient]
    val guidsProvider = new ClassifiedGuidsProvider(aggregationDbClient)

    def mockDb(customerId: Long, result: Seq[ClassifiedStateData]) = {
      when(aggregationDbClient.loadCustomerGuids(customerId)) thenReturn Future.successful(result)
    }

    def verify(customerId: Long, dateRange: DateRange, result: Seq[String]) = {

      val res = Await.result(guidsProvider.get(customerId, dateRange), Duration(1, TimeUnit.SECONDS))
      res should equal(result)
    }
  }

  val day1 = LocalDate.of(2016, 10, 15)
  val day2 = LocalDate.of(2016, 10, 16)
  val day3 = LocalDate.of(2016, 10, 17)

  it should "return guids that are active today" in {
    val p = new TestPack
    p.mockDb(10, Seq(
      ClassifiedStateData("aa", day1, ClassifiedStates.Active)
    ))

    p.verify(10, DateRange(day1, day3), Seq("aa"))
  }

  it should "return guids that are active yesterday" in {
    val p = new TestPack
    p.mockDb(10, Seq(
      ClassifiedStateData("aa", day1, ClassifiedStates.Active)
    ))

    p.verify(10, DateRange(day2, day3), Seq("aa"))
  }

  it should "return guids that are active yesterday and inactive today" in {
    val p = new TestPack
    p.mockDb(10, Seq(
      ClassifiedStateData("aa", day1, ClassifiedStates.Active),
      ClassifiedStateData("aa", day2, ClassifiedStates.Inactive)
    ))

    p.verify(10, DateRange(day2, day3), Seq("aa"))
  }

  it should "not return guids that are inactive" in {
    val p = new TestPack
    p.mockDb(10, Seq(
      ClassifiedStateData("aa", day1, ClassifiedStates.Inactive)
    ))

    p.verify(10, DateRange(day1, day3), Seq())
  }

  it should "not return guids that are active tomorrow" in {
    val p = new TestPack
    p.mockDb(10, Seq(
      ClassifiedStateData("aa", day3, ClassifiedStates.Active)
    ))

    p.verify(10, DateRange(day1, day2), Seq())
  }
}
