package com.lottery.domain.config

import pureconfig.ConfigReader

case class SmtpConfig(host: String, port: Int) derives ConfigReader
