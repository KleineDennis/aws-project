package bluewhale

import com.autoscout24.eventpublisher24.events.TypedEvent

object AppEvents {
  case class InternalError(exception: Throwable) extends TypedEvent("internal-server-error", None)
  case class EventSubmissionFailure(exception: Throwable) extends TypedEvent("event-submission-failure", None)
  case class DbQueryFailure(exception: Throwable) extends TypedEvent("db-query-failure", None)
  case class ProcessedEventTypes(eventTypes: Map[String, Long]) extends TypedEvent("processed-event-types", None)
  case class CouldNotParseEventError(jsonContent: String) extends TypedEvent("could-not-parse-event-error", None)
}
