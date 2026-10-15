package com.lottery.testkit

import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import com.lottery.domain.error.ApiError.InternalServerError
import com.lottery.domain.{Ballot, Lottery, LotteryResult, LotteryStatus}
import com.lottery.persistence.{DrawClaim, LotteryRepository}
import java.time.LocalDate
import java.util.UUID

/** A LotteryRepository backed by a Ref, following the same draw protocol as the Redis implementation. */
class InMemoryLotteryRepository[F[_]: Sync](val state: Ref[F, InMemoryLotteryRepository.State]) extends LotteryRepository[F] {
  import InMemoryLotteryRepository.State

  override def submitBallots(lotteryDate: LocalDate, ballots: List[Ballot]): F[Option[Long]] =
    state.modify { s =>
      if (ballots.isEmpty) (s, Some(0L))
      else if (s.closed.contains(lotteryDate) || s.locks.contains(lotteryDate)) (s, None)
      else {
        val updated = ballots.reverse ++ s.open.getOrElse(lotteryDate, Nil)
        (s.copy(open = s.open.updated(lotteryDate, updated)), Some(updated.size.toLong))
      }
    }

  override def getOpen(lotteryDate: LocalDate): F[Option[Lottery]] =
    state.get.map(_.open.get(lotteryDate).filter(_.nonEmpty).map(Lottery(lotteryDate, LotteryStatus.OPEN, _)))

  override def getClosed(lotteryDate: LocalDate): F[Option[LotteryResult]] =
    state.get.map(_.closed.get(lotteryDate))

  override def startDraw(lotteryDate: LocalDate): F[DrawClaim] =
    Sync[F].delay(UUID.randomUUID().toString).flatMap { token =>
      state.modify { s =>
        if (s.closed.contains(lotteryDate)) (s, DrawClaim.AlreadyDrawn)
        else if (s.locks.contains(lotteryDate)) (s, DrawClaim.InProgress)
        else {
          val ballots = s.drawing.getOrElse(lotteryDate, Nil) ++ s.open.getOrElse(lotteryDate, Nil)
          if (ballots.isEmpty) (s, DrawClaim.NoBallots)
          else
            (
              s.copy(open = s.open - lotteryDate, drawing = s.drawing.updated(lotteryDate, ballots), locks = s.locks.updated(lotteryDate, token)),
              DrawClaim.Claimed(Lottery(lotteryDate, LotteryStatus.OPEN, ballots), token)
            )
        }
      }
    }

  override def completeDraw(result: LotteryResult, token: String): F[Unit] =
    state.modify { s =>
      val date = result.lotteryDate
      if (!s.locks.get(date).contains(token)) (s, false)
      else (s.copy(drawing = s.drawing - date, locks = s.locks - date, closed = s.closed.updated(date, result)), true)
    }.flatMap(stored => Sync[F].raiseUnless(stored)(InternalServerError(s"The draw for ${result.lotteryDate} lost its lock")))

  override def abandonDraw(lotteryDate: LocalDate, token: String): F[Unit] =
    state.update(s => if (s.locks.get(lotteryDate).contains(token)) s.copy(locks = s.locks - lotteryDate) else s)

  /** Test setup: records a result as if a draw had already happened. */
  def recordResult(result: LotteryResult): F[Unit] =
    state.update(s => s.copy(open = s.open - result.lotteryDate, closed = s.closed.updated(result.lotteryDate, result)))
}

object InMemoryLotteryRepository {
  case class State(
      open: Map[LocalDate, List[Ballot]] = Map.empty,
      drawing: Map[LocalDate, List[Ballot]] = Map.empty,
      locks: Map[LocalDate, String] = Map.empty,
      closed: Map[LocalDate, LotteryResult] = Map.empty
  )

  def make[F[_]: Sync]: F[InMemoryLotteryRepository[F]] =
    Ref.of[F, State](State()).map(new InMemoryLotteryRepository(_))
}
