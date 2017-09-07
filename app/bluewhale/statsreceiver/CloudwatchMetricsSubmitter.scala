package bluewhale.statsreceiver

import java.time.Instant
import java.util.Date

import akka.actor.{Actor, ActorRef, ActorSystem}
import bluewhale.AppEvents.InternalError
import bluewhale.ddb.AwsCaller
import bluewhale.statsreceiver.MetricsActor.{AddEvents, SendMetricsToCloudWatch}
import bluewhale.{ClassifiedGuid, CustomerId, IdType}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient
import com.amazonaws.services.cloudwatch.model.{MetricDatum, PutMetricDataRequest, PutMetricDataResult}
import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.google.inject.Inject
import com.google.inject.name.Named
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration._


class CloudwatchMetricsSubmitter @Inject()(
                                            @Named("cloudwatch-metrics-actor") actor: ActorRef,
                                            actorSystem: ActorSystem
                                          ) {


  def submitCountsToCloudwatch(events: Seq[StatisticEvent]): Unit = actor ! AddEvents(events)

  private val SleepDuration = 1.minute
  actorSystem.scheduler.schedule(SleepDuration, SleepDuration) {
    actor ! SendMetricsToCloudWatch
  }

}

class MetricsActor @Inject()(
                              cloudWatchClient: AmazonCloudWatchAsyncClient,
                              eventPublisher: TypedEventPublisher
                            ) extends Actor {

  override def receive: Receive = {
    case AddEvents(events) =>
      events.flatMap(metricNames).foreach(metricName => accumulatedMetrics(metricName) += 1)
      eventLagAggregator.addCounts(events)

    case SendMetricsToCloudWatch =>
      if (accumulatedMetrics.nonEmpty) {
        val req = createCloudWatchRequest()

        accumulatedMetrics.clear()
        eventLagAggregator = new EventLagAggregator

        AwsCaller.callWithDefaultRetryCount(req, callCloudWatch) recover {
          case ex => eventPublisher.publish(InternalError(new RuntimeException("Cannot send to CloudWatch", ex)))
        }
      }
  }

  private class EventLagAggregator {
    var numberOfEvents = 0L
    var sumOfLagsSeconds = 0L
    var maxLagSeconds = 0L

    def addCounts(events: Seq[StatisticEvent]) = {
      val nowSeconds = Instant.now.getEpochSecond

      events.foreach(event => {
        numberOfEvents += 1
        val lag = nowSeconds - event.timestampSeconds

        sumOfLagsSeconds += lag
        if (lag > maxLagSeconds) maxLagSeconds = lag
      })
    }

    def metrics: Map[String, Long] = Map(
      "event-lag-seconds-max" -> maxLagSeconds,
      "event-lag-seconds-average" -> sumOfLagsSeconds / numberOfEvents
    )
  }

  private var eventLagAggregator = new EventLagAggregator

  private val accumulatedMetrics = mutable.HashMap.empty[String, Long].withDefaultValue(0)

  private def callCloudWatch(r: PutMetricDataRequest, h: AsyncHandler[PutMetricDataRequest, PutMetricDataResult]): Unit =
    cloudWatchClient.putMetricDataAsync(r, h)


  private val metricDataNamespace: String = "bluewhale"
  private val metricPrefix = "statistic-events-received"

  private def createCloudWatchRequest(): PutMetricDataRequest =
    new PutMetricDataRequest()
      .withNamespace(metricDataNamespace)
      .withMetricData((accumulatedMetrics ++ eventLagAggregator.metrics).map { case (key, count) =>
        new MetricDatum()
          .withMetricName(key)
          .withTimestamp(new Date)
          .withValue(count.toDouble)
      })

  private def idTypeName(id: IdType): String = id match {
    case ClassifiedGuid(_) => "classified"
    case CustomerId(_) => "customer"
  }

  private def metricNames(event: StatisticEvent): List[String] =
    event.eventTypes.map(eventType => s"$metricPrefix-${idTypeName(event.id)}-$eventType")


}

object MetricsActor {

  case class SendMetricsToCloudWatch()

  case class AddEvents(events: Seq[StatisticEvent])

}
