package com.lottery.draw.domain.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import java.time.LocalTime
import java.time.format.DateTimeParseException

case class CronConfig (timeZone: String, drawTime: LocalTime, dayOffset: Int) derives ConfigReader
object CronConfig {
  given localTimeReader: ConfigReader[LocalTime] = ConfigReader.fromString { str =>
    try {
      Right(LocalTime.parse(str))
    } catch {
      case e: DateTimeParseException =>
        Left(CannotConvert(str, "LocalTime", e.getMessage))
    }
  }
}
