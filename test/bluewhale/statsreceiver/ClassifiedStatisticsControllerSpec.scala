package bluewhale.statsreceiver

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, OffsetDateTime, ZoneOffset}

import akka.util.Timeout
import bluewhale.api.classifiedstate.{ClassifiedHistoryRow, ClassifiedStates}
import bluewhale.api.counts.MiaTier
import bluewhale.api.counts.MiaTier.MiaBasic
import bluewhale.ddb.DdbClassifiedStateClient
import bluewhale.{ClassifiedGuid, JwtTestUtil}
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.specs2.matcher.Matcher
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, WithApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class ClassifiedStatisticsControllerSpec extends Specification with Mockito with DefaultAwaitTimeout {

  override implicit def defaultAwaitTimeout: Timeout = 2.seconds

  def cloudwatchclient() = {
    val client = mock[CloudwatchMetricsSubmitter]
    client
  }

  def newDdbClient = {
    val db = mock[StatisticsReceiverDdbClient]
    db.incrementCounter(any) returns Future(Some(ClassifiedTrackstatItem("abc", LocalDate.of(2017, 2, 3), Map(), Some(MiaBasic))))
    db.writeMiaTier(any[String], any[LocalDate], any[MiaTier]) returns Future(())
    db
  }

  def newStateClient = {
    val stateClient = mock[DdbClassifiedStateClient]
    when(stateClient.getHistoryRows(any[String])) thenReturn Future(Seq())
    stateClient
  }

  private def testApp = GuiceApplicationBuilder()
    .overrides(bind[CloudwatchMetricsSubmitter].toInstance(cloudwatchclient))
    .overrides(bind[StatisticsReceiverDdbClient].toInstance(newDdbClient))
    .overrides(bind[DdbClassifiedStateClient].toInstance(newStateClient))
    .build()


  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  private class WithApp extends WithApplication(testApp) {

    private def matcherFor(event: StatisticEvent): Matcher[IncrementRequest] = { req: IncrementRequest =>
      req mustEqual IncrementRequest(
        event.id,
        LocalDateTime.ofEpochSecond(event.timestampSeconds, 0, ZoneOffset.UTC).toLocalDate,
        event.eventTypes.toSet
      )
    }

    def ddbClient = app.injector.instanceOf(classOf[StatisticsReceiverDdbClient])

    def cloudwatch = app.injector.instanceOf[CloudwatchMetricsSubmitter]

    def historyClient = app.injector.instanceOf[DdbClassifiedStateClient]

    def post(url: String, body: String, responseStatus: Int = OK) = {
      val Some(result) = route(implicitApp, FakeRequest(POST, url)
        .withHeaders(JwtTestUtil.getJwtHeader)
        .withTextBody(body)
        .withHeaders(CONTENT_TYPE -> "application/json")
      )
      status(result) mustEqual responseStatus
    }

    def thereWasDDBUpdateFor(event: StatisticEvent) =
      there was one(ddbClient).incrementCounter(matcherFor(event))

    def metricsWereSubmitted() = there was one(cloudwatch).submitCountsToCloudwatch(any)
  }

  def iso(date: LocalDate) = date.format(DateTimeFormatter.ISO_DATE)


  "bluewhale classifieds statistics endpoint" should {

    "call dynamodb update for list view mobile event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "ListViewMobile"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("ListViewMobile")))
      there were noCallsTo(ddbClient)
      eventually {
        metricsWereSubmitted()
      }

    }

    "call dynamodb update for detail view mobile event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "DetailViewMobile"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("DetailViewMobile")))
      there were noCallsTo(ddbClient)
      eventually {
        metricsWereSubmitted()
      }
    }

    "call dynamodb update for sent email event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "EmailSent"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("EmailSent")))
      there were noCallsTo(ddbClient)
      eventually {
        metricsWereSubmitted()
      }
    }

    "call dynamodb update for call click event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "CallClick"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("CallClick")))
      there were noCallsTo(ddbClient)
      eventually {
        metricsWereSubmitted()
      }
    }

    "call dynamodb update for grabber detail click event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "GrabberClickDetail"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("GrabberClickDetail")))
      there were noCallsTo(ddbClient)
      eventually {
        metricsWereSubmitted()
      }
    }

    "call dynamodb update for added to watchlist event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "AddedToWatchlist"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("AddedToWatchlist")))
      there were noCallsTo(ddbClient)
    }

    "call dynamodb update for a detail page view tracking event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "DetailPageView"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("DetailPageView")))
      there were noCallsTo(ddbClient)
    }

    "call dynamodb update for a list page view tracking event" in new WithApp {

      val json =
        s"""|[{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730660",
            |  "timestamp": 1466587779,
            |  "type": "ListPageView"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730660"), 1466587779, List("ListPageView")))
      there were noCallsTo(ddbClient)
    }

    "call dynamodb update for every event from the provided event list" in new WithApp {

      val json =
        s"""|[
            |{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730661",
            |  "timestamp": 1466587000,
            |  "type": "DetailPageView"
            |},
            |{ "classifiedGuid":"21fbd16e-b151-4739-86c6-e71e563ab50e1363730662",
            |  "timestamp": 1466587779,
            |  "type": "ListPageView"
            |}
            |]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730661"), 1466587000, List("DetailPageView")))
      thereWasDDBUpdateFor(StatisticEvent(ClassifiedGuid("21fbd16e-b151-4739-86c6-e71e563ab50e1363730662"), 1466587779, List("ListPageView")))

      there were noCallsTo(ddbClient)
    }

    "update MIA tier if it is not present" in new WithApp {

      val guid = "21fbd16e-b151-4739-86c6-e71e563ab50e1363730661"

      val json =
        s"""|[
            |{ "classifiedGuid":"$guid",
            |  "timestamp": 1466587000,
            |  "types": ["DetailPageView"]
            |}
            |]""".stripMargin


      when(historyClient.getHistoryRows(any[String])) thenReturn Future(Seq(ClassifiedHistoryRow(guid, OffsetDateTime.now(), ClassifiedStates.Active, MiaTier.MiaPlus)))
      val date = LocalDate.now
      when(ddbClient.incrementCounter(any[IncrementRequest])) thenReturn Future(Some(ClassifiedTrackstatItem(guid, date, Map(), None)))

      post("/api/statistic/events", json)

      there was one(ddbClient).writeMiaTier(guid, date, MiaTier.MiaPlus)
    }

    "update MIA tier with default MIA value if no history items are present" in new WithApp {

      val guid = "21fbd16e-b151-4739-86c6-e71e563ab50e1363730661"

      val json =
        s"""|[
            |{ "classifiedGuid":"$guid",
            |  "timestamp": 1466587000,
            |  "types": ["DetailPageView"]
            |}
            |]""".stripMargin


      when(historyClient.getHistoryRows(any[String])) thenReturn Future(Seq())
      val date = LocalDate.now
      when(ddbClient.incrementCounter(any[IncrementRequest])) thenReturn Future(Some(ClassifiedTrackstatItem(guid, date, Map(), None)))

      post("/api/statistic/events", json)

      there was one(ddbClient).writeMiaTier(guid, date, MiaTier.MiaBasic)
    }

  }
}
