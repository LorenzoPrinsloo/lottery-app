package com.lottery.draw.domain.config

import com.lottery.domain.config.{HttpServerConfig, RedisConfig, SmtpConfig}
import pureconfig.*

case class AppConfig(server: HttpServerConfig, redis: RedisConfig, apiSecret: String, cron: CronConfig, mailhog: SmtpConfig) derives ConfigReader
