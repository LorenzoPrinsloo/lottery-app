package com.lottery.draw.service

import cats.effect.IO
import cats.effect.std.Random
import cats.syntax.all.*
import com.lottery.domain.{Ballot, Participant}
import com.lottery.draw.testkit.RecordingEmailClient
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.{InMemoryLotteryRepository, InMemoryParticipantRepository}
import munit.CatsEffectSuite
import scala.jdk.CollectionConverters.*
import java.time.ZoneId

class DrawServiceSpec extends CatsEffectSuite {

  private val ada = participant("Ada", "ada@example.com")
  private val bob = participant("Bob", "bob@example.com")
  private val zone = ZoneId.of("Europe/Amsterdam")

  private case class Setup(
      service: DrawService[IO],
      lotteries: InMemoryLotteryRepository[IO],
      mail: RecordingEmailClient
  )

  private def setup(seed: Long, registered: Participant*)(ballots: Ballot*): IO[Setup] =
    for {
      participants <- InMemoryParticipantRepository.make[IO](registered*)
      lotteries <- InMemoryLotteryRepository.make[IO]
      _ <- lotteries.submitBallots(today, ballots.toList)
      mail <- RecordingEmailClient.make
      random <- Random.scalaUtilRandomSeedLong[IO](seed)
    } yield Setup(DrawService.default[IO](lotteries, participants, mail, zone)(using summon, random), lotteries, mail)

  test("with no ballots there is no draw and no email") {
    setup(1L, ada)().flatMap { s =>
      (s.service.performDraw(today), s.mail.sent.get).tupled
    }.map { (result, sent) =>
      assertEquals(result, None)
      assertEquals(sent, Nil)
    }
  }

  test("the winning ballot is one of the lottery's ballots") {
    val ballots = List.fill(5)(ballot(ada.email)) ++ List.fill(5)(ballot(bob.email))
    (1L to 30L).toList.traverse { seed =>
      setup(seed, ada, bob)(ballots*).flatMap(_.service.performDraw(today))
    }.map(results => assert(results.forall(_.exists(r => ballots.exists(_.id == r.winningBallotId)))))
  }

  test("a draw closes the lottery and stores the result") {
    for {
      s <- setup(7L, ada)(ballot(ada.email), ballot(ada.email))
      result <- s.service.performDraw(today)
      open <- s.lotteries.getOpen(today)
      closed <- s.lotteries.getClosed(today)
    } yield {
      assertEquals(open, None)
      assertEquals(closed.map(_.winningBallotId), result.map(_.winningBallotId))
      assertEquals(closed.map(_.winnerEmail), Some(ada.email))
    }
  }

  test("the winner gets a congratulations email") {
    for {
      s <- setup(3L, ada)(ballot(ada.email))
      _ <- s.service.performDraw(today)
      sent <- s.mail.sent.get
    } yield {
      assertEquals(sent.size, 1)
      assertEquals(sent.head.getToRecipients.asScala.map(_.getAddress).toList, List(ada.email))
      assert(sent.head.getSubject.contains(today.toString), sent.head.getSubject)
    }
  }

  test("a winner who isn't registered still wins, but gets no email") {
    for {
      s <- setup(3L)(ballot("gone@example.com"))
      result <- s.service.performDraw(today)
      sent <- s.mail.sent.get
    } yield {
      assertEquals(result.map(_.winner), Some(None))
      assertEquals(sent, Nil)
    }
  }

  test("each ballot has an equal chance: 3 ballots out of 4 win about 75% of draws") {
    val draws = 2000
    for {
      participants <- InMemoryParticipantRepository.make[IO](ada, bob)
      lotteries <- InMemoryLotteryRepository.make[IO]
      mail <- RecordingEmailClient.make
      random <- Random.scalaUtilRandomSeedLong[IO](42L)
      service = DrawService.default[IO](lotteries, participants, mail, zone)(using summon, random)
      winners <- (0 until draws).toList.traverse { i =>
        val date = today.plusDays(i.toLong)
        val ballots = List.fill(3)(ballot(ada.email, date)) :+ ballot(bob.email, date)
        lotteries.submitBallots(date, ballots) *> service.performDraw(date).map(_.flatMap(_.winner).map(_.email))
      }
    } yield {
      val share = winners.count(_.contains(ada.email)).toDouble / draws
      assert(math.abs(share - 0.75) < 0.04, s"Ada won $share of draws")
    }
  }
}
