package com.lottery.domain

import cats.implicits.*
import com.lottery.domain.Participant.ParticipantId
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

import java.time.LocalDateTime
import java.util.UUID

case class Participant(id: ParticipantId, name: String, email: String, registeredAt: LocalDateTime)
object Participant {
  opaque type ParticipantId = UUID
  object ParticipantId:
    given participantIdCodec: Codec[ParticipantId] = Codec.from[ParticipantId](
      Decoder[String].map[ParticipantId](s => ParticipantId(UUID.fromString(s))),
      Encoder.encodeUUID.contramap(id => id)
    )
    def apply(uuid: UUID): ParticipantId = uuid

  given participantCodec: Codec[Participant] = deriveCodec
}
