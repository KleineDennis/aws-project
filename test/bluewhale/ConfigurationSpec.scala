package bluewhale

import com.autoscout24.eventpublisher24.events.EventConfig
import org.scalatest.{FeatureSpec, Matchers}
import org.scalatestplus.play.OneAppPerSuite

class ConfigurationSpec extends FeatureSpec with Matchers with OneAppPerSuite {
  feature("Configuration") {
    scenario("should configure event publisher") {
      val expectedName = "local"

      EventConfig.stackName shouldBe expectedName
    }

    // Cannot assert the value of version, as build.sbt changes the version depending on the build environment.
    scenario("should have a configured version") {
      EventConfig.version.getClass shouldBe classOf[String]
    }
  }
}
