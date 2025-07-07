package com.lottery.persistence

import cats.effect.kernel.Async
import com.lottery.domain.{Ballot, Lottery, LotteryStatus}
import com.lottery.logging.Logging
import dev.profunktor.redis4cats.algebra.StringCommands
import io.circe.syntax.*
import cats.implicits.*
import com.lottery.domain.error.ApiError.InternalServerError
import dev.profunktor.redis4cats.RedisCommands
import io.circe.parser.decode

import java.time.LocalDate
import java.time.format.DateTimeFormatter

trait LotteryRepository[F[_]] extends Logging[F] {
  def submitBallots(lotteryDate: LocalDate, ballots: List[Ballot]): F[Long]
  def get(lotteryDate: LocalDate): F[Option[Lottery]]
}
object LotteryRepository {
  def redis[F[_]: Async](
      redis: RedisCommands[F, String, String]
  ): LotteryRepository[F] = new LotteryRepository[F] {
    private def key(lotteryDate: LocalDate): String =
      s"lottery:${lotteryDate.format(DateTimeFormatter.ISO_DATE)}"

    override def submitBallots(
        lotteryDate: LocalDate,
        ballots: List[Ballot]
    ): F[Long] = {
      val ballotJsons = ballots.map(_.asJson.noSpaces)
      if (ballotJsons.nonEmpty) {
        redis.lPush(key(lotteryDate), ballotJsons: _*)
      } else {
        Async[F].pure(0L)
      }
    }

    override def get(lotteryDate: LocalDate): F[Option[Lottery]] = {
      redis.lRange(key(lotteryDate), 0, -1).flatMap { ballotJsons =>
        if (ballotJsons.isEmpty) {
          Async[F].pure(None)
        } else {
          ballotJsons.traverse(json => decode[Ballot](json)) match {
            case Right(ballots) =>
              Async[F]
                .pure(Some(Lottery(lotteryDate, LotteryStatus.OPEN, ballots)))
            case Left(error) =>
              logger.error(error)("Failed to decode ballots") *>
                Async[F]
                  .raiseError[Option[Lottery]](
                    InternalServerError(
                      s"Failed to decode ballots: ${error.getCause}"
                    )
                  )
          }
        }
      }
    }
  }
}
