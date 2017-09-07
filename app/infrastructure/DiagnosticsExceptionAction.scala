package infrastructure

import javax.inject.Inject

import bluewhale.BluewhaleConfiguration
import play.api.mvc.Results.NotFound
import play.api.mvc._

import scala.concurrent.Future

class DiagnosticsExceptionAction @Inject()(configuration: BluewhaleConfiguration) extends ActionBuilder[Request] {
  def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
    if (configuration.allowDiagnosticsException)
      block(request)
    else
      Future.successful(NotFound)
  }
}