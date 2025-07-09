package com.lottery.domain

import com.lottery.domain.Ballot.BallotId
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import java.time.{LocalDate, LocalDateTime}

case class LotteryResult(
    lotteryDate: LocalDate,
    winningBallotId: BallotId,
    winnerEmail: String,
    drawnAt: LocalDateTime
)
object LotteryResult {
  given participantCodec: Codec[LotteryResult] = deriveCodec
}
