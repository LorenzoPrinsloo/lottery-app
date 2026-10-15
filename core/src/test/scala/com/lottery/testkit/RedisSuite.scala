package com.lottery.testkit

import cats.effect.{IO, Resource}
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import com.lottery.domain.config.RedisConfig
import com.lottery.modules.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log.NoOp.given
import munit.CatsEffectSuite
import org.testcontainers.containers.wait.strategy.Wait
import scala.concurrent.duration.*

/** Starts a real Redis in Docker once per suite; each test gets a connection to an empty database. */
trait RedisSuite extends CatsEffectSuite with TestContainerForAll {

  override val containerDef: GenericContainer.Def[GenericContainer] =
    GenericContainer.Def("redis:7-alpine", exposedPorts = Seq(6379), waitStrategy = Wait.forListeningPort())

  def withRedis[A](test: RedisCommands[IO, String, String] => IO[A]): IO[A] =
    withContainers { redis =>
      val uri = s"redis://${redis.container.getHost}:${redis.container.getMappedPort(6379)}"
      Redis.redisApi[IO](RedisConfig(uri, 5.seconds)).use(cmd => cmd.flushAll *> test(cmd))
    }
}
