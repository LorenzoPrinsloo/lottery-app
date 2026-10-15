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
}
