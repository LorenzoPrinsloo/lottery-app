package com.lottery.draw

import cats.effect.std.Random
import cats.effect.{Async, IO, IOApp, Resource}
import com.comcast.ip4s.port
import com.lottery.config.ConfigLoader
import com.lottery.draw.domain.config.AppConfig
import com.lottery.draw.routes.DrawRoutes
import com.lottery.draw.service.DrawService
import com.lottery.logging.Logging
import com.lottery.modules.Http.httpServer
import com.lottery.modules.Redis
import com.lottery.persistence.LotteryRepository
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.{Log, MkRedis}
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import org.http4s.HttpApp
import org.http4s.server.{Router, Server}

object Main extends IOApp.Simple with Logging[IO] {

  private def resources[F[_]: Async: MkRedis: Log: Random](
      config: AppConfig
  ): Resource[F, (Server, RedisCommands[F, String, String])] = {
    for {
      redisApi <- Redis.redisApi[F](config.redis)
      lotteryRepo = LotteryRepository.redis[F](redisApi)
      drawService = DrawService.default[F](lotteryRepo)
      allRoutes: HttpApp[F] = Router(
        "/api/v1" -> DrawRoutes[F](drawService).routes
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
