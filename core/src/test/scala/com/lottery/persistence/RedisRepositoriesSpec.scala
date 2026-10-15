package com.lottery.persistence

import cats.effect.IO
import cats.syntax.all.*
import com.lottery.domain.LotteryStatus
import com.lottery.domain.error.ApiError
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.RedisSuite

/** Runs the Redis repositories against a real Redis in Docker. */
class RedisRepositoriesSpec extends RedisSuite {

  test("submitted ballots can be read back as the open lottery") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      val ballots = List(ballot("a@x.com"), ballot("b@x.com"), ballot("a@x.com"))
      for {
        total <- repo.submitBallots(today, ballots)
        open <- repo.getOpen(today)
      } yield {
        assertEquals(total, Some(3L))
        assertEquals(open.map(_.status), Some(LotteryStatus.OPEN))
        assertEquals(open.map(_.ballots.toSet), Some(ballots.toSet))
      }
    }
  }

  test("submitting no ballots stores nothing") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      (repo.submitBallots(today, Nil), repo.getOpen(today)).tupled.assertEquals((Some(0L), None))
    }
  }

  test("lotteries for different dates are kept apart") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      for {
        _ <- repo.submitBallots(today, List(ballot()))
        other <- repo.getOpen(today.plusDays(1))
      } yield assertEquals(other, None)
    }
  }

  test("a corrupt result in Redis is reported as an internal error") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      redis.set(s"lottery:closed:$today", "{not json") *>
        repo.getClosed(today).attempt.map(r => assert(r.left.exists(_.isInstanceOf[ApiError.InternalServerError]), r))
    }
  }

  test("a corrupt ballot in Redis is reported as an internal error") {
    withRedis { redis =>
      val repo = LotteryRepository.redis[IO](redis)
      val corrupt = """{"id":"nope","email":"a@b.com","lotteryDate":"2030-06-15","submittedAt":"2030-06-15T12:00"}"""
      redis.lPush(s"lottery:open:$today", corrupt) *>
        repo.getOpen(today).attempt.map(r => assert(r.left.exists(_.isInstanceOf[ApiError.InternalServerError]), r))
    }
  }

  test("participants are stored and found by email") {
    withRedis { redis =>
      val repo = ParticipantRepository.redis[IO](redis)
      val p = participant()
      for {
        inserted <- repo.insert(p)
        found <- repo.get(p.email)
        missing <- repo.get("nobody@example.com")
      } yield {
        assert(inserted)
        assertEquals(found, Some(p))
        assertEquals(missing, None)
      }
    }
  }

  test("participant keys don't contain the email address") {
    withRedis { redis =>
      for {
        _ <- ParticipantRepository.redis[IO](redis).insert(participant())
        keys <- redis.keys("*")
      } yield assert(keys.nonEmpty && keys.forall(k => !k.contains("ada")), keys.toString)
    }
  }

  test("inserting a participant whose email is taken keeps the original") {
    withRedis { redis =>
      val repo = ParticipantRepository.redis[IO](redis)
      for {
        first <- repo.insert(participant("Ada"))
        second <- repo.insert(participant("Impostor"))
        stored <- repo.get("ada@example.com")
      } yield {
        assertEquals((first, second), (true, false))
        assertEquals(stored.map(_.name), Some("Ada"))
      }
    }
  }
}
