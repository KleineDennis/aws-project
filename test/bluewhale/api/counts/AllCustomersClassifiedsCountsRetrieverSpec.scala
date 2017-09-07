package bluewhale.api.counts

import java.time.{LocalDate, OffsetDateTime}
import java.util.concurrent.{Future => JavaFuture}

import bluewhale.api.classifiedstate.{ClassifiedHistoryRow, ClassifiedState, ClassifiedStates}
import bluewhale.api.counts.MiaTier.MiaBasic
import bluewhale.ddb.{DbConstants, DdbClassifiedStateClient}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryRequest, QueryResult}
import com.autoscout24.eventpublisher24.playintegration.LogOnlyEventPublisherModule
import org.mockito.ArgumentMatcher
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.mock.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.{RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.collection.JavaConversions._
import scala.concurrent.Future

class AllCustomersClassifiedsCountsRetrieverSpec extends FlatSpec with MockitoSugar {

  val authorizer = mock[RequestAuthorizer]

  when(authorizer.execute(anyLong(), any[RequestHeader])(any[Future[Result]])).thenAnswer(
    new Answer[Future[Result]] {
      override def answer(invocation: InvocationOnMock): Future[Result] = {
        invocation.getArguments.toList(2).asInstanceOf[() => Future[Result]]()
      }
    }
  )

  val ddbClient = mock[AmazonDynamoDBAsync]
  val statesClient = mock[DdbClassifiedStateClient]

  val controller = new GuiceApplicationBuilder()
    .overrides(new LogOnlyEventPublisherModule)
    .overrides(bind[RequestAuthorizer].toInstance(authorizer))
    .overrides(bind[AmazonDynamoDBAsync].toInstance(ddbClient))
    .overrides(bind[DdbClassifiedStateClient].toInstance(statesClient))
    .injector().instanceOf(classOf[ArticleCountsController])

  def call(customerId: Long, url: String): Future[Result] =
    controller.getAllCustomersClassifiedsCounts(customerId).apply(FakeRequest("GET", url))

  class ClassifiedMatcher(guid: String) extends ArgumentMatcher[QueryRequest] {
    override def matches(argument: scala.Any): Boolean = {
      val req = argument.asInstanceOf[QueryRequest]
      req != null &&
        req.getExpressionAttributeValues.toMap.values.map(_.getS).contains(guid)
    }
  }

  class CustomerIdMatcher(customerId: Long) extends ArgumentMatcher[QueryRequest] {
    override def matches(argument: scala.Any): Boolean = {
      val req = argument.asInstanceOf[QueryRequest]
      req != null && req.getExpressionAttributeValues.toMap.values.map(_.getN).contains(customerId.toString)
    }
  }

  case class CustomerClassifiedsEntry(customerId: Long, classifiedGuid: String, state: String)

  def mockDdb(argumentMatcher: ArgumentMatcher[QueryRequest], response: Iterable[Map[String, AttributeValue]]): Unit =
    when(ddbClient.queryAsync(argThat(argumentMatcher), any[AsyncHandler[QueryRequest, QueryResult]])) thenAnswer new Answer[JavaFuture[QueryResult]] {
      override def answer(invocation: InvocationOnMock): JavaFuture[QueryResult] = {
        val request = invocation.getArguments.toList(0).asInstanceOf[QueryRequest]
        val handler = invocation.getArguments.toList(1).asInstanceOf[AsyncHandler[QueryRequest, QueryResult]]
        handler.onSuccess(request, new QueryResult().withItems(response.map(mapAsJavaMap)))
        null
      }
    }

  def mockCustomerClassifiedsDdb(customerId: Long, items: Seq[CustomerClassifiedsEntry]) =
    mockDdb(new CustomerIdMatcher(customerId), items.map(item => Map(
      DbConstants.ColumnCustomerId -> toAttributeValue(item.customerId),
      DbConstants.ColumnClassifiedGuid -> toAttributeValue(item.classifiedGuid),
      DbConstants.ColumnState -> toAttributeValue(item.state)
    )))

  def mockClassifiedsTrackstatDdb(classifiedGuid: String, items: Map[LocalDate, Map[String, Long]]) =
    mockDdb(new ClassifiedMatcher(classifiedGuid), items.map(item =>
      Map(DbConstants.ColumnDate -> toAttributeValue(item._1)) ++
        item._2.map(count => count._1 -> toAttributeValue(count._2))
    ))

  def mockClassifiedHistory(guid: String, statesByDate: Seq[(OffsetDateTime, ClassifiedState)]) =
    when(statesClient.getHistoryRows(guid)).thenReturn(Future.successful(
      statesByDate.map(dateAndState =>
        ClassifiedHistoryRow(
          guid,
          dateAndState._1,
          dateAndState._2,
          MiaBasic
        )
      )
    ))


  def toAttributeValue(rawValue: Any): AttributeValue =
    rawValue match {
      case _ : Int | _ : Long => new AttributeValue().withN(rawValue.toString)
      case _ => new AttributeValue().withS(rawValue.toString)
    }

  "API" should "return aggregated counts for all non deleted classifieds of a given customer" in {
    val classifiedGuids = Seq(
      "A" -> ClassifiedStates.Active,
      "B" -> ClassifiedStates.Active,
      "C" ->  ClassifiedStates.Deleted,
      "D" -> ClassifiedStates.MarkedForDeletion
    )

    val customerId = 1L
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val countDates = Seq(today, yesterday)
    val countTypes = Seq(DbConstants.ColumnDetailPageView, DbConstants.ColumnListPageView)

    val sixActiveThenInactiveStates: Seq[(OffsetDateTime, ClassifiedState)] = 1.to(3).flatMap(index => Seq(
      OffsetDateTime.now.minusDays(6 - (index * 2)) -> ClassifiedStates.Active,
      OffsetDateTime.now.minusDays(6 - (index * 2)).plusHours(1) -> ClassifiedStates.Inactive
    ))

    def getClassifiedCounts(guid: String): Map[LocalDate, Map[String, Long]] =
      countDates.map(countDate => (
        countDate,
        countTypes.map(countType => (
          countType,
          Character.getNumericValue(guid.charAt(0)) * ((countDate, countType) match {
            case (`today`, DbConstants.ColumnDetailPageView) => 1L
            case (`today`, DbConstants.ColumnListPageView) => 10L
            case (`yesterday`, DbConstants.ColumnDetailPageView) => 100L
            case (`yesterday`, DbConstants.ColumnListPageView) => 1000L
          })
        )).toMap
      )).toMap

    mockCustomerClassifiedsDdb(customerId, classifiedGuids.map(cl =>
      CustomerClassifiedsEntry(customerId, cl._1, cl._2.token)
    ))

    classifiedGuids.map(_._1).foreach(guid => {
      mockClassifiedsTrackstatDdb(guid, getClassifiedCounts(guid))
      mockClassifiedHistory(guid, sixActiveThenInactiveStates)
    })

    val result = call(customerId, s"/api/customers/$customerId/all-classifieds-counts")
    status(result) shouldBe 200

    def expectedCounts(guid: String): Seq[(String, Long)] = countTypes.map(countType =>
      countType -> countDates.map(countDate => getClassifiedCounts(guid)(countDate)(countType)).sum
    )

    contentAsJson(result) shouldBe Json.obj(
      "customerId" -> customerId,
      "totalCounts" ->
        classifiedGuids
          .filter(_._2 == ClassifiedStates.Active)
          .map(_._1)
          .map(guid =>
            Json.obj(
              "classifiedGuid" -> guid,
              "counts" -> JsObject(expectedCounts(guid).map(kv => kv._1 -> JsNumber(kv._2)).toMap),
              "activeDaysInStock" -> sixActiveThenInactiveStates.size / 2
            )
          )
    )
  }

}

