package com.lottery.persistence

import cats.effect.kernel.Async
import com.lottery.domain.Participant
import com.lottery.domain.Participant.ParticipantId
import dev.profunktor.redis4cats.RedisCommands
import io.circe.syntax.*
import cats.implicits.*
import com.lottery.logging.Logging
import com.lottery.util.Crypto
import dev.profunktor.redis4cats.algebra.StringCommands
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.decode

trait ParticipantRepository[F[_]] extends Logging[F] {
  def insert(participant: Participant): F[Unit]
  def get(email: String): F[Option[Participant]]
}
object ParticipantRepository {
  def redis[F[_]: Async](
      redis: StringCommands[F, String, String]
  ): ParticipantRepository[F] = new ParticipantRepository[F] {
    private def key(id: String): String = s"participant:${Crypto.sha256(id)}"

    override def insert(participant: Participant): F[Unit] = redis.set(
      key(participant.email),
      participant.asJson.noSpaces
    )

    override def get(email: String): F[Option[Participant]] = {

      for {
        mbJson <- redis.get(key(email))
        _ <- logger.debug(s"Found Participant $mbJson")
        mbParticipant <- mbJson.traverse(entry =>
          decode[Participant](entry)
            .fold(error => Async[F].raiseError(error), Async[F].pure(_))
        )
      } yield mbParticipant
    }
  }
}
