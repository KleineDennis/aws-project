package bluewhale.api.classifiedstate

import java.time.LocalDate

import bluewhale.AppEvents.InternalError
import bluewhale.api.counts.RequestAuthorizer
import bluewhale.ddb.DdbClassifiedStateClient
import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import play.api.libs.concurrent.Execution.Implicits._

class ClassifiedStateController @Inject()(
                                           ddb: DdbClassifiedStateClient,
                                           typedEventPublisher: TypedEventPublisher,
                                           requestAuthorizer: RequestAuthorizer
                                         ) extends Controller {

  import ClassifiedStatesResponseWrites._

  private class BadQueryParameter(val str: String) extends RuntimeException

  private def toLocalDate(str: Option[String]) =
    str.filter(_.trim.nonEmpty) match {
      case None => Future.successful(None)
      case Some(x) => Try(LocalDate.parse(x)) match {
        case Success(date) => Future.successful(Some(date))
        case Failure(e) => Future.failed(new BadQueryParameter(x))
      }
    }

  private def makeResponse(start: Option[LocalDate], end: Option[LocalDate], historyRows: Seq[ClassifiedHistoryRow]) =
    ClassifiedStateCalculator.getWebResponse(start, end, historyRows) match {
      case Some(resp) => Ok(Json.toJson(resp))
      case None => NotFound
    }

  def getClassifiedStates(classifiedGuid: String) = Action.async { request =>
    requestAuthorizer.execute(classifiedGuid, request) {
      (for (
        start <- toLocalDate(request.getQueryString("start"));
        end <- toLocalDate(request.getQueryString("end"));
        historyRows <- ddb.getHistoryRows(classifiedGuid)
      ) yield makeResponse(start, end, historyRows)) recover {
        case ex: BadQueryParameter => BadRequest(s"Bad query parameter: ${ex.str}")
        case ex =>
          typedEventPublisher.publish(InternalError(ex))
          InternalServerError
      }
    }
  }
}
