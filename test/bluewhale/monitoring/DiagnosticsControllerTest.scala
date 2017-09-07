package bluewhale.monitoring

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, WithApplication}

class DiagnosticsControllerTest extends Specification {

  "heartbeat check should return ok" in new WithApplication {
    val Some(response) = route(implicitApp, FakeRequest(GET, "/diagnostics/heartbeat"))

    status(response) mustEqual OK
    contentAsString(response) mustEqual "Ok"
  }

  "version should return current build version and time" in new WithApplication {
    val Some(response) = route(implicitApp, FakeRequest(GET, "/diagnostics/version"))
    val date = """\d{4}-\d{2}-\d{2}"""
    val time = """\d{2}:\d{2}:\d{2}"""
    val timezone = """CE(S)?T"""
    val buildTime = s"buildTime: $date $time $timezone".r
    val version = """^version: \d+.*""".r

    status(response) mustEqual OK
    contentAsString(response) must =~ (version) and =~ (buildTime)
  }

  "exception check should return an exception" in new WithApplication {
    val Some(responseFuture) = route(implicitApp, FakeRequest(GET, "/diagnostics/exception"))

    responseFuture must FutureMatchable(throwA[RuntimeException])(ExecutionEnv.fromGlobalExecutionContext).await
  }
}
