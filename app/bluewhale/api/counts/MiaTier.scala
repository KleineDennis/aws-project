package bluewhale.api.counts

sealed abstract class MiaTier(val id: String, val priority: Integer)

object MiaTier {
  def apply(id: String): Option[MiaTier] = all.find(_.id == id)

  case object MiaBasic extends MiaTier("T10", 0)
  case object MiaPlus extends MiaTier("T20", 1)
  case object MiaPremium extends MiaTier("T30", 2)

  val all = Set(MiaBasic, MiaPlus, MiaPremium)
}
