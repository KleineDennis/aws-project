package bluewhale

trait IdType

case class ClassifiedGuid(classifiedGuid: String) extends IdType

case class CustomerId(id: Long) extends IdType

