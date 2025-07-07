package com.lottery.api.config

import cats.effect.kernel.Sync
import com.lottery.api.domain.config.AppConfig
import pureconfig.*
import pureconfig.module.catseffect.syntax.*

trait ConfigLoader[F[_]: Sync] {
  def load: F[AppConfig]
}
object ConfigLoader {

  def default[F[_]: Sync]: ConfigLoader[F] = new ConfigLoader[F] {
    override def load: F[AppConfig] =
      ConfigSource.default.loadF[F, AppConfig]()
  }
}
