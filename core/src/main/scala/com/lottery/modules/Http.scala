package com.lottery.modules

import cats.effect.{Async, Resource}
import com.lottery.domain.config.HttpServerConfig
import com.lottery.domain.error.ApiError
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, Request, Response}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

import java.time.LocalDate
import cats.implicits.*
import com.lottery.logging.Logging

object Http {

  /** A resource for the HTTP server.
    */
  def httpServer[F[_]: Async](
      config: HttpServerConfig,
      httpApp: HttpApp[F]
  ): Resource[F, Server] = {
    EmberServerBuilder
      .default[F]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(httpApp)
      .build
  }

  trait RouteDsl[F[_]: Async] extends Http4sDsl[F] with Logging[F] {
    object LocalDateVar {
      def unapply(str: String): Option[LocalDate] = {
        Either.catchNonFatal(LocalDate.parse(str)).toOption
      }
    }

    def withErrorHandling(
        request: Request[F]
    )(block: => F[Response[F]]): F[Response[F]] = {
      block
        .onError(error =>
          logger.error(error)(s"${request.method} ${request.uri} Failed")
        )
        .recoverWith {
          case apiError: ApiError => apiError.toResponse
          case e                  => InternalServerError(s"Unhandled Exception: $e")
        }
    }

    extension(e: ApiError) {
      def toResponse: F[Response[F]] = {
        e match {
          case ApiError.BadRequest(details) => BadRequest(details)
          case ApiError.Conflict(details)   => Conflict(details)
          case ApiError.InternalServerError(details) =>
            InternalServerError(details)
          case ApiError.NotFound(details) => NotFound(details)
        }
      }
    }
  }
}
