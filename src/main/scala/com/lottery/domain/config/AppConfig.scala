package com.lottery.domain.config

import pureconfig.*

case class AppConfig(server: HttpServerConfig, redis: RedisConfig) derives ConfigReader
