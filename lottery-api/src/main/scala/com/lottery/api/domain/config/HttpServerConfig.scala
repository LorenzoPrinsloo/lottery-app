package com.lottery.api.domain.config

import com.comcast.ip4s.{Host, Port}
import pureconfig.*
import pureconfig.module.ip4s.*

case class HttpServerConfig(host: Host, port: Port) derives ConfigReader
