package bluewhale.statsreceiver

import java.time.LocalDate

import bluewhale.ClassifiedGuid
import bluewhale.ddb.{DbConstants, DdbClassifiedStateClient}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.mock.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StatisticsEventHandlerSpec extends FlatSpec with MockitoSugar {

  "StatisticsEventHandler" should "use German time zone to determine the date" in {
    val ddb = mock[StatisticsReceiverDdbClient]
    when(ddb.incrementCounter(any[IncrementRequest])) thenReturn Future(None)


    val stateClient = mock[DdbClassifiedStateClient]
    when(stateClient.getHistoryRows(any[String])) thenReturn Future(Seq())
    val h = new StatisticsEventHandler(ddb, stateClient)

    h.handle(StatisticEvent(ClassifiedGuid("123"), 1482276600, List(DbConstants.ColumnDetailViewMobile)))

    verify(ddb).incrementCounter(IncrementRequest(ClassifiedGuid("123"), LocalDate.of(2016, 12, 21), Set(DbConstants.ColumnDetailViewMobile)))
  }

}
