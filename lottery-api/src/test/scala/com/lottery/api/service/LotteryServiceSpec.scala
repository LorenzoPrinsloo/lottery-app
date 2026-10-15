package com.lottery.api.service

import cats.effect.IO
import cats.syntax.all.*
import com.lottery.domain.error.ApiError
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.{InMemoryLotteryRepository, InMemoryParticipantRepository}
import munit.CatsEffectSuite

class LotteryServiceSpec extends CatsEffectSuite {

  private val ada = participant("Ada", "ada@example.com")
  private val bob = participant("Bob", "bob@example.com")

  private def service(registered: com.lottery.domain.Participant*) =
    for {
      participants <- InMemoryParticipantRepository.make[IO](registered*)
      lotteries <- InMemoryLotteryRepository.make[IO]
    } yield (LotteryService.default[IO](participants, lotteries), participants, lotteries)

  test("registers a new participant") {
    service().flatMap { (svc, participants, _) =>
      for {
      registered <- svc.registerParticipant(ada)
      stored <- participants.get(ada.email)
      } yield {
      assertEquals(registered, ada)
      assertEquals(stored, Some(ada))
    }
    }
  }

  test("won't register the same email twice") {
    service(ada).flatMap((svc, _, _) => svc.registerParticipant(ada).attempt).map { r =>
      assert(r.left.exists(_.isInstanceOf[ApiError.Conflict]), r)
    }
  }

  test("only registered participants can submit ballots") {
    service().flatMap((svc, _, _) => svc.submitBallots("ghost@example.com", today, 3).attempt).map { r =>
      assert(r.left.exists(_.isInstanceOf[ApiError.BadRequest]), r)
    }
  }

  test("submitting ballots reports how many the participant now holds for that lottery") {
    service(ada, bob).flatMap { (svc, _, lotteries) =>
      for {
      first <- svc.submitBallots(ada.email, today, 3)
      _ <- svc.submitBallots(bob.email, today, 4)
      second <- svc.submitBallots(ada.email, today, 2)
      open <- lotteries.getOpen(today)
      } yield {
      assertEquals(first.ballotsSubmitted, 3L)
      assertEquals(first.totalBallotsForLottery, 3L)
      assertEquals(second.totalBallotsForLottery, 5L)
      assertEquals(second.lotteryDate, today)
      assertEquals(second.drawDate, today.plusDays(1))
      assertEquals(open.map(_.ballots.size), Some(9))
    }
    }
  }

  test("every ballot gets its own id and the participant's email") {
    service(ada).flatMap { (svc, _, lotteries) =>
      for {
      _ <- svc.submitBallots(ada.email, today, 50)
      open <- lotteries.getOpen(today)
      } yield {
      val ballots = open.toList.flatMap(_.ballots)
      assertEquals(ballots.map(_.id).distinct.size, 50)
      assert(ballots.forall(b => b.email == ada.email && b.lotteryDate == today))
    }
    }
  }

  test("asking for a result that hasn't been drawn is NotFound") {
    service().flatMap((svc, _, _) => svc.fetchLotteryResult(today).attempt).map { r =>
      assert(r.left.exists(_.isInstanceOf[ApiError.NotFound]), r)
    }
  }

  test("a drawn result names the winner") {
    val r = result(today, ada.email)
    service(ada).flatMap { (svc, _, lotteries) =>
      for {
      _ <- lotteries.closeLottery(r)
      response <- svc.fetchLotteryResult(today)
      } yield {
      assertEquals(response.winnerName, Some("Ada"))
      assertEquals(response.winningBallot, r.winningBallotId)
      assertEquals(response.drawnAt, r.drawnAt)
    }
    }
  }

  test("a winner who is no longer registered is reported without a name") {
    service().flatMap { (svc, _, lotteries) =>
      for {
      _ <- lotteries.closeLottery(result(today, "gone@example.com"))
      response <- svc.fetchLotteryResult(today)
      } yield assertEquals(response.winnerName, None)
    }
  }

  private def badRequest[A](io: IO[A]): IO[Unit] =
    io.attempt.map(r => assert(r.left.exists(_.isInstanceOf[ApiError.BadRequest]), r))

  test("a participant needs a name") {
    service().flatMap((svc, _, _) => badRequest(svc.registerParticipant(ada.copy(name = "   "))))
  }

  test("a participant needs a valid email address") {
    service().flatMap { (svc, _, _) =>
      List("", "ada", "ada@", "@example.com", "ada@example", "a da@example.com").traverse_(e => badRequest(svc.registerParticipant(ada.copy(email = e))))
    }
  }

  test("emails are stored trimmed and in lower case, so case doesn't create a second participant") {
    service().flatMap { (svc, participants, _) =>
      for {
        registered <- svc.registerParticipant(ada.copy(name = " Ada ", email = " Ada@Example.COM "))
        again <- svc.registerParticipant(ada.copy(email = "ada@example.com")).attempt
        stored <- participants.get("ada@example.com")
      } yield {
        assertEquals(registered.email, "ada@example.com")
        assertEquals(registered.name, "Ada")
        assertEquals(stored.map(_.email), Some("ada@example.com"))
        assert(again.left.exists(_.isInstanceOf[ApiError.Conflict]), again)
      }
    }
  }

  test("ballots can be submitted with the email in any case") {
    service(ada).flatMap((svc, _, _) => svc.submitBallots(" ADA@example.com", today, 2)).map(r => assertEquals(r.totalBallotsForLottery, 2L))
  }

  test("a ballot count must be between 1 and 1000, and a rejected count stores nothing") {
    service(ada).flatMap { (svc, _, lotteries) =>
      List(0, -1, Int.MinValue, 1001, Int.MaxValue).traverse_(n => badRequest(svc.submitBallots(ada.email, today, n))) *>
        lotteries.getOpen(today).map(open => assertEquals(open, None))
    }
  }

  test("any count from 1 to 1000 is accepted") {
    service(ada).flatMap { (svc, _, _) =>
      List(1, 2, 999, 1000).traverse(n => svc.submitBallots(ada.email, today.plusDays(n.toLong), n).map(_.ballotsSubmitted))
    }.map(counts => assertEquals(counts, List(1L, 2L, 999L, 1000L)))
  }

  test("a conflict doesn't reveal the existing participant's details") {
    service(ada).flatMap((svc, _, _) => svc.registerParticipant(ada.copy(name = "Someone else")).attempt).map { r =>
      assert(r.left.exists(e => e.isInstanceOf[ApiError.Conflict] && !e.getMessage.contains("Ada") && !e.getMessage.contains("registeredAt")), r)
    }
  }
}
