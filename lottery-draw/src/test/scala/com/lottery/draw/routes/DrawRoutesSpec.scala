package com.lottery.draw.routes

import cats.effect.IO
import cats.effect.std.Random
import com.lottery.draw.service.DrawService
import com.lottery.draw.testkit.RecordingEmailClient
import com.lottery.modules.AuthMiddleware
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.{InMemoryLotteryRepository, InMemoryParticipantRepository}
import io.circe.Json
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.typelevel.ci.CIStringSyntax
import java.time.ZoneId

class DrawRoutesSpec extends CatsEffectSuite {

  private def app(withBallots: Boolean): IO[HttpApp[IO]] =
    for {
      participants <- InMemoryParticipantRepository.make[IO](participant())
      lotteries <- InMemoryLotteryRepository.make[IO]
      _ <- if (withBallots) lotteries.submitBallots(today, List(ballot())).void else IO.unit
      mail <- RecordingEmailClient.make
      random <- Random.scalaUtilRandomSeedLong[IO](1L)
      service = DrawService.default[IO](lotteries, participants, mail, ZoneId.of("UTC"))(using summon, random)
    } yield AuthMiddleware[IO]("s3cret").apply(DrawRoutes[IO](service).routes).orNotFound

  private def draw(app: HttpApp[IO], secret: Option[String]): IO[(Status, Option[Json])] = {
    val req = Request[IO](Method.POST, Uri.unsafeFromString(s"/$today"))
    val withSecret = secret.fold(req)(s => req.putHeaders(Header.Raw(ci"X-Api-Secret", s)))
    app.run(withSecret).flatMap(r => r.as[Json].attempt.map(body => r.status -> body.toOption))
  }

  test("a draw needs the API secret") {
    app(withBallots = true).flatMap(draw(_, None)).map((status, _) => assertEquals(status, Status.Unauthorized))
  }

  test("a draw with the secret returns the winner") {
    app(withBallots = true).flatMap(draw(_, Some("s3cret"))).map { (status, body) =>
      assertEquals(status, Status.Ok)
      assertEquals(body.flatMap(_.hcursor.downField("winner").get[String]("email").toOption), Some("ada@example.com"))
    }
  }

  test("a draw for a date without ballots is 404") {
    app(withBallots = false).flatMap(draw(_, Some("s3cret"))).map((status, _) => assertEquals(status, Status.NotFound))
  }
}
