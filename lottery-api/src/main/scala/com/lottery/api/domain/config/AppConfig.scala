package com.lottery.api.domain.config

import com.lottery.domain.config.{HttpServerConfig, RedisConfig}
import pureconfig.*

/** `timeZone` decides which day "today" is; it must match the draw service's time zone. */
case class AppConfig(server: HttpServerConfig, redis: RedisConfig, timeZone: String)
    derives ConfigReader
