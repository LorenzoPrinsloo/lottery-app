package com.lottery.draw.domain.config

import com.lottery.domain.config.{HttpServerConfig, RedisConfig}
import pureconfig.*

case class AppConfig (server: HttpServerConfig, redis: RedisConfig) derives ConfigReader
