package bluewhale.api.counts

import java.lang.{String => EventType}
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import bluewhale.{ClassifiedGuid, CustomerId, IdType}

/* Response:
  {
    classifiedGuid: "2345324", // OR
    customerId: 345", // OR
    dates: [
      {
        date: "2016-04-12",
        counts: [
          {
            "eventType": "DetailPageView",
            "count": 33
          }
        ]
      },
      {
        date: "2016-04-13",
        counts: [
          {
            "eventType": "DetailPageView",
            "count": 33
          },
          {
            "eventType": "ListPageView",
            "count": 33
          }
        ]
      },
    ]
  }
 */

case class CountsResponse(id: IdType, dates: Seq[DateCount])

case class DateCount(date: LocalDate, counts: Seq[EventTypeCount])

case class EventTypeCount(eventType: String, count: Long)

object ArticleResponseWriters {

  import play.api.libs.json._

  val queryDateFormat = DateTimeFormatter.ISO_LOCAL_DATE

  implicit val eventTypeCountWRite = new Writes[EventTypeCount] {
    def writes(eventType: EventTypeCount) = Json.obj(
      "eventType" -> eventType.eventType,
      "count" -> eventType.count
    )
  }

  implicit val dateCountWrite = new Writes[DateCount] {
    def writes(dateCount: DateCount) = Json.obj(
      "date" -> dateCount.date.format(queryDateFormat),
      "counts" -> dateCount.counts
    )
  }

  implicit val responseWrite = new Writes[CountsResponse] {
    override def writes(resp: CountsResponse): JsValue = Json.obj(
      resp.id match {
        case ClassifiedGuid(guid) => "classifiedGuid" -> JsString(guid)
        case CustomerId(id) => "customerId" -> JsNumber(id)
      },
      "dates" -> resp.dates
    )
  }
}


