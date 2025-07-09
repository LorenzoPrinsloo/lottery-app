package com.lottery.modules

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware as Http4sAuthMiddleware
import org.http4s.implicits.http4sStringSyntax

object AuthMiddleware {

  /** The authentication function. It inspects a request and returns an
    * `OptionT` containing the "user" if successful.
    *
    * [A] =>> OptionT[F, A] == OptionT[F, *] in scala 2 with kind projector
    * plugin
    */
  private def authUser[F[_]: Async](
      secret: String
  ): Kleisli[[A] =>> OptionT[F, A], Request[F], Unit] =
    Kleisli { req =>
      val maybeHeader = req.headers.get("X-Api-Secret".ci)
      val isAuthorized = maybeHeader.exists(_.head.value == secret)

      if (isAuthorized) {
        OptionT.liftF(Async[F].pure(()))
      } else {
        OptionT.none[F, Unit]
      }
    }

  def apply[F[_]: Async](secret: String): Http4sAuthMiddleware[F, Unit] =
    Http4sAuthMiddleware(authUser(secret))

}
