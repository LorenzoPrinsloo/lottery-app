package com.lottery.draw.stream

import cats.effect.kernel.Async
import com.lottery.logging.Logging
import java.time.{Duration, LocalDate, LocalTime, ZonedDateTime}
import scala.concurrent.duration.*
import cats.implicits.*
import com.lottery.draw.service.DrawService

trait DrawWorker[F[_]] extends Logging[F] {
  def cronStream(): fs2.Stream[F, Unit]
}
object DrawWorker {
  def default[F[_]: Async](service: DrawService[F]): DrawWorker[F] =
    new DrawWorker[F] {
      override def cronStream(): fs2.Stream[F, Unit] = {
        val now = ZonedDateTime.now()
        // Schedule to run at 00:01 every day
        val timeToFirstRun = {
          val midnight = now.toLocalDate.plusDays(1).atTime(LocalTime.of(0, 1))
          val duration = Duration.between(now, midnight)
          scala.concurrent.duration
            .FiniteDuration(Math.max(0, duration.toNanos), "nanos")
        }

        fs2.Stream.sleep[F](timeToFirstRun) ++
          fs2.Stream.awakeEvery[F](24.hours).evalMap { _ =>
            val yesterday = LocalDate.now().minusDays(1)
            logger.info(s"Cron triggered: performing draw for $yesterday") *>
              service.performDraw(yesterday).void
          }
      }
    }
}
