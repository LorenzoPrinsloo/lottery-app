package com.lottery.draw.service

import cats.effect.{Async, Temporal}
import cats.effect.std.Random
import com.lottery.persistence.{LotteryRepository, ParticipantRepository}
import cats.implicits.*
import com.lottery.domain.{LotteryResult, Participant}
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
    ): F[Option[LotteryResultResponse]] = for {
      drawTime <- Temporal[F].realTimeInstant
      mbLottery <- lotteryRepo.getOpen(date)
      result <- mbLottery
        .fold(ifEmpty =
          logger
            .warn(
              s"No lottery or no ballots found for $date. No draw will be performed."
            )
            .as(Option.empty[LotteryResultResponse])
        ) { lottery =>
          for {
            _ <- logger.info(
              s"Performing draw for ${lottery.ballots.size} ballots on $date."
            )
            winningBallot <- Random[F].elementOf(lottery.ballots)
            _ <- logger.info(
              s"🎉 The winner for $date is participant ${winningBallot.email}! 🎉"
            )
            lotteryResult = LotteryResult(
              date,
              winningBallot.id,
              winningBallot.email,
              drawTime.atZone(systemZone).toLocalDateTime()
            )
            _ <- lotteryRepo.closeLottery(lotteryResult)
            winner <- participantRepo.get(winningBallot.email)
            _ <- sendNotification(winner, date)
          } yield LotteryResultResponse(
            lotteryResult.lotteryDate,
            lotteryResult.winningBallotId,
            winner,
            lotteryResult.drawnAt
          ).some
        }
    } yield result
  }
}
