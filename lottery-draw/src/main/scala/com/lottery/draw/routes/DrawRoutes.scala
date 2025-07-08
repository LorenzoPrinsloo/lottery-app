package com.lottery.draw.routes

import cats.effect.{Async, Concurrent}
import com.lottery.draw.service.DrawService
import com.lottery.logging.Logging
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import cats.implicits.*
import com.lottery.domain.error.ApiError
import com.lottery.modules.Http.RouteDsl
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import java.time.LocalDate

class DrawRoutes[F[_]: Async: Concurrent](service: DrawService[F])
    extends RouteDsl[F] {

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
