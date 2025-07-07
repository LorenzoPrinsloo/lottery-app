package com.lottery.draw.routes

import cats.effect.{Async, Concurrent}
import com.lottery.draw.service.DrawService
import com.lottery.logging.Logging
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import cats.implicits.*
import com.lottery.domain.error.ApiError
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import java.time.LocalDate

class DrawRoutes[F[_]: Async: Concurrent](service: DrawService[F])
    extends Logging[F]
    with Http4sDsl[F] {
  private object LocalDateVar {
    def unapply(str: String): Option[LocalDate] = {
      Either.catchNonFatal(LocalDate.parse(str)).toOption
    }
  }

  extension(e: ApiError) {
    def toResponse: F[Response[F]] = {
      e match {
        case ApiError.BadRequest(details) => BadRequest(details)
        case ApiError.Conflict(details)   => Conflict(details)
        case ApiError.InternalServerError(details) =>
          InternalServerError(details)
      }
    }
  }

  private def withErrorHandling(
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

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / LocalDateVar(date) =>
      withErrorHandling(req) {
        for {
          _ <- logger.info(
            s"Received developer request to perform draw for $date"
          )
          result <- service.performDraw(date)
          response <- result.fold(ifEmpty =
            NotFound("No Lottery for given date")
          )(r => Ok(r))
        } yield response
      }
  }
}
