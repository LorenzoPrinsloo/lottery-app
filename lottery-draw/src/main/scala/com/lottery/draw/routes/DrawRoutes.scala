package com.lottery.draw.routes

import cats.effect.{Async, Concurrent}
import com.lottery.draw.service.DrawService
import com.lottery.logging.Logging
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.implicits.*
import java.time.LocalDate

class DrawRoutes[F[_]: Async: Concurrent](service: DrawService[F])
    extends Logging[F]
    with Http4sDsl[F] {
  private object LocalDateVar {
    def unapply(str: String): Option[LocalDate] = {
      Either.catchNonFatal(LocalDate.parse(str)).toOption
    }
  }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case POST -> Root / LocalDateVar(date) =>
      logger.info(s"Received developer request to perform draw for $date") *>
        service.performDraw(date) *>
        Ok(s"Draw simulation triggered for $date.")
  }
}
