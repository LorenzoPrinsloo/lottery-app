package com.lottery.domain

import com.lottery.domain.Ballot.BallotId
import com.lottery.domain.Participant.ParticipantId
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

case class Ballot(
    id: BallotId,
    participantId: ParticipantId,
    lotteryDate: LocalDate,
    submittedAt: LocalDateTime
)
object Ballot {
  opaque type BallotId = UUID
  object BallotId:
    given ballotIdCodec: Codec[BallotId] = Codec.from[BallotId](
      Decoder[String].map[BallotId](s => BallotId(UUID.fromString(s))),
      Encoder.encodeUUID.contramap(id => id)
    )
    def apply(uuid: UUID): BallotId = uuid

  given ballotCodec: Codec[Ballot] = deriveCodec
}
