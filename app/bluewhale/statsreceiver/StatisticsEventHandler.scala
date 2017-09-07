package bluewhale.statsreceiver

import java.time._

import bluewhale.api.counts.MiaTier
import bluewhale.ddb.DdbClassifiedStateClient
import com.google.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StatisticsEventHandler @Inject()(
                                        trackstatDdbClient: StatisticsReceiverDdbClient,
                                        classifiedStateClient: DdbClassifiedStateClient
                                      ) {

  private val GermanTimezone: ZoneId = ZoneId.of("Europe/Berlin")

  def handle(event: StatisticEvent): Future[Unit] =
    trackstatDdbClient.incrementCounter(toRequest(event))
      .flatMap(
        _
          .filter(_.miaTier.isEmpty)
          .map(updateMiaTier)
          .getOrElse(Future(()))
      )

  private def updateMiaTier(classifiedTrackstatItem: ClassifiedTrackstatItem): Future[Unit] =
    classifiedStateClient
      .getHistoryRows(classifiedTrackstatItem.classifiedGuid)
      .map(_
        .lastOption
        .map(_.miaTier)
        .getOrElse(MiaTier.MiaBasic)
      )
      .flatMap(miaTier =>
        trackstatDdbClient.writeMiaTier(
          classifiedTrackstatItem.classifiedGuid,
          classifiedTrackstatItem.date,
          miaTier)
      )


  private def toRequest(event: StatisticEvent) =
    IncrementRequest(
      event.id,
      LocalDateTime.ofInstant(Instant.ofEpochSecond(event.timestampSeconds), GermanTimezone).toLocalDate,
      event.eventTypes.toSet
    )

}
