package com.lottery.domain

import cats.implicits.*
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import java.time.LocalDateTime
import java.util.UUID

case class Participant(name: String, email: String, registeredAt: LocalDateTime)
object Participant {
  given participantCodec: Codec[Participant] = deriveCodec
}
