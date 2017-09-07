package bluewhale.api.counts

import bluewhale.BluewhaleConfiguration
import com.google.inject.Inject
import play.api.mvc.{RequestHeader, Result, Results}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RequestAuthorizer @Inject()(config: BluewhaleConfiguration, customerIdSearcher: CustomerIdSearcher) {

  private val accessProhibited: Future[Result] = Future.successful(Results.Forbidden(s"Access prohibited"))

  def execute(customerId: Long, request: RequestHeader)(result: => Future[Result]): Future[Result] =
    if (config.skipJwtSecret) result
    else request.tags.get("CustomerId") match {
      case Some(cid) if cid == customerId.toString => result
      case _ => accessProhibited
    }

  def execute(classifiedGuid: String, request: RequestHeader)(result: => Future[Result]): Future[Result] =
    if (config.skipJwtSecret) result
    else customerIdSearcher.search(classifiedGuid).flatMap {
      case Some(customerId) => execute(customerId, request)(result)
      case None => accessProhibited
    }

}
