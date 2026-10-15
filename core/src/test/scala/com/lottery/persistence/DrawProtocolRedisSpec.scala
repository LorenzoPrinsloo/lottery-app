package com.lottery.persistence

import cats.effect.IO
import cats.syntax.all.*
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.RedisSuite
import scala.concurrent.duration.*

/** The draw protocol against a real Redis, including draws and submissions racing each other. */
class DrawProtocolRedisSpec extends RedisSuite {

  private def claimed(claim: DrawClaim): DrawClaim.Claimed = claim match {
    case c: DrawClaim.Claimed => c
    case other                => fail(s"expected the draw to claim the ballots, got $other")
  }

  test("a draw with no ballots claims nothing and leaves no lock behind") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      (repo.startDraw(today), repo.startDraw(today)).tupled.assertEquals((DrawClaim.NoBallots, DrawClaim.NoBallots))
    }
  }

  test("a draw claims every ballot, and while it runs the lottery takes no new ballots and no second draw") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      val ballots = List(ballot("a@x.com"), ballot("b@x.com"), ballot("c@x.com"))
      for {
        _ <- repo.submitBallots(today, ballots)
        claim <- repo.startDraw(today).map(claimed)
        open <- repo.getOpen(today)
        late <- repo.submitBallots(today, List(ballot("d@x.com")))
        second <- repo.startDraw(today)
      } yield {
        assertEquals(claim.lottery.ballots.toSet, ballots.toSet)
        assertEquals(open, None)
        assertEquals(late, None)
        assertEquals(second, DrawClaim.InProgress)
      }
    }
  }

  test("completing a draw stores the result, closes the lottery, and cleans up") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      val r = result()
      for {
        _ <- repo.submitBallots(today, List(ballot()))
        claim <- repo.startDraw(today).map(claimed)
        _ <- repo.completeDraw(r, claim.token)
        closed <- repo.getClosed(today)
        late <- repo.submitBallots(today, List(ballot()))
        again <- repo.startDraw(today)
        keys <- redis.keys("lottery:*")
      } yield {
        assertEquals(closed, Some(r))
        assertEquals(late, None)
        assertEquals(again, DrawClaim.AlreadyDrawn)
        assertEquals(keys, List(s"lottery:closed:$today"))
      }
    }
  }

  test("an abandoned draw keeps its ballots, and the retry also picks up ballots submitted since") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      val first = List(ballot("a@x.com"), ballot("b@x.com"))
      val later = ballot("c@x.com")
      for {
        _ <- repo.submitBallots(today, first)
        claim <- repo.startDraw(today).map(claimed)
        _ <- repo.abandonDraw(today, claim.token)
        _ <- repo.submitBallots(today, List(later))
        retry <- repo.startDraw(today).map(claimed)
      } yield assertEquals(retry.lottery.ballots.toSet, (later :: first).toSet)
    }
  }

  test("a draw that no longer holds the lock can't store a result") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      for {
        _ <- repo.submitBallots(today, List(ballot()))
        _ <- repo.startDraw(today).map(claimed)
        stale <- repo.completeDraw(result(), "someone-elses-token").attempt
        closed <- repo.getClosed(today)
      } yield {
        assert(stale.isLeft)
        assertEquals(closed, None)
      }
    }
  }

  test("when 20 draws start at once, exactly one claims the ballots") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      repo.submitBallots(today, List.fill(10)(ballot())) *>
        (1 to 20).toList.parTraverse(_ => repo.startDraw(today)).map { claims =>
          assertEquals(claims.count(_.isInstanceOf[DrawClaim.Claimed]), 1)
          assertEquals(claims.count(_ == DrawClaim.InProgress), 19)
        }
    }
  }

  test("no accepted ballot is lost when submissions race a draw") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      val submissions = (1 to 40).toList.map(i => ballot(s"p$i@x.com"))
      for {
        results <- submissions.parTraverse(b => repo.submitBallots(today, List(b)).map(b -> _))
          .both(IO.sleep(5.millis) *> repo.startDraw(today))
        (outcomes, claim) = results
        accepted = outcomes.collect { case (b, Some(_)) => b }
        leftover <- repo.getOpen(today)
      } yield {
        val drawn = claim match {
          case DrawClaim.Claimed(lottery, _) => lottery.ballots
          case _                             => Nil
        }
        assertEquals(drawn.toSet, accepted.toSet)
        assertEquals(leftover, None)
      }
    }
  }
}
