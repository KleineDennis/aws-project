package bluewhale

import javax.inject.{Inject, Provider}

import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.autoscout24.eventpublisher24.events.TypedEvents.{BadRequestRejected, ExceptionNotHandled}
import com.autoscout24.eventpublisher24.request.ScoutRequestMeta
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import play.api.{Environment, OptionalSourceMapper}

import scala.concurrent.Future

/**
 *  Forwards server errors to the event publisher
 */
class PublishingErrorHandler @Inject() (
  publisher: TypedEventPublisher,
  env: Environment,
  config: play.api.Configuration,
  sourceMapper: OptionalSourceMapper,
  router: Provider[Router]
  ) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    publisher.publish(ExceptionNotHandled(
      ex,
      request.uri,
      request.method)(ScoutRequestMeta.fromRequestHeader(request)))
    super.onServerError(request, ex)
  }

  override def onClientError(request: RequestHeader, statusCode: Int, message: String = ""): Future[Result] = {
    publisher.publish(
      BadRequestRejected(
        message,
        request.uri,
        request.method,
        request.headers.toSimpleMap,
        statusCode)(ScoutRequestMeta.fromRequestHeader(request)))
    super.onClientError(request, statusCode, message)
  }
}
