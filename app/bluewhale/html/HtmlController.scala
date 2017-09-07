package bluewhale.html


import play.api.mvc.{Action, Controller}


class HtmlController extends Controller {

  def viewx = Action {
    Ok("<p>Your new application is ready.</p>").as(HTML)
  }

}
