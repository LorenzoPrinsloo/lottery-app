package com.lottery.domain

import com.lottery.testkit.Fixtures.*
import io.circe.parser.decode
import io.circe.syntax.*
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

class CodecSpec extends ScalaCheckSuite {

  property("ballots survive a JSON round trip") {
    forAll(ballots)(b => decode[Ballot](b.asJson.noSpaces) == Right(b))
  }

  property("lottery results survive a JSON round trip") {
    forAll(ballots, times) { (b, drawnAt) =>
      val r = LotteryResult(b.lotteryDate, b.id, b.email, drawnAt)
      decode[LotteryResult](r.asJson.noSpaces) == Right(r)
    }
  }

  property("participants survive a JSON round trip") {
    forAll(emails, times) { (e, t) =>
      val p = Participant("Ada", e, t)
      decode[Participant](p.asJson.noSpaces) == Right(p)
    }
  }

  test("a ballot id that isn't a UUID is a decoding failure, not an exception") {
    val json = """{"id":"nope","email":"a@b.com","lotteryDate":"2030-01-01","submittedAt":"2030-01-01T00:00"}"""
    assert(decode[Ballot](json).isLeft)
  }
}
