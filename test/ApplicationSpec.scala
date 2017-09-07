import bluewhale.JwtTestUtil
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  val jwtHeader = JwtTestUtil.getJwtHeader

  "Application" should {

    "send 404 on a bad request" in new WithApplication {
      route(implicitApp, FakeRequest(GET, "/api/boum").withHeaders(jwtHeader)) must beSome.which(status(_) == NOT_FOUND)
    }

    "return 401 if called without JWT token" in new WithApplication {
      val home = route(implicitApp, FakeRequest(GET, "/api/uhu")).get
      status(home) must equalTo(UNAUTHORIZED)
    }
  }
}
