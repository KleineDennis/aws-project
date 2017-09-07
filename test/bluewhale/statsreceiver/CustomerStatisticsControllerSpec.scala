package bluewhale.statsreceiver

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}

import akka.util.Timeout
import bluewhale.ddb.DdbClassifiedStateClient
import bluewhale.{CustomerId, JwtTestUtil}
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.specs2.matcher.Matcher
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, WithApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class CustomerStatisticsControllerSpec extends Specification with Mockito with DefaultAwaitTimeout {

  override implicit def defaultAwaitTimeout: Timeout = 2.seconds

  def cloudwatchclient() = {
    val client = mock[CloudwatchMetricsSubmitter]
    client
  }


  def ddbClient = {
    val db = mock[StatisticsReceiverDdbClient]
    db.incrementCounter(any) returns Future(None)
    db
  }

  def newStateClient = {
    val stateClient = mock[DdbClassifiedStateClient]
    when(stateClient.getHistoryRows(any[String])) thenReturn Future(Seq())
    stateClient
  }

  private def testApp = GuiceApplicationBuilder()
    .overrides(bind[CloudwatchMetricsSubmitter].toInstance(cloudwatchclient))
    .overrides(bind[StatisticsReceiverDdbClient].toInstance(ddbClient))
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


  "bluewhale customer statistics endpoint" should {

    "call dynamodb update for sent email event" in new WithApp {

      val json =
        s"""|[{ "customerId":123,
            |  "timestamp": 1466587779,
            |  "type": "EmailSent"
            |}]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(CustomerId(123), 1466587779, List("EmailSent")))
      there were noCallsTo(ddbClient)
      eventually {
        metricsWereSubmitted()
      }
    }

    "call dynamodb update for event with eventList" in new WithApp {

      val json =
        s"""|[{ "customerId":123,
            |  "timestamp": 1466587779,
            |  "type": "EmailSent",
            |  "types": ["DealerHomePageView","EmailSent"]
            |}]""".stripMargin

      import EventReads._

      println(Json.parse(json).as[List[StatisticEvent]])

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(CustomerId(123), 1466587779, List("DealerHomePageView", "EmailSent")))
      there were noCallsTo(ddbClient)
      eventually {
        metricsWereSubmitted()
      }
    }


    "call dynamodb update for every event from the provided event list" in new WithApp {

      val json =
        s"""|[
            |{ "customerId":456,
            |  "timestamp": 1466587000,
            |  "type": "EmailSent"
            |},
            |{ "customerId":789,
            |  "timestamp": 1466587779,
            |  "type": "EmailSent"
            |}
            |]""".stripMargin

      post("/api/statistic/events", json)

      thereWasDDBUpdateFor(StatisticEvent(CustomerId(456), 1466587000, List("EmailSent")))
      thereWasDDBUpdateFor(StatisticEvent(CustomerId(789), 1466587779, List("EmailSent")))

      there were noCallsTo(ddbClient)
    }

  }
}
