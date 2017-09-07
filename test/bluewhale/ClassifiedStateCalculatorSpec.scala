package bluewhale

import java.time.{LocalDate, OffsetDateTime}

import bluewhale.api.classifiedstate._
import bluewhale.api.counts.MiaTier
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class ClassifiedStateCalculatorSpec extends FlatSpec {

  private def date(minusDays: Int) = LocalDate.now.minusDays(minusDays)
  private def timestamp(minusDays: Int, plusHours: Int = 0) = OffsetDateTime.now.minusDays(minusDays).plusHours(plusHours)

  "ClassifiedStateCalculator" should "return None when no data found" in {
    ClassifiedStateCalculator.getWebResponse(None, None, Seq()) shouldEqual None
  }

  it should "return data for all dates in the specified interval" in {
    ClassifiedStateCalculator.getWebResponse(Some(date(3)), Some(date(1)), Seq(
      ClassifiedHistoryRow("abc", timestamp(3), ClassifiedStates.Active, MiaTier.MiaBasic)
    )) shouldEqual
      Some(ClassifiedStatesResponse("abc", Seq(
        ClassifiedDateState(date(3), ClassifiedStates.Active),
        ClassifiedDateState(date(2), ClassifiedStates.Active),
        ClassifiedDateState(date(1), ClassifiedStates.Active)
      )))
  }

  it should "return data for all dates until now if no end date is specified" in {
    ClassifiedStateCalculator.getWebResponse(None, None, Seq(
      ClassifiedHistoryRow("abc", OffsetDateTime.now.minusDays(2), ClassifiedStates.Active, MiaTier.MiaBasic)
    )) shouldEqual
      Some(ClassifiedStatesResponse("abc", Seq(
        ClassifiedDateState(LocalDate.now.minusDays(2), ClassifiedStates.Active),
        ClassifiedDateState(LocalDate.now.minusDays(1), ClassifiedStates.Active),
        ClassifiedDateState(LocalDate.now, ClassifiedStates.Active)
      )))
  }

  it should "return active if there was an activation in that date" in {
    ClassifiedStateCalculator.getWebResponse(None, None, Seq(
      ClassifiedHistoryRow("abc", timestamp(0, 1), ClassifiedStates.Active, MiaTier.MiaBasic),
      ClassifiedHistoryRow("abc", timestamp(0, 2), ClassifiedStates.Inactive, MiaTier.MiaBasic)
    )) shouldEqual
      Some(ClassifiedStatesResponse("abc", Seq(ClassifiedDateState(date(0), ClassifiedStates.Active))))
  }

  it should "return active if there was an activation in the date before" in {
    ClassifiedStateCalculator.getWebResponse(None, None, Seq(
      ClassifiedHistoryRow("abc", timestamp(2), ClassifiedStates.Active, MiaTier.MiaBasic),
      ClassifiedHistoryRow("abc", timestamp(1), ClassifiedStates.Inactive, MiaTier.MiaBasic)
    )) shouldEqual
      Some(ClassifiedStatesResponse("abc", Seq(
        ClassifiedDateState(date(2), ClassifiedStates.Active),
        ClassifiedDateState(date(1), ClassifiedStates.Active),
        ClassifiedDateState(date(0), ClassifiedStates.Inactive)
      )))
  }

  it should "return inactive if there was an inactivation in the date before" in {
    ClassifiedStateCalculator.getWebResponse(None, None, Seq(
      ClassifiedHistoryRow("abc", timestamp(3), ClassifiedStates.Active, MiaTier.MiaBasic),
      ClassifiedHistoryRow("abc", timestamp(2), ClassifiedStates.Inactive, MiaTier.MiaBasic),
      ClassifiedHistoryRow("abc", timestamp(0), ClassifiedStates.Active, MiaTier.MiaBasic)
    )) shouldEqual
      Some(ClassifiedStatesResponse("abc", Seq(
        ClassifiedDateState(date(3), ClassifiedStates.Active),
        ClassifiedDateState(date(2), ClassifiedStates.Active),
        ClassifiedDateState(date(1), ClassifiedStates.Inactive),
        ClassifiedDateState(date(0), ClassifiedStates.Active)
      )))
  }

  it should "return unknown if no data are available" in {
    ClassifiedStateCalculator.getWebResponse(Some(date(3)), None, Seq(
      ClassifiedHistoryRow("abc", timestamp(2), ClassifiedStates.Inactive, MiaTier.MiaBasic),
      ClassifiedHistoryRow("abc", timestamp(0), ClassifiedStates.Active, MiaTier.MiaBasic)
    )) shouldEqual
      Some(ClassifiedStatesResponse("abc", Seq(
        ClassifiedDateState(date(3), ClassifiedStates.Unknown),
        ClassifiedDateState(date(2), ClassifiedStates.Inactive),
        ClassifiedDateState(date(1), ClassifiedStates.Inactive),
        ClassifiedDateState(date(0), ClassifiedStates.Active)
      )))
  }

}
