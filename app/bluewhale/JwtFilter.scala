package bluewhale

import akka.stream.Materializer
import com.google.inject.Inject
import pdi.jwt.JwtAlgorithm.HS256
import pdi.jwt.JwtJson
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Filter, RequestHeader, Result, Results}

import scala.concurrent.Future
import scala.util.Try

class JwtFilter @Inject()(implicit val mat: Materializer, config: BluewhaleConfiguration) extends Filter {

  private val PrivateKey = config.jwtSecret

  override def apply(nextFilter: (RequestHeader) => Future[Result])(requestData: RequestHeader): Future[Result] = {
    if (!config.skipJwtSecret && requestData.path.startsWith("/api")) {
      requestData.headers.get("Authorization")
        .map(_.replaceAll("^Bearer *", ""))
        .flatMap(getTokenContent) match {
        case None => Future(Results.Unauthorized("Bad token"))
        case Some(tokenData) => nextFilter(
          tokenData.foldLeft(requestData)((req, tag) => req.withTag(tag._1, tag._2))
        )
      }
    } else {
      nextFilter(requestData)
    }
  }

  private def getTokenContent(token: String): Option[Map[String, String]] = {
    JwtJson.decode(token, PrivateKey, Seq(HS256)).toOption
      .flatMap(claim => Try(Json.parse(claim.content).as[JsObject].fields).toOption)
      .map(
        _.map(x => (x._1, x._2.asOpt[String].orElse(x._2.asOpt[Long].map(_.toString))))
          .filter(x => x._2.isDefined)
          .map(x => (x._1, x._2.get))
          .toMap
      )
  }
}
