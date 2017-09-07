package bluewhale.api.classifiedstate

import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class ClassifiedStatesResponse(classifiedGuid: String, states: Seq[ClassifiedDateState])

case class ClassifiedDateState(date: LocalDate, state: ClassifiedState)

object ClassifiedStatesResponseWrites {
  import play.api.libs.json._

  val DateFormat = DateTimeFormatter.ISO_LOCAL_DATE

  implicit val stateWrite = new Writes[ClassifiedDateState] {
    override def writes(state: ClassifiedDateState): JsValue = Json.obj(
      "date" -> state.date.format(DateFormat),
      "state" -> state.state.token
    )
  }


  implicit val responseWrite = new Writes[ClassifiedStatesResponse] {
    override def writes(resp: ClassifiedStatesResponse): JsValue = Json.obj(
      "classifiedGuid" -> resp.classifiedGuid,
      "dates" -> resp.states
    )
  }
}