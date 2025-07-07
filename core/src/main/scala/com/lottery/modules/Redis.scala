package com.lottery.modules

import cats.effect.*
import com.lottery.domain.config.RedisConfig
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.{Log, MkRedis}
import dev.profunktor.redis4cats.{RedisCommands, Redis as Redis4Cats}
import io.lettuce.core.{ClientOptions, TimeoutOptions}
import org.typelevel.log4cats.Logger

object Redis {

  /** A shared, reusable Resource for creating a Redis connection.
    * Both the lottery-api and lottery-draw services can use this method.
    *
    * @param config The Redis configuration (URI, timeout).
    * @return A Resource that safely acquires and releases the RedisCommands client.
    */
  def redisApi[F[_]: Async: MkRedis: Log](
      config: RedisConfig
  ): Resource[F, RedisCommands[F, String, String]] = {
    val mkOpts: F[ClientOptions] =
      Async[F].pure {
        ClientOptions
          .builder()
          .autoReconnect(true)
          .pingBeforeActivateConnection(false)
          .timeoutOptions(
            TimeoutOptions
              .builder()
              .fixedTimeout(
                java.time.Duration.ofMillis(config.timeout.toMillis)
              )
              .build()
          )
          .build()
      }

    for {
      opts <- Resource.eval(mkOpts)
      client <- RedisClient[F].withOptions(config.uri, opts)
      redis <- Redis4Cats[F].fromClient(client, RedisCodec.Utf8)
    } yield redis
  }
}
