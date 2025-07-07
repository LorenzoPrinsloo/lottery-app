package com.lottery.api

import cats.effect.*
import cats.implicits.*
import com.lottery.config.ConfigLoader
import com.lottery.api.domain.config.AppConfig
import com.lottery.logging.Logging
import com.lottery.persistence.ParticipantRepository
import com.lottery.api.routes.LotteryRoutes
import com.lottery.api.service.LotteryService
import com.lottery.modules.Redis
import com.lottery.persistence.LotteryRepository
import dev.profunktor.redis4cats.effect.{Log, MkRedis}
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.server.{Router, Server}
import com.lottery.modules.Http.*
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.log4cats.log4CatsInstance

object Main extends IOApp.Simple with Logging[IO] {

  def resources[F[_]: Async: MkRedis: Log](
      config: AppConfig
  ): Resource[F, (Server, RedisCommands[F, String, String])] = {
    for {
      redisApi <- Redis.redisApi[F](config.redis)
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
      config <- ConfigLoader.default[IO, AppConfig].load
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
