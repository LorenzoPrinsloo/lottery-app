package com.lottery.api.domain

import com.lottery.api.domain.Ballot.BallotId
import com.lottery.api.domain.Participant.ParticipantId

import java.time.{LocalDate, LocalDateTime}

case class LotteryResult(
    lotteryDate: LocalDate,
    winningBallotId: BallotId,
    participantId: ParticipantId,
    drawnAt: LocalDateTime
)
