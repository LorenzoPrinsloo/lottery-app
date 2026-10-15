package com.lottery.modules

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.typelevel.ci.CIStringSyntax

class AuthMiddlewareSpec extends CatsEffectSuite {

  private val protectedApp: HttpApp[IO] =
    AuthMiddleware[IO]("s3cret").apply(AuthedRoutes.of[Unit, IO] { case GET -> Root / "secret" as _ => Ok("in") }).orNotFound

  private def call(headers: Header.Raw*): IO[Status] =
    protectedApp.run(headers.foldLeft(Request[IO](Method.GET, uri"/secret"))((r, h) => r.putHeaders(h))).map(_.status)

  test("lets a request with the right secret through") {
    call(Header.Raw(ci"X-Api-Secret", "s3cret")).assertEquals(Status.Ok)
  }

  test("rejects a request with the wrong secret") {
    call(Header.Raw(ci"X-Api-Secret", "guess")).assertEquals(Status.Unauthorized)
  }

  test("rejects a request with no secret") {
    call().assertEquals(Status.Unauthorized)
  }
}
