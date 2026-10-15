package com.lottery.draw.domain.config

import munit.FunSuite
import pureconfig.ConfigSource
import java.time.LocalTime

class CronConfigSpec extends FunSuite {

  private def load(drawTime: String) =
    ConfigSource.string(s"""time-zone = "Europe/Amsterdam", draw-time = "$drawTime", day-offset = 1""").load[CronConfig]

  test("reads the draw time as HH:mm") {
    assertEquals(load("00:01").map(_.drawTime), Right(LocalTime.of(0, 1)))
  }

  test("rejects a draw time that isn't a time") {
    assert(load("25:00").isLeft)
    assert(load("noon").isLeft)
  }
}
