package com.lottery.api.routes

import cats.effect.IO
import com.lottery.api.service.LotteryService
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.{InMemoryLotteryRepository, InMemoryParticipantRepository}
import io.circe.Json
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.implicits.*
import java.time.LocalDate

class LotteryRoutesSpec extends CatsEffectSuite {

  private val future: LocalDate = LocalDate.now().plusDays(3)

  private val client: IO[(Client[IO], InMemoryLotteryRepository[IO])] =
    for {
      participants <- InMemoryParticipantRepository.make[IO]()
      lotteries <- InMemoryLotteryRepository.make[IO]
      routes = LotteryRoutes[IO](LotteryService.default[IO](participants, lotteries)).routes
    } yield (Client.fromHttpApp(routes.orNotFound), lotteries)

  private def post(c: Client[IO], uri: Uri, body: Json): IO[(Status, Json)] =
    c.run(Request[IO](Method.POST, uri).withEntity(body)).use(r => r.as[Json].map(r.status -> _).handleError(_ => r.status -> Json.Null))

  private def register(c: Client[IO], name: String, email: String) =
    post(c, uri"/participants", Json.obj("name" -> Json.fromString(name), "email" -> Json.fromString(email)))

  private def ballots(c: Client[IO], date: LocalDate, email: String, count: Int) =
    post(c, Uri.unsafeFromString(s"/lotteries/$date/ballots"), Json.obj("email" -> Json.fromString(email), "count" -> Json.fromInt(count)))

  test("POST /participants registers a participant and returns them") {
    client.flatMap((c, _) => register(c, "Ada", "ada@example.com")).map { (status, body) =>
      assertEquals(status, Status.Created)
      assertEquals(body.hcursor.get[String]("email"), Right("ada@example.com"))
    }
  }

  test("registering the same email twice is a 409 Conflict") {
    client.flatMap { (c, _) => register(c, "Ada", "ada@example.com") *> register(c, "Ada", "ada@example.com") }
      .map((status, _) => assertEquals(status, Status.Conflict))
  }

  test("POST /lotteries/{date}/ballots submits ballots for a future lottery") {
    client.flatMap { (c, _) => register(c, "Ada", "ada@example.com") *> ballots(c, future, "ada@example.com", 5) }
      .map { (status, body) =>
        assertEquals(status, Status.Ok)
        assertEquals(body.hcursor.get[Long]("ballotsSubmitted"), Right(5L))
        assertEquals(body.hcursor.get[String]("lotteryDate"), Right(future.toString))
      }
  }

  test("ballots for a past date are a 400 Bad Request") {
    client.flatMap { (c, _) => register(c, "Ada", "ada@example.com") *> ballots(c, LocalDate.now().minusDays(1), "ada@example.com", 1) }
      .map((status, _) => assertEquals(status, Status.BadRequest))
  }

  test("ballots from an unregistered email are a 400 Bad Request") {
    client.flatMap((c, _) => ballots(c, future, "ghost@example.com", 1)).map((status, _) => assertEquals(status, Status.BadRequest))
  }

  test("GET /lotteries/{date}/winner is 404 until the lottery has been drawn") {
    client.flatMap((c, _) => c.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/lotteries/$future/winner"))).use(r => IO.pure(r.status)))
      .assertEquals(Status.NotFound)
  }

  test("GET /lotteries/{date}/winner returns the drawn result") {
    client.flatMap { (c, lotteries) =>
      for {
      _ <- register(c, "Ada", "ada@example.com")
      _ <- lotteries.closeLottery(result(future, "ada@example.com"))
      body <- c.expect[Json](Uri.unsafeFromString(s"/lotteries/$future/winner"))
    } yield {
      assertEquals(body.hcursor.get[String]("winnerName"), Right("Ada"))
      assertEquals(body.hcursor.get[String]("lotteryDate"), Right(future.toString))
    }
    }
  }

  test("a date that isn't a date doesn't match any route") {
    client.flatMap((c, _) => c.run(Request[IO](Method.GET, uri"/lotteries/2030-13-45/winner")).use(r => IO.pure(r.status)))
      .assertEquals(Status.NotFound)
  }

  test("a body that isn't valid JSON is a 400 Bad Request") {
    client.flatMap((c, _) => c.run(Request[IO](Method.POST, uri"/participants").withEntity("{not json")).use(r => IO.pure(r.status)))
      .assertEquals(Status.BadRequest)
  }

  test("JSON with missing fields is a 400 Bad Request") {
    client.flatMap((c, _) => post(c, uri"/participants", Json.obj("name" -> Json.fromString("Ada"))))
      .map((status, _) => assertEquals(status, Status.BadRequest))
  }

  test("a ballot count that isn't a number is a 400 Bad Request") {
    client.flatMap { (c, _) =>
      register(c, "Ada", "ada@example.com") *>
        post(c, Uri.unsafeFromString(s"/lotteries/$future/ballots"), Json.obj("email" -> Json.fromString("ada@example.com"), "count" -> Json.fromString("five")))
    }.map((status, _) => assertEquals(status, Status.BadRequest))
  }

  test("a ballot count of zero is a 400 Bad Request") {
    client.flatMap((c, _) => register(c, "Ada", "ada@example.com") *> ballots(c, future, "ada@example.com", 0))
      .map((status, _) => assertEquals(status, Status.BadRequest))
  }

  test("an invalid email is a 400 Bad Request") {
    client.flatMap((c, _) => register(c, "Ada", "not-an-email")).map((status, _) => assertEquals(status, Status.BadRequest))
  }
}
