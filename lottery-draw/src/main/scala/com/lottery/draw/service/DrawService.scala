package com.lottery.draw.service

import cats.effect.{Async, Temporal}
import cats.effect.std.Random
import com.lottery.persistence.{LotteryRepository, ParticipantRepository}
import cats.implicits.*
import com.lottery.domain.{Lottery, LotteryResult, Participant}
import com.lottery.domain.error.ApiError.Conflict
import com.lottery.persistence.DrawClaim
import com.lottery.draw.client.EmailClient
import com.lottery.draw.domain.response.LotteryResultResponse
import com.lottery.logging.Logging
import org.simplejavamail.email.EmailBuilder
import java.time.{LocalDate, ZoneId}

trait DrawService[F[_]] extends Logging[F] {
  def performDraw(date: LocalDate): F[Option[LotteryResultResponse]]
}
object DrawService {
  def default[F[_]: Async: Random](
      lotteryRepo: LotteryRepository[F],
      participantRepo: ParticipantRepository[F],
      emailClient: EmailClient[F],
      systemZone: ZoneId
  ): DrawService[F] = new DrawService[F] {

    private def sendNotification(
        winner: Option[Participant],
        lotteryDate: LocalDate
    ): F[Unit] = {
      winner.fold(ifEmpty =
        logger.warn("Couldn't find participant to send mail to")
      ) { participant =>
        logger.info(s"Sending Winner Notification") *>
          emailClient.sendMail(
            EmailBuilder
              .startingBlank()
              .from("The Lottery", "noreply@lotteryservice.com")
              .to(participant.name, participant.email)
              .withSubject(
                s"Congratulations! You've won the lottery for $lotteryDate!"
              )
              .withPlainText(
                s"Dear ${participant.name},\n\nCongratulations! You are the winner of the lottery held on $lotteryDate.\n\nBest regards,\nThe Lottery Team"
              )
              .buildEmail()
          )
      }
    }

    override def performDraw(
        date: LocalDate
    ): F[Option[LotteryResultResponse]] =
      lotteryRepo.startDraw(date).flatMap {
        case DrawClaim.AlreadyDrawn =>
          logger.warn(s"The lottery for $date has already been drawn.").as(None)
        case DrawClaim.NoBallots =>
          logger.warn(s"No ballots found for $date. No draw will be performed.").as(None)
        case DrawClaim.InProgress =>
          Async[F].raiseError(Conflict(s"A draw for $date is already in progress"))
        case DrawClaim.Claimed(lottery, token) =>
          drawWinner(lottery, token)
            .onError(_ => lotteryRepo.abandonDraw(date, token))
            .flatMap { (result, winner) =>
              sendNotification(winner, date)
                .handleErrorWith(error => logger.error(error)(s"Couldn't send the winner notification for $date"))
                .as(LotteryResultResponse(result.lotteryDate, result.winningBallotId, winner, result.drawnAt).some)
            }
      }

    /** Picks a winning ballot from a claimed lottery and stores the result. */
    private def drawWinner(lottery: Lottery, token: String): F[(LotteryResult, Option[Participant])] =
      for {
        drawTime <- Temporal[F].realTimeInstant
        _ <- logger.info(s"Performing draw for ${lottery.ballots.size} ballots on ${lottery.date}.")
        winningBallot <- Random[F].elementOf(lottery.ballots)
        _ <- logger.info(s"🎉 The winner for ${lottery.date} is participant ${winningBallot.email}! 🎉")
        result = LotteryResult(
          lottery.date,
          winningBallot.id,
          winningBallot.email,
          drawTime.atZone(systemZone).toLocalDateTime()
        )
        _ <- lotteryRepo.completeDraw(result, token)
        winner <- participantRepo.get(winningBallot.email)
      } yield (result, winner)
  }
}
