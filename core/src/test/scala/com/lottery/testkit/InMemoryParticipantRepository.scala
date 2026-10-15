package com.lottery.testkit

import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import com.lottery.domain.Participant
import com.lottery.persistence.ParticipantRepository

/** A ParticipantRepository backed by a Ref, for testing services without Redis. */
class InMemoryParticipantRepository[F[_]: Sync](val state: Ref[F, Map[String, Participant]]) extends ParticipantRepository[F] {
  override def insert(participant: Participant): F[Unit] = state.update(_.updated(participant.email, participant))
  override def get(email: String): F[Option[Participant]] = state.get.map(_.get(email))
}

object InMemoryParticipantRepository {
  def make[F[_]: Sync](participants: Participant*): F[InMemoryParticipantRepository[F]] =
    Ref.of[F, Map[String, Participant]](participants.map(p => p.email -> p).toMap).map(new InMemoryParticipantRepository(_))
}
