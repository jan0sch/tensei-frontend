/*
 * Copyright (C) 2014 - 2017  Contributors as noted in the AUTHORS.md file
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import jp.t2v.lab.play2.auth.{ AuthConfig, CookieTokenAccessor }
import models.{ Account, Authorities }
import play.api.mvc.Results._
import play.api.mvc._
import play.api.Play.current

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.{ classTag, ClassTag }

/**
  * Basic implementation of the `AuthConfig` trait from the `play2-auth` package.
  */
trait AuthConfigImpl extends AuthConfig {

  /**
    * A type that is used to identify a user.
    * `String`, `Int`, `Long` and so on.
    */
  override type Id = Int

  /**
    * A type that represents a user in your application.
    */
  override type User = Account

  /**
    * A type that is defined by every action for authorisation.
    *
    * For example the following trait:
    *
    * {{{
    * sealed trait Role
    * case object Administrator extends Role
    * case object NormalUser extends Role
    * }}}
    */
  override type Authority = Authorities

  /**
    * A `ClassTag` is used to retrieve an id from the Cache API.
    */
  override def idTag: ClassTag[Id] = classTag[Id]

  /**
    * A function that returns a `User` object from an `Id`.
    * You can alter the procedure to suit your application.
    *
    * @param id  The unique database id e.g. primary key for the user.
    * @param context An implicit execution context.
    * @return A future holding an option to the resolved user.
    */
  override def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]]

  /**
    * The default session timeout in seconds.
    *
    * @return The number of seconds that a session is valid.
    * @todo Remove deprecated dependency on `play.api.Play.current`.
    */
  override def sessionTimeoutInSeconds: Int =
    (current.configuration
      .getMilliseconds("application.session.timeout")
      .getOrElse(14400L) / 1000).toInt

  /**
    * Where to redirect the user after logging out
    */
  override def logoutSucceeded(
      request: RequestHeader
  )(implicit context: ExecutionContext): Future[Result] =
    Future.successful(Redirect(routes.Authentication.login()))

  /**
    * If the user is not logged in.
    */
  override def authenticationFailed(
      request: RequestHeader
  )(implicit context: ExecutionContext): Future[Result] =
    Future.successful(Redirect(routes.Authentication.login()))

  /**
    * Where to redirect the user after a successful login.
    */
  override def loginSucceeded(
      request: RequestHeader
  )(implicit context: ExecutionContext): Future[Result] =
    Future.successful(Redirect(routes.DashboardController.index()))

  /**
    * If the user tries to access a forbidden resource.
    */
  override def authorizationFailed(
      request: RequestHeader,
      user: User,
      authority: Option[Authority]
  )(implicit context: ExecutionContext): Future[Result] =
    Future.successful(Forbidden(views.html.errors.forbidden()))

  /**
    * A function that determines what authority a user has.
    *
    * === The user gets access to a resource if one of the following conditions is met. ===
    *
    * {{{
    * - The user is an administrator.
    * - The user is the owner of the resource.
    * - The user belongs to the group that owns the resource and the group permissions are not empty.
    * - The world permissions are not empty.
    * }}}
    *
    * === Further details ===
    *
    * This function is intended to be used the following way. You pass in
    * the user and an `Authority` that is generated by the appropriate
    * function call on the [[models.AuthorisableResource]] trait.
    * You should use the function `getReadAuthorisation` to check for
    * read permissions and so on.
    */
  def authorize(user: User, authority: Authority)(
      implicit context: ExecutionContext
  ): Future[Boolean] = Future.successful {
    authority match {
      case Authorities.AdminAuthority          => user.isAdmin
      case Authorities.UserAuthority           => true
      case Authorities.UserWithIdAuthority(id) => user.id.contains(id) || user.isAdmin
      case Authorities.ResourceAuthority(ownerId, groupId, groupPermissions, worldPermissions) =>
        if (user.isAdmin || user.id.contains(ownerId) || worldPermissions.nonEmpty || (groupId
              .exists(id => user.groupIds.contains(id)) && groupPermissions.nonEmpty))
          true
        else
          false
    }
  }

  /**
    * You can custom SessionID Token handler.
    *
    * @todo Remove deprecated dependency on `play.api.Play.current`.
    */
  override lazy val tokenAccessor = new CookieTokenAccessor(
    /*
     * Whether use the secure option or not use it in the cookie.
     */
    cookieSecureOption =
      current.configuration.getBoolean("application.cookies.secure").getOrElse(true),
    cookieMaxAge = Option(sessionTimeoutInSeconds)
  )

}