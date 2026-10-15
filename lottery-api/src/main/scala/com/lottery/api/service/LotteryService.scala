package com.lottery.api.service

import cats.effect.kernel.Async
import com.lottery.domain.{Ballot, Participant, Validation}
import com.lottery.persistence.ParticipantRepository
import cats.implicits.*
import com.lottery.domain.Ballot.BallotId
import com.lottery.domain.error.ApiError.{BadRequest, Conflict}
import com.lottery.api.domain.response.{
  LotteryResultResponse,
  SubmitBallotsResponse
}
import com.lottery.domain.error.ApiError
import com.lottery.logging.Logging
import com.lottery.persistence.LotteryRepository
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

trait LotteryService[F[_]] extends Logging[F] {
  def registerParticipant(participant: Participant): F[Participant]

  def submitBallots(
      email: String,
      lotteryDate: LocalDate,
      noBallots: Int
  ): F[SubmitBallotsResponse]

  def fetchLotteryResult(date: LocalDate): F[LotteryResultResponse]

}
object LotteryService {
  def default[F[_]: Async](
      participantRepo: ParticipantRepository[F],
      lotteryRepo: LotteryRepository[F]
  ): LotteryService[F] = new LotteryService[F] {
    override def registerParticipant(
        request: Participant
    ): F[Participant] = {
      for {
        participant <- Async[F].fromEither(
          (Validation.name(request.name), Validation.email(request.email))
            .mapN((name, email) => request.copy(name = name, email = email))
        )
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
        rawEmail: String,
        lotteryDate: LocalDate,
        requestedBallots: Int
    ): F[SubmitBallotsResponse] = {
      for {
        email <- Async[F].fromEither(Validation.email(rawEmail))
        noBallots <- Async[F].fromEither(Validation.ballotCount(requestedBallots))
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
              participant.email,
              lotteryDate,
              submitTime
            )
          }

          for {
            _ <- lotteryRepo.submitBallots(lotteryDate, ballots)
            lottery <- lotteryRepo.getOpen(lotteryDate)
          } yield SubmitBallotsResponse(
            lotteryDate,
            lotteryDate.plusDays(1),
            noBallots,
            lottery
              .map(_.ballots.count(_.email == participant.email).toLong)
              .getOrElse(0L)
          )
        })
        lottery <- lotteryRepo.getOpen(lotteryDate)
      } yield response
    }

    override def fetchLotteryResult(
        date: LocalDate
    ): F[LotteryResultResponse] = {
      lotteryRepo
        .getClosed(date)
        .flatMap { mbResult =>
          mbResult.fold(ifEmpty =
            Async[F].raiseError(
              ApiError.NotFound(s"No LotteryResult found for $date")
            )
          ) { result =>
            participantRepo
              .get(result.winnerEmail)
              .map { winner =>
                LotteryResultResponse(
                  result.lotteryDate,
                  result.winningBallotId,
                  winner.map(_.name),
                  result.drawnAt
                )
              }
          }
        }
    }
  }
}
