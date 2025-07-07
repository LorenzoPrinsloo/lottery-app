package com.lottery.api.domain.response

import io.circe.Encoder
import java.time.LocalDate

case class SubmitBallotsResponse(
    lotteryDate: LocalDate,
    drawDate: LocalDate,
    ballotsSubmitted: Long,
    totalBallotsForLottery: Long
) derives Encoder
