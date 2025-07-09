package com.lottery.api.domain.request

import com.lottery.domain.Participant
import io.circe.{Codec, Decoder, Encoder}
import java.time.LocalDateTime

case class RegisterParticipantRequest(name: String, email: String)
    derives Codec {
  def asParticipant(): Participant = {
    Participant(name = name, email = email, registeredAt = LocalDateTime.now())
  }
}
