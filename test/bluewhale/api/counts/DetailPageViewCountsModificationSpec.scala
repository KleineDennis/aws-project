package bluewhale.api.counts

import bluewhale.ddb.DbConstants
import bluewhale.statsreceiver.EventTypes
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
import scala.util.Try

class DetailPageViewCountsModificationSpec extends FlatSpec with MockitoSugar {

  val authorizer = mock[RequestAuthorizer]
  private val passThrough = new Answer[Future[Result]] {
    override def answer(invocation: InvocationOnMock): Future[Result] =
      invocation.getArguments.toList(2).asInstanceOf[() => Future[Result]]()
  }
  when(authorizer.execute(any[String], any[RequestHeader])(any[Future[Result]])) thenAnswer passThrough
  when(authorizer.execute(any[Long], any[RequestHeader])(any[Future[Result]])) thenAnswer passThrough

  val ddbClient = mock[AmazonDynamoDBAsync]

  val controller = new GuiceApplicationBuilder()
    .overrides(new LogOnlyEventPublisherModule)
    .overrides(bind[RequestAuthorizer].toInstance(authorizer))
    .overrides(bind[AmazonDynamoDBAsync].toInstance(ddbClient))
    .injector().instanceOf(classOf[ArticleCountsController])

  def call(guid: String, url: String) =
    controller.getClassifiedCounts(guid).apply(FakeRequest("GET", url))

  def callCustomer(customerId: Long, url: String) =
    controller.getCustomerCounts(customerId).apply(FakeRequest("GET", url))

  class GuidQueryRequestMatcher(guid: String) extends ArgumentMatcher[QueryRequest] {
    override def matches(argument: scala.Any): Boolean = {
      val req = argument.asInstanceOf[QueryRequest]
      req != null && req.getExpressionAttributeValues.toMap.values.map(_.getS).contains(guid)
    }
  }
  class CustomerIdQueryRequestMatcher(customerId: Long) extends ArgumentMatcher[QueryRequest] {
    override def matches(argument: scala.Any): Boolean = {
      val req = argument.asInstanceOf[QueryRequest]
      req != null && req.getExpressionAttributeValues.toMap.values.map(_.getN).contains(customerId.toString)
    }
  }

  def mockClassifiedDdb(guid: String, response: Map[String, AttributeValue]): Unit = {
    when(ddbClient.queryAsync(argThat(new GuidQueryRequestMatcher(guid)), any[AsyncHandler[QueryRequest, QueryResult]])) thenAnswer new Answer[java.util.concurrent.Future[QueryResult]] {
      override def answer(invocation: InvocationOnMock): java.util.concurrent.Future[QueryResult] = {
        val request = invocation.getArguments.toList(0).asInstanceOf[QueryRequest]
        val handler = invocation.getArguments.toList(1).asInstanceOf[AsyncHandler[QueryRequest, QueryResult]]
        handler.onSuccess(request, new QueryResult().withItems(response))
        null
      }
    }
  }

  def mockCustomerDdb(customerId: Long, response: Map[String, AttributeValue]): Unit = {
    when(ddbClient.queryAsync(argThat(new CustomerIdQueryRequestMatcher(customerId)), any[AsyncHandler[QueryRequest, QueryResult]])) thenAnswer new Answer[java.util.concurrent.Future[QueryResult]] {
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

  "API" should "add mobile page views to detail page views starting on Feb 22nd 2017" in {
    mockClassifiedDdb("g1", Map(
      DbConstants.ColumnDate -> new AttributeValue().withS("2017-02-22"),
      DbConstants.ColumnDetailPageView -> new AttributeValue().withN("10"),
      DbConstants.ColumnDetailViewMobile -> new AttributeValue().withN("20"),
      DbConstants.ColumnMiaTier -> new AttributeValue().withS("T20")
    ))
    val result = call("g1", "/api/classifieds/g1/counts?startDate=2017-01-13&endDate=2017-02-22")
    status(result) shouldBe 200

    getCount(result, DbConstants.ColumnDetailPageView) shouldBe 30
    getCount(result, DbConstants.ColumnDetailPageViewPlus) shouldBe 50
  }

  it should "modify customer DPV, as well" in {
    mockCustomerDdb(33L, Map(
      DbConstants.ColumnDate -> new AttributeValue().withS("2017-02-22"),
      "ClassifiedsDetailPageView" -> new AttributeValue().withN("100"),
      "ClassifiedsDetailViewMobile" -> new AttributeValue().withN("200")
    ))

    val result = callCustomer(33L, "/api/customers/33/counts?startDate=2017-01-13&endDate=2017-02-22")
    status(result) shouldBe 200

    getCount(result, EventTypes.ClassifiedEventPrefix + DbConstants.ColumnDetailPageView) shouldBe 300
  }

  it should "not modify detail page views before Feb 22nd 2017" in {
    mockClassifiedDdb("g1", Map(
      DbConstants.ColumnDate -> new AttributeValue().withS("2017-01-22"),
      DbConstants.ColumnDetailPageView -> new AttributeValue().withN("10"),
      DbConstants.ColumnDetailViewMobile -> new AttributeValue().withN("20"),
      DbConstants.ColumnMiaTier -> new AttributeValue().withS("T20")
    ))
    val result = call("g1", "/api/classifieds/g1/counts?startDate=2017-01-13&endDate=2017-01-22")
    status(result) shouldBe 200

    getCount(result, DbConstants.ColumnDetailPageView) shouldBe 10
    getCount(result, DbConstants.ColumnDetailPageViewPlus) shouldBe 30
  }

  it should "not modify customer DPV before Feb 22nd 2017" in {
    mockCustomerDdb(33L, Map(
      DbConstants.ColumnDate -> new AttributeValue().withS("2017-02-21"),
      "ClassifiedsDetailPageView" -> new AttributeValue().withN("100"),
      "ClassifiedsDetailViewMobile" -> new AttributeValue().withN("200")
    ))

    val result = callCustomer(33L, "/api/customers/33/counts?startDate=2017-01-13&endDate=2017-02-22")
    status(result) shouldBe 200

    getCount(result, EventTypes.ClassifiedEventPrefix + DbConstants.ColumnDetailPageView) shouldBe 100
  }

  it should "add AutoTrader KPIs (instead of returning them) to the normal ones if they are present in the DDB entry" in {
    mockCustomerDdb(13L, Map(
      DbConstants.ColumnDate -> new AttributeValue().withS("2017-02-22"),
      EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnDetailPageView -> new AttributeValue().withN("10"),
      EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnDetailViewMobile -> new AttributeValue().withN("15"),
      s"${DbConstants.AutoTraderPrefix}${DbConstants.ColumnDetailPageView}" -> new AttributeValue().withN("20"),
      EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnPrintout -> new AttributeValue().withN("10"),
      EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnAddedToWatchlist -> new AttributeValue().withN("10"),
      s"${DbConstants.AutoTraderPrefix}${DbConstants.ColumnAddedToWatchlist}" -> new AttributeValue().withN("20")
    ))
    val result = callCustomer(13L, "/api/customers/13/counts?startDate=2017-02-22&endDate=2017-02-22")
    status(result) shouldBe 200

    getCount(result, EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnDetailPageView) shouldBe (10 + 15 + 20)
    getCount(result, EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnDetailViewMobile) shouldBe 15
    getCount(result, EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnPrintout) shouldBe 10
    getCount(result, EventTypes.ClassifiedEventPrefix ++ DbConstants.ColumnAddedToWatchlist) shouldBe (10 + 20)

    Seq(
      s"${DbConstants.AutoTraderPrefix}${DbConstants.ColumnAddedToWatchlist}",
      s"${DbConstants.AutoTraderPrefix}${DbConstants.ColumnDetailPageView}"
    ).foreach(autoTraderKpiThatShouldBeIgnored =>
      Try(getCount(result, autoTraderKpiThatShouldBeIgnored)).toOption shouldBe None
    )
  }

}

