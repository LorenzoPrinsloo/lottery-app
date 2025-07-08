package com.lottery.draw.service

import cats.effect.kernel.Clock
import cats.effect.{Async, Temporal}
import cats.effect.std.Random
import com.lottery.persistence.{LotteryRepository, ParticipantRepository}
import cats.implicits.*
import com.lottery.domain.LotteryResult
import com.lottery.draw.domain.response.LotteryResultResponse
import com.lottery.logging.Logging

import java.time.{LocalDate, LocalDateTime, ZoneId}

trait DrawService[F[_]] extends Logging[F] {
  def performDraw(date: LocalDate): F[Option[LotteryResultResponse]]
}
object DrawService {
  def default[F[_]: Async: Random](
      lotteryRepo: LotteryRepository[F],
      participantRepo: ParticipantRepository[F]
  ): DrawService[F] = new DrawService[F] {
    val systemZone: ZoneId = ZoneId.of("Europe/Amsterdam")

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
