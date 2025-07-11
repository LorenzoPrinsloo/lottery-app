package com.lottery.draw.routes

import cats.effect.{Async, Concurrent}
import com.lottery.draw.service.DrawService
import com.lottery.logging.Logging
import org.http4s.{AuthedRoutes, Response}
import cats.implicits.*
import com.lottery.modules.Http.RouteDsl
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder

class DrawRoutes[F[_]: Async: Concurrent](service: DrawService[F])
    extends RouteDsl[F] {

  val routes: AuthedRoutes[Unit, F] = AuthedRoutes.of[Unit, F] {
    case authedReq @ POST -> Root / LocalDateVar(date) as _ =>
      withErrorHandling(authedReq.req) {
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
