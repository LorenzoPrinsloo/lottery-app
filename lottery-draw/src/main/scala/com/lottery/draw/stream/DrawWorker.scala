package com.lottery.draw.stream

import cats.effect.kernel.Async
import cats.implicits.*
import com.lottery.draw.domain.config.CronConfig
import com.lottery.draw.service.DrawService
import com.lottery.logging.Logging
import java.time.{Duration, ZoneId}
import scala.concurrent.duration.*

trait DrawWorker[F[_]] extends Logging[F] {
  def cronStream(): fs2.Stream[F, Unit]
}
object DrawWorker {
  def default[F[_]: Async](
      service: DrawService[F],
      config: CronConfig
  ): DrawWorker[F] =
    new DrawWorker[F] {
      private val zone: ZoneId = ZoneId.of(config.timeZone)

      /** Sleeps until the next scheduled draw, then draws; a failed draw is logged and the schedule carries on. */
      private val drawNext: F[Unit] =
        for {
          now <- Async[F].realTimeInstant
          run = DrawSchedule.nextRun(now.atZone(zone), config.drawTime)
          lotteryDate = DrawSchedule.lotteryFor(run, config.dayOffset)
          _ <- logger.info(s"Next draw at $run, for the lottery of $lotteryDate")
          _ <- Async[F].sleep(Duration.between(now, run.toInstant).toNanos.nanos)
          _ <- logger.info(s"Cron triggered: performing draw for $lotteryDate")
          _ <- service.performDraw(lotteryDate).void.handleErrorWith { error =>
            logger.error(error)(s"Draw for $lotteryDate failed; the schedule continues with the next draw")
          }
        } yield ()

      override def cronStream(): fs2.Stream[F, Unit] =
        fs2.Stream.repeatEval(drawNext)
    }
}
