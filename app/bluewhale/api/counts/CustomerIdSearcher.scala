package bluewhale.api.counts

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import bluewhale.BluewhaleConfiguration
import com.google.inject.Inject
import pdi.jwt.JwtAlgorithm.HS256
import pdi.jwt.{JwtClaim, JwtHeader, JwtJson}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class CustomerIdSearcher @Inject()(
                                    ws: WSClient,
                                    config: BluewhaleConfiguration,
                                    actorSystem: ActorSystem
                                  ) {

  private val urlBase = config.babelfishUrl

  var jwtToken = createBabelfishJwtToken()

  private def createBabelfishJwtToken(): String = {
    val claim = JwtClaim().by("bluewhale").expiresIn(20 * 60)
    val header = JwtHeader(Some(HS256))
    JwtJson.encode(header, claim, config.babelfishJwtSecret)
  }

  def search(classifiedGuid: String): Future[Option[Long]] = {
    ws.url(s"$urlBase/$classifiedGuid").withHeaders(("Authorization", s"Bearer $jwtToken")).get().map { response =>
      if (response.status == 200) Some(response.body.toLong) else None
    }
  }

  private val tokenRefreshInterval = Duration(15, TimeUnit.MINUTES)
  actorSystem.scheduler.schedule(tokenRefreshInterval, tokenRefreshInterval) {
    jwtToken = createBabelfishJwtToken()
  }
}
