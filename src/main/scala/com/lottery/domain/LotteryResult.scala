package com.lottery.domain

import com.lottery.domain.Ballot.BallotId
import com.lottery.domain.Participant.ParticipantId

import java.time.{LocalDate, LocalDateTime}

case class LotteryResult(
    lotteryDate: LocalDate,
    winningBallotId: BallotId,
    participantId: ParticipantId,
    drawnAt: LocalDateTime
)
