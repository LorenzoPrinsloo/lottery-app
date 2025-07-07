package com.lottery.config

import cats.effect.kernel.Sync
import pureconfig.*
import pureconfig.module.catseffect.syntax.*

import scala.reflect.ClassTag

trait ConfigLoader[F[_]: Sync, A: ConfigReader: ClassTag] {
  def load: F[A]
}
object ConfigLoader {
  def default[F[_]: Sync, A: ConfigReader: ClassTag]: ConfigLoader[F, A] = new ConfigLoader[F, A] {
    override def load: F[A] =
      ConfigSource.default.loadF[F, A]()
  }
}
