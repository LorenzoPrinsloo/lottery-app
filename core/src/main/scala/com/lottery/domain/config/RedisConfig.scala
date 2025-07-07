package com.lottery.domain.config

import pureconfig.*

import scala.concurrent.duration.FiniteDuration

case class RedisConfig(uri: String, timeout: FiniteDuration) derives ConfigReader
