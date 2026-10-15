package com.lottery.draw.stream

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import java.time.*

class DrawScheduleSpec extends ScalaCheckSuite {

  private val amsterdam = ZoneId.of("Europe/Amsterdam")
  private def at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(amsterdam)
  private val oneMinutePastMidnight = LocalTime.of(0, 1)

  test("before the draw time, the next draw is today") {
    assertEquals(DrawSchedule.nextRun(at("2030-06-15T00:00"), oneMinutePastMidnight), at("2030-06-15T00:01"))
  }

  test("after the draw time, the next draw is tomorrow") {
    assertEquals(DrawSchedule.nextRun(at("2030-06-15T09:00"), oneMinutePastMidnight), at("2030-06-16T00:01"))
  }

  test("exactly at the draw time, the next draw is tomorrow, so a draw never repeats") {
    assertEquals(DrawSchedule.nextRun(at("2030-06-15T00:01"), oneMinutePastMidnight), at("2030-06-16T00:01"))
  }

  test("the draw stays at the same local time when the clocks go forward") {
    val beforeChange = DrawSchedule.nextRun(at("2030-03-30T12:00"), oneMinutePastMidnight)
    val afterChange = DrawSchedule.nextRun(beforeChange, oneMinutePastMidnight)
    assertEquals(afterChange.toLocalDateTime, LocalDateTime.parse("2030-04-01T00:01"))
    assertEquals(Duration.between(beforeChange, afterChange), Duration.ofHours(23))
  }

  test("a draw time in the skipped hour moves to just after the gap") {
    assertEquals(DrawSchedule.nextRun(at("2030-03-31T00:00"), LocalTime.of(2, 30)).toLocalDateTime, LocalDateTime.parse("2030-03-31T03:30"))
  }

  test("day-offset picks which lottery a run draws") {
    val run = at("2030-06-16T00:01")
    assertEquals(DrawSchedule.lotteryFor(run, 1), LocalDate.of(2030, 6, 15))
    assertEquals(DrawSchedule.lotteryFor(run, 0), LocalDate.of(2030, 6, 16))
  }

  property("the next draw is always in the future, within a day and an hour, at the draw time") {
    val instants = Gen.choose(0L, 3000000000L).map(s => Instant.ofEpochSecond(1900000000L + s))
    val times = Gen.choose(0, 1439).map(m => LocalTime.of(m / 60, m % 60))
    forAll(instants, times) { (instant, time) =>
      val now = instant.atZone(amsterdam)
      val next = DrawSchedule.nextRun(now, time)
      val inGap = !amsterdam.getRules.isValidOffset(next.toLocalDate.atTime(time), next.getOffset) &&
        amsterdam.getRules.getValidOffsets(next.toLocalDate.atTime(time)).isEmpty
      next.isAfter(now) &&
        Duration.between(now, next).compareTo(Duration.ofHours(25)) <= 0 &&
        (inGap || next.toLocalTime == time)
    }
  }
}
