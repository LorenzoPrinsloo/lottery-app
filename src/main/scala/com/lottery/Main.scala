package com.lottery

import cats.MonadThrow
import cats.effect.*
import cats.implicits.*
import com.lottery.config.ConfigLoader
import com.lottery.domain.config.{AppConfig, HttpServerConfig, RedisConfig}
import com.lottery.logging.Logging
import com.lottery.persistence.{LotteryRepository, ParticipantRepository}
import com.lottery.routes.LotteryRoutes
import com.lottery.service.LotteryService
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.algebra.StringCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.{Log, MkRedis}
import io.lettuce.core.{ClientOptions, TimeoutOptions}
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.{Router, Server}
import dev.profunktor.redis4cats.config.*

import scala.concurrent.duration.*
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import dev.profunktor.redis4cats.algebra.StringCommands
import dev.profunktor.redis4cats.connection.*
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.log4cats.*
import dev.profunktor.redis4cats.*

object Main extends IOApp.Simple with Logging[IO] {

  /** A resource for the Redis Connection.
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
      client <- RedisClient[F].withOptions(
        config.uri,
        opts
      )
      redis <- Redis[F].fromClient(client, RedisCodec.Utf8)
    } yield redis
  }

  /** A resource for the HTTP server.
    */
  def httpServer[F[_]: Async](
      config: HttpServerConfig,
      httpApp: HttpApp[F]
  ): Resource[F, Server] = {
    EmberServerBuilder
      .default[F]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(httpApp)
      .build
  }

  def resources[F[_]: Async: MkRedis: Log](
      config: AppConfig
  ): Resource[F, (Server, StringCommands[F, String, String])] = {
    for {
      redisApi <- redisApi[F](config.redis)
      participantRepo = ParticipantRepository.redis[F](redisApi)
      lotteryRepo = LotteryRepository.redis[F](redisApi)
      lotteryService = LotteryService.default[F](participantRepo, lotteryRepo)
      allRoutes: HttpApp[F] = Router(
        "/api/v1" -> LotteryRoutes[F](lotteryService).routes
      ).orNotFound
      httpServer <- httpServer[F](config.server, allRoutes)
    } yield (httpServer, redisApi)
  }

  override def run: IO[Unit] = {
    (for {
      _ <- logger.info("Application starting up...")
      config <- ConfigLoader.default[IO].load
      _ <- logger.info(s"Loaded config $config")
      serverResource <- resources[IO](config).use { (httpServer, redisApi) =>
        logger.info(s"Server started on ${httpServer.address}") *> IO.never
      }
    } yield ()).handleErrorWith { error =>
      logger.error(error)(
        s"Application failed to start: ${error.getMessage}"
      ) *> IO.raiseError(error)
    }
  }
}
