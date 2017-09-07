package bluewhale.api.counts

import java.time.LocalDate

import bluewhale.AppEvents.{DbQueryFailure, InternalError}
import bluewhale.api.counts.ArticleResponseWriters._
import bluewhale.{ClassifiedGuid, CustomerId, IdType}
import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, Request, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class ArticleCountsController @Inject()(
                                         calc: ArticleCountsCalculator,
                                         publisher: TypedEventPublisher,
                                         requestAuthorizer: RequestAuthorizer,
                                         allCustomersClassifiedsCountsRetriever: AllCustomersClassifiedsCountsRetriever
                                       ) extends Controller {


  def getClassifiedCounts(classifiedGuid: String) = Action.async { request =>
    requestAuthorizer.execute(classifiedGuid, request) {
      getCounts(request, ClassifiedGuid(classifiedGuid))
    }
  }

  def getCustomerCounts(customerId: Long) = Action.async { request =>
    requestAuthorizer.execute(customerId, request) {
      getCounts(request, CustomerId(customerId))
    }
  }

  def getAllCustomersClassifiedsCounts(customerId: Long) = Action.async { request =>
    requestAuthorizer.execute(customerId, request) {
      allCustomersClassifiedsCountsRetriever
        .getAllClassifiedsCountsAsJson(customerId)
        .map(result => Ok(result))
        .recover {
          case ex =>
            publisher.publish(InternalError(new RuntimeException("Cannot get all customers classifieds counts", ex)))
            InternalServerError
        }
    }
  }

  private class BadRequestException(message: String) extends Exception(message)

  private def getDate(queryParameter: String, request: Request[Any]): Future[LocalDate] =
    request.getQueryString(queryParameter) match {
      case Some(x) => Try(LocalDate.parse(x, queryDateFormat)) match {
        case Success(date) => Future.successful(date)
        case Failure(e) => Future.failed(new BadRequestException(e.getMessage))
      }
      case None => Future.failed(new BadRequestException(s"No $queryParameter was provided"))
    }

  private def validateEndAfterStart(start: LocalDate, end: LocalDate): Future[Unit] =
    if (end.isBefore(start))
      Future.failed(new BadRequestException("endDate after be before or equal startDate"))
    else
      Future.successful(())

  private def createResponse(request: Request[Any], id: IdType): Future[CountsResponse] =
    for (
      start <- getDate("startDate", request);
      end <- getDate("endDate", request);
      _ <- validateEndAfterStart(start, end);
      resp <- calc.calculate(id, start, end)
    ) yield resp

  private def getCounts(request: Request[Any], id: IdType) = {
    createResponse(request, id).map(createJsonResult) recover {
      case x: BadRequestException => BadRequest(x.getMessage)
      case x =>
        x.printStackTrace()
        publisher.publish(DbQueryFailure(x))
        InternalServerError
    }
  }

  private def createJsonResult(response: CountsResponse): Result =
    Ok(Json.toJson(response))
      .withHeaders("Access-Control-Allow-Origin" -> "*")
}
