package com.lottery.draw.service

import cats.effect.kernel.Clock
import cats.effect.{Async, Temporal}
import cats.effect.std.Random
import com.lottery.persistence.LotteryRepository
import cats.implicits.*
import com.lottery.domain.LotteryResult
import com.lottery.logging.Logging

import java.time.{LocalDate, LocalDateTime}

trait DrawService[F[_]] extends Logging[F] {
  def performDraw(date: LocalDate): F[Option[LotteryResult]]
}
object DrawService {
  def default[F[_]: Async: Random](
      lotteryRepo: LotteryRepository[F]
  ): DrawService[F] = new DrawService[F] {
    override def performDraw(date: LocalDate): F[Option[LotteryResult]] = {
      for {
        drawTime <- Temporal[F].realTimeInstant
        mbLottery <- lotteryRepo.get(date)
        result <- mbLottery
          .fold(ifEmpty =
          logger
            .warn(
              s"No lottery or no ballots found for $date. No draw will be performed."
            )
            .as(Option.empty[LotteryResult])
        ) { lottery =>
          for {
            _ <- logger.info(
              s"Performing draw for ${lottery.ballots.size} ballots on $date."
            )
            winningBallot <- Random[F].elementOf(lottery.ballots)
            _ <- logger.info(
              s"🎉 The winner for $date is participant ${winningBallot.participantId}! 🎉"
            )
            lotteryResult = LotteryResult(
              date,
              winningBallot.id,
              winningBallot.participantId,
              LocalDateTime.from(drawTime)
            )
            _ <- lotteryRepo.closeLottery(lotteryResult)
          } yield lotteryResult.some
        }
      } yield result
    }
  }
}
