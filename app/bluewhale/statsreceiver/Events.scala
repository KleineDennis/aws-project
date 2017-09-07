package bluewhale.statsreceiver

import bluewhale.ddb.DbConstants
import bluewhale.statsreceiver.EventsUseCase.{StatsReceiver, CountsApi}
import bluewhale.{ClassifiedGuid, CustomerId, IdType}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}


case class StatisticEvent(id: IdType, timestampSeconds: Long, eventTypes: List[String]) {
  def allowedEventTypes = EventTypes.allowedFor(id, EventsUseCase.StatsReceiver)

  def isValid: Boolean = eventTypes.nonEmpty && eventTypes.forall(x => allowedEventTypes.contains(x))
}

sealed trait EventsUseCase
object EventsUseCase {
  case object CountsApi extends EventsUseCase
  case object StatsReceiver extends EventsUseCase
}

object EventTypes {

  def allowedFor(id: IdType, useCase: EventsUseCase): List[String] = id match {
    case ClassifiedGuid(_) => useCase match {
      case CountsApi => classifiedEventsTypesForCountsApi
      case StatsReceiver => classifiedEventTypesForStatsReceiver
    }
    case CustomerId(_) => useCase match {
      case CountsApi => customerEventTypesForCountsApi
      case StatsReceiver => customerEventTypesForStatsReceiver
    }
  }

  def toSumFor(id: IdType): Map[String, String] = id match {
    case ClassifiedGuid(_) => Map.empty
    case CustomerId(_) => AutoTraderEventsMapping
  }

  val ClassifiedEventPrefix = "Classifieds"

  private val classifiedEventTypesForStatsReceiver = List(
    DbConstants.ColumnDetailPageView,
    DbConstants.ColumnDetailViewMobile,
    DbConstants.ColumnListPageView,
    DbConstants.ColumnEmailSent,
    DbConstants.ColumnAddedToWatchlist,
    DbConstants.ColumnCallClick,
    DbConstants.ColumnListViewMobile,
    DbConstants.ColumnHomePageAdClick,
    DbConstants.ColumnPrintout,
    DbConstants.ColumnGrabberClickDetail
  )

  private val classifiedEventsTypesForCountsApi = classifiedEventTypesForStatsReceiver ++ List(
    DbConstants.ColumnDetailPageViewPremium,
    DbConstants.ColumnDetailPageViewPlus
  )

  private val AutoTraderEventsMapping = List(
    DbConstants.ColumnDetailPageView,
    DbConstants.ColumnListPageView,
    DbConstants.ColumnAddedToWatchlist,
    DbConstants.ColumnEmailSent,
    DbConstants.ColumnCallClick,
    DbConstants.ColumnPrintout
  ).map(x => s"$ClassifiedEventPrefix$x" -> s"${DbConstants.AutoTraderPrefix}$x").toMap

  private val customerEventTypesForStatsReceiver = List(
    DbConstants.ColumnEmailSent,
    DbConstants.ColumnAnsweredCall,
    DbConstants.ColumnUnansweredCall,
    DbConstants.ColumnDealerStockListPageView,
    DbConstants.ColumnDealerHomePageView
  )

  private val customerEventTypesForCountsApi = customerEventTypesForStatsReceiver ++
    classifiedEventsTypesForCountsApi.map(x => s"$ClassifiedEventPrefix$x")

}


object EventReads {

  implicit val customerStatisticEventRead: Reads[StatisticEvent] = (
    (JsPath \ "classifiedGuid").read[String].map(ClassifiedGuid(_).asInstanceOf[IdType])
      .orElse((JsPath \ "customerId").read[Long].map(CustomerId(_).asInstanceOf[IdType]))
      and
      (JsPath \ "timestamp").read[Long] and
      (for (
        typeValue <- (JsPath \ "type").readNullable[String];
        typeList <- (JsPath \ "types").readNullable[List[String]].map(_.getOrElse(List()))
      ) yield if (typeList.isEmpty) List(typeValue).flatten else typeList)
    ) (StatisticEvent.apply _)

}