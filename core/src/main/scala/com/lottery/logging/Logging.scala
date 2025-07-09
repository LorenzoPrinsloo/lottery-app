package com.lottery.logging

import cats.effect.kernel.Sync
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.Logger

trait Logging[F[_]: Sync] {
  protected given logger: Logger[F] = Slf4jFactory.create[F].getLogger
}
