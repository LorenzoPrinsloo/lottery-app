package com.lottery.api

import cats.effect.std.UUIDGen
import cats.effect.testkit.TestControl
import cats.effect.{IO, Ref}
import com.lottery.api.routes.LotteryRoutes
import com.lottery.api.service.LotteryService
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.{InMemoryLotteryRepository, InMemoryParticipantRepository}
import io.circe.Json
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.implicits.*
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util.UUID
import scala.concurrent.duration.*

/** Time and ids come from Cats Effect, so they can be controlled: these tests run on a simulated clock. */
class ClockAndZoneSpec extends CatsEffectSuite {

  private val amsterdam = ZoneId.of("Europe/Amsterdam")

  /** Runs `test` on a simulated clock that reads `instant`. */
  private def at[A](instant: Instant)(test: IO[A]): IO[A] =
    TestControl.executeEmbed(IO.sleep(instant.toEpochMilli.millis) *> test)

  private def countingIds: IO[UUIDGen[IO]] =
    Ref.of[IO, Long](0L).map(n => new UUIDGen[IO] { def randomUUID: IO[UUID] = n.getAndUpdate(_ + 1).map(new UUID(0L, _)) })

  private def client(zone: ZoneId): IO[(Client[IO], InMemoryParticipantRepository[IO], InMemoryLotteryRepository[IO])] =
    for {
      participants <- InMemoryParticipantRepository.make[IO](participant())
      lotteries <- InMemoryLotteryRepository.make[IO]
      ids <- countingIds
      service = LotteryService.default[IO](participants, lotteries, zone)(using summon, ids)
    } yield (Client.fromHttpApp(LotteryRoutes[IO](service, zone).routes.orNotFound), participants, lotteries)

  private def submit(c: Client[IO], date: LocalDate): IO[Status] =
    c.run(Request[IO](Method.POST, Uri.unsafeFromString(s"/lotteries/$date/ballots"))
      .withEntity(Json.obj("email" -> Json.fromString("ada@example.com"), "count" -> Json.fromInt(3)))).use(r => IO.pure(r.status))

  test("ballots are stamped with the current time and get ids from UUIDGen") {
    val instant = Instant.parse("2030-06-15T10:00:00Z")
    at(instant) {
      client(amsterdam).flatMap { (c, _, lotteries) =>
        submit(c, today) *> lotteries.getOpen(today)
      }
    }.map { open =>
      val ballots = open.toList.flatMap(_.ballots)
      assertEquals(ballots.map(_.submittedAt).distinct, List(LocalDateTime.ofInstant(instant, amsterdam)))
      assertEquals(ballots.map(_.id.toString).sorted, (0L until 3L).map(n => new UUID(0L, n).toString).toList)
    }
  }

  test("registration is stamped with the current time in the configured zone") {
    val instant = Instant.parse("2030-06-15T10:00:00Z")
    at(instant) {
      client(amsterdam).flatMap { (c, participants, _) =>
        c.run(Request[IO](Method.POST, uri"/participants")
          .withEntity(Json.obj("name" -> Json.fromString("Bob"), "email" -> Json.fromString("bob@example.com")))).use_ *>
          participants.get("bob@example.com")
      }
    }.map(bob => assertEquals(bob.map(_.registeredAt), Some(LocalDateTime.of(2030, 6, 15, 12, 0))))
  }

  test("'today' follows the configured zone: at 00:30 in Amsterdam, the previous day is in the past") {
    val halfPastMidnightInAmsterdam = Instant.parse("2030-06-14T22:30:00Z")
    val utcToday = LocalDate.of(2030, 6, 14)
    for {
      amsterdamStatus <- at(halfPastMidnightInAmsterdam)(client(amsterdam).flatMap((c, _, _) => submit(c, utcToday)))
      utcStatus <- at(halfPastMidnightInAmsterdam)(client(ZoneId.of("UTC")).flatMap((c, _, _) => submit(c, utcToday)))
    } yield {
      assertEquals(amsterdamStatus, Status.BadRequest)
      assertEquals(utcStatus, Status.Ok)
    }
  }
}
