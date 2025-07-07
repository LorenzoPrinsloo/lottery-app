package com.lottery.domain.request

import com.lottery.domain.Participant
import com.lottery.domain.Participant.ParticipantId
import io.circe.{Codec, Decoder, Encoder}

import java.time.LocalDateTime
import java.util.UUID

case class RegisterParticipantRequest(name: String, email: String) derives Codec {
  def asParticipant(): Participant = {
    Participant(id = ParticipantId(UUID.randomUUID()), name = name, email = email, registeredAt = LocalDateTime.now())
  }
}
