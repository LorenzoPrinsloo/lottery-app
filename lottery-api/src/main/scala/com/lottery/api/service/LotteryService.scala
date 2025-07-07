package com.lottery.api.service

import cats.effect.kernel.Async
import com.lottery.api.domain.{Ballot, Participant}
import com.lottery.api.persistence.{LotteryRepository, ParticipantRepository}
import cats.implicits.*
import com.lottery.api.domain.Ballot.BallotId
import com.lottery.api.domain.error.ApiError.{BadRequest, Conflict}
import com.lottery.api.domain.response.SubmitBallotsResponse
import com.lottery.api.logging.Logging
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

trait LotteryService[F[_]] extends Logging[F] {
  def registerParticipant(participant: Participant): F[Participant]
  def submitBallots(
      email: String,
      lotteryDate: LocalDate,
      noBallots: Int
  ): F[SubmitBallotsResponse]
}
object LotteryService {
  def default[F[_]: Async](
      participantRepo: ParticipantRepository[F],
      lotteryRepo: LotteryRepository[F]
  ): LotteryService[F] = new LotteryService[F] {
    override def registerParticipant(
        participant: Participant
    ): F[Participant] = {
      for {
        mbParticipant <- participantRepo.get(participant.email)
        _ <- logger.debug(
          s"Checking for existing participant, found $mbParticipant"
        )
        _ <- mbParticipant.fold(ifEmpty = participantRepo.insert(participant))(
          found =>
            Async[F].raiseError(Conflict(s"Participant already exists $found"))
        )
      } yield participant
    }

    override def submitBallots(
        email: String,
        lotteryDate: LocalDate,
        noBallots: Int
    ): F[SubmitBallotsResponse] = {
      for {
        mbParticipant <- participantRepo.get(email)
        response <- mbParticipant.fold(ifEmpty =
          Async[F].raiseError[SubmitBallotsResponse](
            BadRequest(s"No Registered participant found for $email")
          )
        )(participant => {
          val submitTime = LocalDateTime.now()

          val ballots = List.tabulate(noBallots) { _ =>
            Ballot(
              BallotId(UUID.randomUUID()),
              participant.id,
              lotteryDate,
              submitTime
            )
          }

          for {
            _ <- lotteryRepo.submitBallots(lotteryDate, ballots)
            lottery <- lotteryRepo.get(lotteryDate)
          } yield SubmitBallotsResponse(
            lotteryDate,
            lotteryDate.plusDays(1),
            noBallots,
            lottery
              .map(_.ballots.count(_.participantId == participant.id).toLong)
              .getOrElse(0L)
          )
        })
        lottery <- lotteryRepo.get(lotteryDate)
      } yield response
    }
  }
}
