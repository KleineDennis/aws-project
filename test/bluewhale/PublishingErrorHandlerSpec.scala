package bluewhale

import com.autoscout24.eventpublisher24.events.TypedEvents.ExceptionNotHandled
import com.autoscout24.eventpublisher24.events.{TypedEvent, TypedEventPublisher}
import com.autoscout24.eventpublisher24.request.ScoutRequestMeta
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers}
import org.scalatestplus.play.OneAppPerSuite
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Logger}

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

class PublishingErrorHandlerSpec extends FeatureSpec with Matchers with BeforeAndAfter with OneAppPerSuite {

  val publishedEvents = new ArrayBuffer[TypedEvent]()
  val publisherMock = new TypedEventPublisher {
    override def publish(event: TypedEvent) = {
      publishedEvents += event
    }
  }

  implicit override lazy val app: Application = {
    Logger.info("overriding app...")
    new GuiceApplicationBuilder()
      .overrides(bind[TypedEventPublisher].toInstance(publisherMock))
      .build()
  }

  /*
   * Just tests the publishing error handler.
   */
  feature("PublishingErrorHandler") {
    scenario("should call event publisher") {
      publishedEvents.clear()
      val errorHandler = app.errorHandler

      val mockRequest = FakeRequest("GET", "/abc")
      val exception = new RuntimeException("test exception")

      errorHandler.onServerError(mockRequest, exception)
      val handled: ExceptionNotHandled = ExceptionNotHandled(exception, "/abc", "GET")(ScoutRequestMeta())
      publishedEvents.contains(handled) shouldBe true
    }
  }

  /*
   * Attempt to provide an end-to-end test where an exception is handled by the application and an event is published.
   *
   * When enabling the verification the test fails but is kept in the codebase as a reference for future use.
   *
   * When running in test mode, Play does not call custom error handlers as suggested by
   * https://github.com/playframework/playframework/issues/4857. The workaround described at the bottom
   * ("Override HttpErrorHandler binding in custom ApplicationLoader") does not solve the issue.
   *
   * https://github.com/playframework/playframework/issues/2484 suggests that not calling the error handler is a feature.
   */
  feature("Application") {
    scenario("publish exception") {
      val Some(responseFuture) = route(app, FakeRequest(GET, "/diagnostics/exception").withHeaders(JwtTestUtil.getJwtHeader))

      ScalaFutures.whenReady(responseFuture.failed) { e =>
        Logger.info("matching exception...")
        e shouldBe a[RuntimeException]
      }

    }
  }

}
