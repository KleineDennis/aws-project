package bluewhale.api.classifiedstate

import java.time.{LocalDate, ZoneId}

import scala.collection.mutable

object ClassifiedStateCalculator {

  private val StatesPrecedence = Array(
    ClassifiedStates.Unknown,
    ClassifiedStates.Deleted,
    ClassifiedStates.MarkedForDeletion,
    ClassifiedStates.Expired,
    ClassifiedStates.Inactive,
    ClassifiedStates.Active
  )

  private def getDateStates(historyRows: Seq[ClassifiedHistoryRow]): Seq[ClassifiedDateState] = {
    historyRows.map(hr => ClassifiedDateState(hr.timestamp.toLocalDate, hr.state))
  }

  private def firstDate(firstRow: ClassifiedHistoryRow, start: Option[LocalDate]): LocalDate =
    start match {
      case Some(x) => x
      case None => firstRow.timestamp.toLocalDate
    }

  private def lastDate(end: Option[LocalDate]): LocalDate =
    end match {
      case Some(x) => x
      case None => LocalDate.now(ZoneId.of("Europe/Berlin"))
    }

  def getDateStates(historyRows: Seq[ClassifiedHistoryRow], start: Option[LocalDate], end: Option[LocalDate]): Seq[ClassifiedDateState] = {

    if(historyRows.isEmpty)
      return Seq[ClassifiedDateState]()

    var date = firstDate(historyRows.head, start)
    val endDate = lastDate(end)
    var lastStateIndex = 0
    var rows = historyRows

    val result = mutable.ArrayBuffer.empty[ClassifiedDateState]

    while (!date.isAfter(endDate)) {

      var maxStateIndex = lastStateIndex
      while (rows.nonEmpty && rows.head.timestamp.toLocalDate == date) {
        val current = rows.head
        val ix = StatesPrecedence.indexOf(current.state)

        if (ix > maxStateIndex) maxStateIndex = ix

        lastStateIndex = ix
        rows = rows.tail
      }

      result.append(ClassifiedDateState(date, StatesPrecedence(maxStateIndex)))

      date = date.plusDays(1)
    }
    result
  }

  def getWebResponse(start: Option[LocalDate], end: Option[LocalDate], historyRows: Seq[ClassifiedHistoryRow]): Option[ClassifiedStatesResponse] =
    if (historyRows.isEmpty)
      None
    else
      Some(ClassifiedStatesResponse(historyRows.head.classifiedGuid, getDateStates(historyRows, start, end)))
}
