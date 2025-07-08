package com.lottery.draw

import cats.effect.std.Random
import cats.effect.{Async, IO, IOApp, Resource}
import com.lottery.config.ConfigLoader
import com.lottery.draw.domain.config.AppConfig
import com.lottery.draw.routes.DrawRoutes
import com.lottery.draw.service.DrawService
import com.lottery.draw.stream.DrawWorker
import com.lottery.logging.Logging
import com.lottery.modules.Http.httpServer
import com.lottery.modules.{AuthMiddleware, Redis}
import com.lottery.persistence.{LotteryRepository, ParticipantRepository}
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.{Log, MkRedis}
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import org.http4s.HttpApp
import org.http4s.server.{Router, Server}

object Main extends IOApp.Simple with Logging[IO] {

  private def routes[F[_]: Async](
      drawService: DrawService[F],
      apiSecret: String
  ): HttpApp[F] = {
    val drawRoutes = DrawRoutes[F](drawService).routes
    val authMiddleware = AuthMiddleware[F](apiSecret)
    val protectedRoutes = authMiddleware(drawRoutes)

    Router(
      "/api/v1" -> protectedRoutes
    ).orNotFound
  }

  private def resources[F[_]: Async: MkRedis: Log: Random](
      config: AppConfig
  ): Resource[
    F,
    (Server, RedisCommands[F, String, String], fs2.Stream[F, Unit])
  ] = {
    for {
      redisApi <- Redis.redisApi[F](config.redis)
      lotteryRepo = LotteryRepository.redis[F](redisApi)
      participantRepo = ParticipantRepository.redis[F](redisApi)
      drawService = DrawService.default[F](lotteryRepo, participantRepo)
      allRoutes: HttpApp[F] = routes[F](drawService, config.apiSecret)
      httpServer <- httpServer[F](config.server, allRoutes)
      cronStream <- Resource.pure(
        DrawWorker.default[F](drawService, config.cron).cronStream()
      )
    } yield (httpServer, redisApi, cronStream)
  }

  override def run: IO[Unit] = {
    (for {
      _ <- logger.info("Application starting up...")
      config <- ConfigLoader.default[IO, AppConfig].load
      _ <- logger.info(s"Loaded config $config")
      serverResource <- resources[IO](config).use {
        (httpServer, redisApi, cronStream) =>
          IO.race(
            cronStream.compile.drain,
            logger.info(s"Server started on ${httpServer.address}") *> IO.never
          )
      }
    } yield ()).handleErrorWith { error =>
      logger.error(error)(
        s"Application failed to start: ${error.getMessage}"
      ) *> IO.raiseError(error)
    }
  }
}
