package bluewhale.statsreceiver

import bluewhale.AppEvents.{CouldNotParseEventError, EventSubmissionFailure}
import bluewhale.statsreceiver.EventReads._
import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.google.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Controller, Request}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

@Singleton
class StatisticsReceiverController @Inject()(eventHandler: StatisticsEventHandler,
                                             publisher: TypedEventPublisher,
                                             metricsSubmitter: CloudwatchMetricsSubmitter
                                    ) extends Controller {

  private case class ErrorStatisticEvent(json: String)

  private def extractClassifiedEvents(request: Request[AnyContent]): (Seq[StatisticEvent],Seq[ErrorStatisticEvent]) = {
    val statisticEvents = new ListBuffer[StatisticEvent]
    val errorStatisticEvents = new ListBuffer[ErrorStatisticEvent]

    request.body.asJson.map(_.as[JsArray]).getOrElse(JsArray()).value
      .map(json => {

        Json.fromJson[StatisticEvent](json).asOpt.filter(_.isValid) match {
          case Some(event) => statisticEvents += event
          case None => errorStatisticEvents += ErrorStatisticEvent(json.toString())
        }
      })

    (statisticEvents, errorStatisticEvents)
  }

  def statisticEvent = Action.async { request =>
    val (statisticEvents, errors) = extractClassifiedEvents(request)
    metricsSubmitter.submitCountsToCloudwatch(statisticEvents)
    errors.foreach(x => publisher.publish(CouldNotParseEventError(x.json)))
    Future.sequence(statisticEvents.map(eventHandler.handle)).map(_ => Ok) recover {
      case x => {
        publisher.publish(EventSubmissionFailure(x))
        InternalServerError
      }
    }
  }

}