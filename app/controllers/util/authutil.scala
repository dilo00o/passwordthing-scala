package controllers.util

import play.api._
import play.api.mvc._

import models.User._
import models._

import controllers._

// Based on this example: 
// http://www.playframework.org/documentation/2.0.1/ScalaSecurity
trait Secured {
  self: Controller =>

  // ----------------------------------------------------------------------
  // Public methods
  // ----------------------------------------------------------------------

  def username(request: RequestHeader) = request.session.get(Security.username)

  def onUnauthorized(request: RequestHeader) = Results.Forbidden

  def onNeedLogin(request: RequestHeader) =
    Results.Redirect(routes.Auth.login())

  // Chain this to a controller action, e.g.:
  //
  //    def index = ActionWithUser { user => implicit request =>
  //      Ok(views.html.index(...))
  //    }
  def ActionWithUser(f: User => Request[Any] => Result) = withAuth {
    username => implicit request =>

    callBlockActionWithUser(username, request, f)
  }

  // Version of ActionWithUser() that accepts a BodyParser and passes it through
  // to the generated Action.
  def ActionWithUser[T](bodyParser: BodyParser[T])
                       (f: User => Request[T] => Result) = {

    withAuth(bodyParser) { username => implicit request =>
      callBlockActionWithUser(username, request, f)
    }
  }

  // Chain this to a controller action, e.g.:
  //
  //    def index = ActionWithAdminUser { user => implicit request =>
  //      Ok(views.html.index(...))
  //    }
  def ActionWithAdminUser(f: User => Request[Any] => Result) = {
    ActionWithUser { user => implicit request =>
      callIfAdmin(user, request, f)
    }
  }

  // Version of ActionWithAdminUser() that accepts a BodyParser and passes it
  // through to the generated Action.
  def ActionWithAdminUser[T](bodyParser: BodyParser[T])
                            (f: User => Request[Any] => Result) = {
    ActionWithUser(bodyParser) { user => implicit request =>
      callIfAdmin(user, request, f)
    }
  }

  // ----------------------------------------------------------------------
  // Private methods
  // ----------------------------------------------------------------------

  private def callIfAdmin[T](user: User,
                             request: Request[T],
                             f: User => Request[T] => Result) = {
    if (user.isAdmin)
      f(user)(request)

    else {
      Logger.error(
        "Non-admin user %s attempted unauthorized access.".format(user.username)
      )
      onUnauthorized(request)
    }
  }

  private def callBlockActionWithUser[T](username: String,
                                   request: Request[T],
                                   f: User => Request[T] => Result) = {
    // Map the user name to the user, and pass it to the block.
    User.findByName(username).fold(
      { error =>

        Logger.error(error)
        onUnauthorized(request)
      },

      { user => f(user)(request) }
    )
  }

  private def withAuth(f: String => Request[AnyContent] => Result) = {
    Security.Authenticated(username, onNeedLogin) { username =>
      Action(request => f(username)(request))
    }
  }

  // Version of withAuth() that accepts a BodyParser and passes it through
  // to the generated Action.
  private def withAuth[T](bodyParser: BodyParser[T])
                 (f: String => Request[T] => Result) = {
    Security.Authenticated(username, onNeedLogin) { username =>
      Action(bodyParser)(request => f(username)(request))
    }
  }

}
