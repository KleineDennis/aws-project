package bluewhale.api.livedata

import bluewhale.AppEvents.InternalError
import bluewhale.api.counts.RequestAuthorizer
import bluewhale.ddb.{DdbLiveDataClient, ListingLivedata}
import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext


class LiveDataController @Inject()(requestAuthorizer: RequestAuthorizer,
                                   client: DdbLiveDataClient,
                                   publisher: TypedEventPublisher)
                                  (implicit ec: ExecutionContext) extends Controller {

  implicit val ListingLivedataWrites = Json.writes[ListingLivedata]

  def getClassifiedLiveData(classifiedGuid: String) = Action.async { request =>
    requestAuthorizer.execute(classifiedGuid, request) {
      client.read(classifiedGuid)
        .map(res => Ok(Json.toJson(res)))
        .recover {
          case ex =>
            publisher.publish(InternalError(new RuntimeException("Reading listing livedata failed", ex)))
            InternalServerError
        }
    }
  }
}
