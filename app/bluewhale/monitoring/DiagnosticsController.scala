package bluewhale.monitoring

import info.BuildInfo
import play.api.mvc.{Action, Controller}

class DiagnosticsController extends Controller {

  def heartbeatCheck = Action(Ok("Ok"))

  def exceptionCheck = Action {
    throw new RuntimeException("Simulated RuntimeException")
    Ok("This code is not reachable but necessary to compile")
  }

  def version = Action(Ok(BuildInfo.toString.replace(", ", "\n")))
}

object DiagnosticsController extends DiagnosticsController