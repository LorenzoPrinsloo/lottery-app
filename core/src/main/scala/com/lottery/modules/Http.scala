package com.lottery.modules

import cats.effect.{Async, Resource}
import com.lottery.domain.config.HttpServerConfig
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

object Http {

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
}
