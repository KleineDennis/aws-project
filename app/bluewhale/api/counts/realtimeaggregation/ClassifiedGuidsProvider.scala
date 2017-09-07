package bluewhale.api.counts.realtimeaggregation

import bluewhale.api.classifiedstate.{ClassifiedState, ClassifiedStates}
import AggregationDbClient.ClassifiedStateData
import bluewhale.api.counts.DbRequests.DateRange
import com.google.inject.Inject

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ClassifiedGuidsProvider @Inject()(
                                         aggregationDbClient: AggregationDbClient
                                       ) {


  def get(customerId: Long, dateRange: DateRange): Future[Seq[String]] = {

    def chooseGuid(guids: Seq[ClassifiedStateData]): Option[String] = {
      var prevState = ClassifiedStates.default
      for (data <- guids.sortBy(_.date.toEpochDay)) {
        if (data.date.isAfter(dateRange.end)) {
          if (prevState == ClassifiedStates.Active)
            return Some(data.guid)
          else
            return None
        }
        if (!data.date.isBefore(dateRange.start)) {
          if (prevState == ClassifiedStates.Active || data.state == ClassifiedStates.Active) {
            return Some(data.guid)
          }
        }

        prevState = data.state
      }
      if(prevState == ClassifiedStates.Active) Some(guids.head.guid) else None
    }

    aggregationDbClient.loadCustomerGuids(customerId).map { guids =>
      guids.groupBy(_.guid)
        .values
        .flatMap(chooseGuid)
        .toSeq
    }
  }

}
