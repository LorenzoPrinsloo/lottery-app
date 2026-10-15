package com.lottery.api.service

import cats.effect.IO
import cats.syntax.all.*
import com.lottery.domain.error.ApiError
import com.lottery.persistence.{LotteryRepository, ParticipantRepository}
import com.lottery.testkit.Fixtures.*
import com.lottery.testkit.RedisSuite

/** Runs the service against a real Redis, where requests really do race each other. */
class LotteryServiceRedisSpec extends RedisSuite {

  test("when many people register the same email at once, exactly one succeeds") {
    withRedis { redis =>
      val svc = LotteryService.default[IO](ParticipantRepository.redis[IO](redis), LotteryRepository.redis[IO](redis))
      (1 to 25).toList
        .parTraverse(i => svc.registerParticipant(participant(s"Person $i", "shared@example.com")).attempt)
        .map { results =>
          assertEquals(results.count(_.isRight), 1)
          assert(results.collect { case Left(e) => e }.forall(_.isInstanceOf[ApiError.Conflict]))
        }
    }
  }
}
