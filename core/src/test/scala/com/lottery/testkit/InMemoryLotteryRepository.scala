package com.lottery.testkit

import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import com.lottery.domain.{Ballot, Lottery, LotteryResult, LotteryStatus}
import com.lottery.persistence.LotteryRepository
import java.time.LocalDate

/** A LotteryRepository backed by a Ref, mirroring the Redis implementation's behaviour. */
class InMemoryLotteryRepository[F[_]: Sync](
    val open: Ref[F, Map[LocalDate, List[Ballot]]],
    val closed: Ref[F, Map[LocalDate, LotteryResult]]
) extends LotteryRepository[F] {

  override def submitBallots(lotteryDate: LocalDate, ballots: List[Ballot]): F[Long] =
    open.modify { m =>
      val updated = ballots.reverse ++ m.getOrElse(lotteryDate, Nil)
      (if (ballots.isEmpty) m else m.updated(lotteryDate, updated), if (ballots.isEmpty) 0L else updated.size.toLong)
    }

  override def getOpen(lotteryDate: LocalDate): F[Option[Lottery]] =
    open.get.map(_.get(lotteryDate).filter(_.nonEmpty).map(Lottery(lotteryDate, LotteryStatus.OPEN, _)))

  override def getClosed(lotteryDate: LocalDate): F[Option[LotteryResult]] =
    closed.get.map(_.get(lotteryDate))

  override def closeLottery(result: LotteryResult): F[Unit] =
    open.update(_ - result.lotteryDate) *> closed.update(_.updated(result.lotteryDate, result))
}

object InMemoryLotteryRepository {
  def make[F[_]: Sync]: F[InMemoryLotteryRepository[F]] =
    (Ref.of[F, Map[LocalDate, List[Ballot]]](Map.empty), Ref.of[F, Map[LocalDate, LotteryResult]](Map.empty))
      .mapN(new InMemoryLotteryRepository(_, _))
}
