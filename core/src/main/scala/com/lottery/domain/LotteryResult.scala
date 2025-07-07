package com.lottery.domain

import com.lottery.domain.Ballot.BallotId
import com.lottery.domain.Participant.ParticipantId
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.{LocalDate, LocalDateTime}

case class LotteryResult(
  lotteryDate: LocalDate,
  winningBallotId: BallotId,
  winner: ParticipantId,
  drawnAt: LocalDateTime
)
object LotteryResult {
  given participantCodec: Codec[LotteryResult] = deriveCodec
}