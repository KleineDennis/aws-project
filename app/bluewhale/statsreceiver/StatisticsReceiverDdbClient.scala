package bluewhale.statsreceiver

import java.time.LocalDate

import bluewhale._
import bluewhale.api.counts.MiaTier
import bluewhale.ddb.{AwsCaller, DbConstants}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import com.google.inject.Inject

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IncrementRequest(id: IdType, date: LocalDate, eventTypes: Set[String])

case class ClassifiedTrackstatItem(classifiedGuid: String, date: LocalDate, counts: Map[String, Long], miaTier: Option[MiaTier])


class StatisticsReceiverDdbClient @Inject()(
                                             asyncClient: AmazonDynamoDBAsync,
                                             config: BluewhaleConfiguration
                                           ) {

  def incrementCounter(incrementRequest: IncrementRequest): Future[Option[ClassifiedTrackstatItem]] =
    AwsCaller.callWithDefaultRetryCount(
      createDdbUpdateRequest(tableName(incrementRequest.id), incrementRequest),
      ddbUpdateCall
    ).map(result =>
      incrementRequest.id match {
        case ClassifiedGuid(_) => Some(createClassifiedTrackstatItem(result))
        case CustomerId(_) => None
      }
    )

  def writeMiaTier(classifiedGuid: String, date: LocalDate, miaTier: MiaTier): Future[Unit] = {
    val request = new UpdateItemRequest()
      .withTableName(config.classifiedStatisticsTableName)
      .withKey(Map(
        DbConstants.ColumnClassifiedGuid -> new AttributeValue().withS(classifiedGuid),
        DbConstants.ColumnDate -> new AttributeValue().withS(date.format(DbConstants.DateFormatter))
      ))
      .withUpdateExpression(s"SET ${DbConstants.ColumnMiaTier} = if_not_exists(${DbConstants.ColumnMiaTier}, :mia)")
      .withExpressionAttributeValues(Map(
        ":mia" -> new AttributeValue().withS(miaTier.id)
      ))

    AwsCaller.callWithDefaultRetryCount(request, ddbUpdateCall).map(_ => ())
  }

  private def createClassifiedTrackstatItem(result: UpdateItemResult): ClassifiedTrackstatItem = {
    val columns = result.getAttributes.toMap
    ClassifiedTrackstatItem(
      classifiedGuid = columns(DbConstants.ColumnClassifiedGuid).getS,
      date = LocalDate.parse(columns(DbConstants.ColumnDate).getS, DbConstants.DateFormatter),
      counts = columns
        .filter(column => DbConstants.isClassifiedTrackstatCountField(column._1))
        .map {
          case (key, value) => key -> value.getN.toLong
        },
      miaTier = columns.get(DbConstants.ColumnMiaTier).map(_.getS).flatMap(MiaTier.apply)
    )
  }

  private def tableName(id: IdType): String = id match {
    case ClassifiedGuid(_) => config.classifiedStatisticsTableName
    case CustomerId(_) => config.customerStatisticsTableName
  }

  private def hashKeyMapping(id: IdType): (String, AttributeValue) = id match {
    case ClassifiedGuid(guid) => (DbConstants.ColumnClassifiedGuid, new AttributeValue().withS(guid))
    case CustomerId(cid) => (DbConstants.ColumnCustomerId, new AttributeValue().withN(cid.toString))
  }

  private def createDdbUpdateRequest(tableName: String, request: IncrementRequest) =
    new UpdateItemRequest()
      .withTableName(tableName)
      .withKey(Map(
        hashKeyMapping(request.id),
        DbConstants.ColumnDate -> new AttributeValue(request.date.format(DbConstants.DateFormatter))
      ))
      .withAttributeUpdates(request.eventTypes.map(eventType =>
        eventType -> new AttributeValueUpdate(new AttributeValue().withN("1"), AttributeAction.ADD))
        .toMap[String, AttributeValueUpdate])
      .withReturnValues(ReturnValue.ALL_NEW)

  private def ddbUpdateCall(request: UpdateItemRequest, handler: AsyncHandler[UpdateItemRequest, UpdateItemResult]): Unit =
    asyncClient.updateItemAsync(request, handler)

}
