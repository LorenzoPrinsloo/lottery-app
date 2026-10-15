package com.lottery.draw.stream

import cats.effect.testkit.TestControl
import cats.effect.{IO, Ref}
import com.lottery.draw.domain.config.CronConfig
import com.lottery.draw.domain.response.LotteryResultResponse
import com.lottery.draw.service.DrawService
import munit.CatsEffectSuite
import java.time.{Instant, LocalDate, LocalTime, ZoneId}
import scala.concurrent.duration.*

/** Runs the cron stream on a simulated clock and records when each draw happens, and for which lottery. */
class DrawWorkerSpec extends CatsEffectSuite {

  private val amsterdam = ZoneId.of("Europe/Amsterdam")

  private class RecordingDrawService(val draws: Ref[IO, List[(Instant, LocalDate)]], failFirst: Boolean) extends DrawService[IO] {
    override def performDraw(date: LocalDate): IO[Option[LotteryResultResponse]] =
      for {
        now <- IO.realTimeInstant
        previous <- draws.getAndUpdate(_ :+ (now -> date))
        _ <- IO.raiseWhen(failFirst && previous.isEmpty)(new RuntimeException("Redis is down"))
      } yield None
  }

  /** Starts the worker at `start` and returns the draws made in `runFor` of simulated time. */
  private def draws(start: Instant, runFor: FiniteDuration, config: CronConfig, failFirst: Boolean = false): IO[List[(Instant, LocalDate)]] =
    TestControl.executeEmbed {
      for {
        _ <- IO.sleep(start.toEpochMilli.millis)
        recorded <- Ref.of[IO, List[(Instant, LocalDate)]](Nil)
        service = new RecordingDrawService(recorded, failFirst)
        _ <- DrawWorker.default[IO](service, config).cronStream().interruptAfter(runFor).compile.drain
        all <- recorded.get
      } yield all
    }

  private val nightly = CronConfig("Europe/Amsterdam", LocalTime.of(0, 1), dayOffset = 1)

  test("draws at the draw time every night, each for the previous day's lottery") {
    draws(Instant.parse("2030-06-15T10:00:00Z"), 3.days, nightly).map { made =>
      assertEquals(made.map(_._1.atZone(amsterdam).toLocalDateTime.toString), List("2030-06-16T00:01", "2030-06-17T00:01", "2030-06-18T00:01"))
      assertEquals(made.map(_._2), List(LocalDate.of(2030, 6, 15), LocalDate.of(2030, 6, 16), LocalDate.of(2030, 6, 17)))
    }
  }

  test("keeps drawing at 00:01 local time across the change to summer time") {
    draws(Instant.parse("2030-03-29T12:00:00Z"), 3.days, nightly).map { made =>
      assertEquals(made.map(_._1.atZone(amsterdam).toLocalTime), List.fill(3)(LocalTime.of(0, 1)))
      assertEquals(made.map(_._1.toString), List("2030-03-29T23:01:00Z", "2030-03-30T23:01:00Z", "2030-03-31T22:01:00Z"))
    }
  }

  test("with day-offset 0 and the draw time already past, it waits for tomorrow instead of drawing at once") {
    val config = nightly.copy(drawTime = LocalTime.of(20, 0), dayOffset = 0)
    draws(Instant.parse("2030-06-15T19:00:00Z"), 30.hours, config).map { made =>
      assertEquals(made.map(_._1.atZone(amsterdam).toLocalDateTime.toString), List("2030-06-16T20:00"))
      assertEquals(made.map(_._2), List(LocalDate.of(2030, 6, 16)))
    }
  }

  test("a failed draw doesn't stop the schedule") {
    draws(Instant.parse("2030-06-15T10:00:00Z"), 2.days, nightly, failFirst = true).map { made =>
      assertEquals(made.map(_._2), List(LocalDate.of(2030, 6, 15), LocalDate.of(2030, 6, 16)))
    }
  }
}
