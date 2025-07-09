package com.lottery.draw.domain.response

import com.lottery.domain.Ballot.BallotId
import com.lottery.domain.Participant
import io.circe.Codec
import java.time.{LocalDate, LocalDateTime}

case class LotteryResultResponse(
    lotteryDate: LocalDate,
    winningBallotId: BallotId,
    winner: Option[Participant],
    drawnAt: LocalDateTime
) derives Codec
