package com.lottery.api.domain.config

import pureconfig.*

case class AppConfig(server: HttpServerConfig, redis: RedisConfig) derives ConfigReader
