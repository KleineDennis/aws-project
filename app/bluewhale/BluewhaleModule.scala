package bluewhale

import bluewhale.statsreceiver.MetricsActor
import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClient}
import com.google.inject.{AbstractModule, Provides, Singleton}
import play.api.libs.concurrent.AkkaGuiceSupport

class BluewhaleModule extends AbstractModule with AkkaGuiceSupport {

  def configure() = {
    bind(classOf[BluewhaleConfiguration]).asEagerSingleton()
    bindActor[MetricsActor]("cloudwatch-metrics-actor")
  }

  @Singleton
  @Provides
  def dynamoDBAsync(configuration: BluewhaleConfiguration): AmazonDynamoDBAsync = {
    val cc: ClientConfiguration = new ClientConfiguration().withClientExecutionTimeout(10 * 1000) // 5 sec
    val client = new AmazonDynamoDBAsyncClient(cc)
    client.setRegion(RegionUtils.getRegion(configuration.region))

    client
  }

}
