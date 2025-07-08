package com.lottery.api.domain.response

import com.lottery.domain.Ballot.BallotId
import com.lottery.domain.Participant
import io.circe.Codec

import java.time.{LocalDate, LocalDateTime}

case class LotteryResultResponse(
    lotteryDate: LocalDate,
    winningBallot: BallotId,
    winnerName: Option[String],
    drawnAt: LocalDateTime
) derives Codec
