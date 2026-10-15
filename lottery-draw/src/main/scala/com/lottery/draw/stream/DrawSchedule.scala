package com.lottery.draw.stream

import java.time.{LocalDate, LocalTime, ZonedDateTime}

object DrawSchedule {

  /**
   * The next draw strictly after `now`: today at `drawTime` if that's still ahead, otherwise tomorrow. Computed in
   * `now`'s zone, so the draw stays at the same local time across daylight-saving changes. If `drawTime` falls in
   * the hour skipped when clocks go forward, the draw moves to the same time after the gap.
   */
  def nextRun(now: ZonedDateTime, drawTime: LocalTime): ZonedDateTime = {
    val todays = now.toLocalDate.atTime(drawTime).atZone(now.getZone)
    if (todays.isAfter(now)) todays
    else now.toLocalDate.plusDays(1).atTime(drawTime).atZone(now.getZone)
  }

  /** The lottery a run draws: `dayOffset` days before the run's date (1 draws yesterday's lottery). */
  def lotteryFor(run: ZonedDateTime, dayOffset: Int): LocalDate =
    run.toLocalDate.minusDays(dayOffset.toLong)
}
