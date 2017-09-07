package bluewhale.api.counts

import java.time.LocalDate

import bluewhale.IdType

object DbRequests {

  case class DateRange(start: LocalDate, end: LocalDate)
  case class DateCounts(date: LocalDate, attributeCounts: Map[String, Long])

  case class SearchRequest(id: IdType, dateRange: DateRange)

  case class SearchResponse(idType: IdType, dateMap: Seq[DateCounts])

}
