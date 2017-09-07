package bluewhale.api.counts

import bluewhale.ddb.DbConstants
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
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, JsValue, Reads}
import play.api.mvc.{RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.collection.JavaConversions._
import scala.concurrent.Future

class MiaTierSpec extends FlatSpec with MockitoSugar {

  val authorizer = mock[RequestAuthorizer]
  when(authorizer.execute(any[String], any[RequestHeader])(any[Future[Result]])) thenAnswer new Answer[Future[Result]] {
    override def answer(invocation: InvocationOnMock): Future[Result] =
      invocation.getArguments.toList(2).asInstanceOf[() => Future[Result]]()
  }

  val ddbClient = mock[AmazonDynamoDBAsync]

  val controller = new GuiceApplicationBuilder()
    .overrides(new LogOnlyEventPublisherModule)
    .overrides(bind[RequestAuthorizer].toInstance(authorizer))
    .overrides(bind[AmazonDynamoDBAsync].toInstance(ddbClient))
    .injector().instanceOf(classOf[ArticleCountsController])

  def call(guid: String, url: String) =
    controller.getClassifiedCounts(guid).apply(FakeRequest("GET", url))

  class QueryRequestMatcher(guid: String) extends ArgumentMatcher[QueryRequest] {
    override def matches(argument: scala.Any): Boolean = {
      val req = argument.asInstanceOf[QueryRequest]
      req != null && req.getExpressionAttributeValues.toMap.values.map(_.getS).contains(guid)
    }
  }

  def mockDdb(guid: String, response: Map[String, AttributeValue]): Unit = {
    when(ddbClient.queryAsync(argThat(new QueryRequestMatcher(guid)), any[AsyncHandler[QueryRequest, QueryResult]])) thenAnswer new Answer[java.util.concurrent.Future[QueryResult]] {
      override def answer(invocation: InvocationOnMock): java.util.concurrent.Future[QueryResult] = {
        val request = invocation.getArguments.toList(0).asInstanceOf[QueryRequest]
        val handler = invocation.getArguments.toList(1).asInstanceOf[AsyncHandler[QueryRequest, QueryResult]]
        handler.onSuccess(request, new QueryResult().withItems(response))
        null
      }
    }
  }

  def getCount(result: Future[Result], attribute: String): Long = {
    case class AttributeCount(attributeName: String, count: Long)

    implicit val reader: Reads[AttributeCount] = (
      (JsPath \ "eventType").read[String] and
        (JsPath \ "count").read[Long]
      ) (AttributeCount.apply _)

    val body: JsValue = contentAsJson(result)
    val counts = ((body \ "dates") (0) \ "counts").as[List[AttributeCount]]
    counts.find(p => p.attributeName == attribute).map(_.count).get
  }

  "API" should "return T20 counts" in {
    mockDdb("g1", Map(
      DbConstants.ColumnDate -> new AttributeValue().withS("2017-01-13"),
      DbConstants.ColumnDetailPageView -> new AttributeValue().withN("10"),
      DbConstants.ColumnDetailViewMobile -> new AttributeValue().withN("20"),
      DbConstants.ColumnMiaTier -> new AttributeValue().withS("T20")
    ))
    val result = call("g1", "/api/classifieds/g1/counts?startDate=2017-01-13&endDate=2017-01-13")
    status(result) shouldBe 200

    getCount(result, DbConstants.ColumnDetailPageViewPlus) shouldBe 30
  }

  it should "return T30 counts" in {
    mockDdb("g2", Map(
      DbConstants.ColumnDate -> new AttributeValue().withS("2017-01-13"),
      DbConstants.ColumnDetailPageView -> new AttributeValue().withN("10"),
      DbConstants.ColumnDetailViewMobile -> new AttributeValue().withN("20"),
      DbConstants.ColumnMiaTier -> new AttributeValue().withS("T30")
    ))
    val result = call("g2", "/api/classifieds/g2/counts?startDate=2017-01-13&endDate=2017-01-13")
    status(result) shouldBe 200

    getCount(result, DbConstants.ColumnDetailPageViewPremium) shouldBe 30
  }


}

