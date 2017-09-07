package bluewhale.api.classifiedstate

sealed abstract class ClassifiedState(val token: String)

object ClassifiedState {
  def apply(token: String): ClassifiedState =
    ClassifiedStates
      .all
      .find(_.token == token)
      .getOrElse(ClassifiedStates.default)
}

object ClassifiedStates {
  case object Active extends ClassifiedState("active")
  case object Inactive extends ClassifiedState("inactive")
  case object Expired extends ClassifiedState("expired")
  case object MarkedForDeletion extends ClassifiedState("markedForDeletion")
  case object Deleted extends ClassifiedState("deleted")
  case object Unknown extends ClassifiedState("unknown")

  val all: Seq[ClassifiedState] = Seq(Active, Inactive, Expired, MarkedForDeletion, Deleted, Unknown)
  val default: ClassifiedState = Unknown
}
