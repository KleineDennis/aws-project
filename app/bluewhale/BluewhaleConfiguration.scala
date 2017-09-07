package bluewhale

import javax.inject.{Inject, Singleton}

import com.autoscout24.eventpublisher24.events.EventConfig
import com.typesafe.config.ConfigException
import info.BuildInfo

import scala.util.Try



@Singleton
class BluewhaleConfiguration @Inject()(playConfig: play.api.Configuration) {
  lazy val config = playConfig.underlying
  val stackName = Try(config.getString("stack-name")).recover { case e: ConfigException.Missing => "local" }.get
  val version = BuildInfo.version
  val allowDiagnosticsException = config.getBoolean("allowDiagnosticsException")

  EventConfig.setStackName(stackName)
  EventConfig.setVersion(version)

  val classifiedStatisticsTableName = config.getString("classified.statistics.tablename")

  val customerStatisticsTableName = config.getString("customer.statistics.tablename")

  lazy val region: String = playConfig.getString("region").get

  val jwtSecret = playConfig.getString("jwt.secret").get
  val skipJwtSecret = playConfig.getBoolean("jwt.skipAuthentication").contains(true)

  val babelfishUrl = playConfig.getString("classified-babelfish.url").get
  val babelfishJwtSecret = playConfig.getString("classified-babelfish.jwt.secret").get
}
