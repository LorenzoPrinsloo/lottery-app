package com.lottery.api.domain

import java.time.LocalDate

case class Lottery(
    date: LocalDate,
    status: LotteryStatus,
    ballots: List[Ballot]
)
