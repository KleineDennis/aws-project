package bluewhale.ddb

import java.time.format.DateTimeFormatter

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.dynamodbv2.model.{InternalServerErrorException, ProvisionedThroughputExceededException}

object DbConstants {

  val DateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  val ColumnDate = "Date"
  val ColumnClassifiedGuid = "ClassifiedGuid"
  val ColumnCustomerId = "CustomerId"
  val ColumnState = "State"
  val ColumnDetailPageView = "DetailPageView"
  val ColumnDetailViewMobile = "DetailViewMobile"
  val ColumnListPageView = "ListPageView"
  val ColumnEmailSent = "EmailSent"
  val ColumnAddedToWatchlist = "AddedToWatchlist"
  val ColumnCallClick = "CallClick"
  val ColumnListViewMobile = "ListViewMobile"
  val ColumnHomePageAdClick = "HomePageAdClick"
  val ColumnGrabberClickDetail = "GrabberClickDetail"
  val ColumnPrintout = "Printout"
  val ColumnAnsweredCall = "AnsweredCall"
  val ColumnUnansweredCall = "UnansweredCall"
  val ColumnDealerHomePageView = "DealerHomePageView"
  val ColumnDealerStockListPageView = "DealerStockListPageView"

  // Only in the customer table
  val ColumnDetailPageViewPremium = "DetailPageViewPremium"
  val ColumnDetailPageViewPlus = "DetailPageViewPlus"

  val AutoTraderPrefix = "AutoTrader"

  // Flags
  val ColumnMiaTier = "MiaTier"

  val ClassifiedTrackstatNonCountFields = Set(ColumnClassifiedGuid, ColumnDate, ColumnMiaTier)

  def isClassifiedTrackstatCountField(fieldName: String) = !ClassifiedTrackstatNonCountFields.contains(fieldName)



  def canResubmit(exception: Exception): Boolean = {
    exception match {
      case ex: ProvisionedThroughputExceededException => true
      case ex: InternalServerErrorException => true
      case ex: ClientExecutionTimeoutException => true
      case _ => false
    }
  }
}
