package bluewhale.api.classifiedstate

import java.time.OffsetDateTime

import bluewhale.api.counts.MiaTier

case class ClassifiedHistoryRow(
                                 classifiedGuid: String,
                                 timestamp: OffsetDateTime,
                                 state: ClassifiedState,
                                 miaTier: MiaTier
                               )
