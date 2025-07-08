package com.lottery.persistence

import cats.effect.kernel.Async
import com.lottery.domain.{Ballot, Lottery, LotteryResult, LotteryStatus}
import com.lottery.logging.Logging
import io.circe.syntax.*
import cats.implicits.*
import com.lottery.domain.LotteryStatus.{CLOSED, OPEN}
import com.lottery.domain.error.ApiError.InternalServerError
import dev.profunktor.redis4cats.RedisCommands
import io.circe.parser.decode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

trait LotteryRepository[F[_]] extends Logging[F] {
  def submitBallots(lotteryDate: LocalDate, ballots: List[Ballot]): F[Long]
  def getOpen(lotteryDate: LocalDate): F[Option[Lottery]]
  def getClosed(lotteryDate: LocalDate): F[Option[LotteryResult]]
  def closeLottery(result: LotteryResult): F[Unit]
}
object LotteryRepository {
  def redis[F[_]: Async](
      redis: RedisCommands[F, String, String]
  ): LotteryRepository[F] = new LotteryRepository[F] {
    private def key(status: LotteryStatus, lotteryDate: LocalDate): String =
      s"lottery:${status.toString.toLowerCase()}:${lotteryDate.format(DateTimeFormatter.ISO_DATE)}"

    override def submitBallots(
        lotteryDate: LocalDate,
        ballots: List[Ballot]
    ): F[Long] = {
      val ballotJsons = ballots.map(_.asJson.noSpaces)
      if (ballotJsons.nonEmpty) {
        redis.lPush(key(OPEN, lotteryDate), ballotJsons: _*)
      } else {
        Async[F].pure(0L)
      }
    }

    override def getOpen(lotteryDate: LocalDate): F[Option[Lottery]] = {
      redis.lRange(key(OPEN, lotteryDate), 0, -1).flatMap { ballotJsons =>
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

    override def getClosed(lotteryDate: LocalDate): F[Option[LotteryResult]] = {
      redis
        .get(key(CLOSED, lotteryDate))
        .flatMap { mbJson =>
          mbJson
            .traverse(json =>
              decode[LotteryResult](json) match {
                case Left(error) =>
                  logger.error(error)("Failed to decode LotteryResult") *>
                    Async[F]
                      .raiseError[LotteryResult](
                        InternalServerError(
                          s"Failed to decode LotteryResult: ${error.getCause}"
                        )
                      )
                case Right(value) => Async[F].pure(value)
              }
            )
        }
    }

    override def closeLottery(
        result: LotteryResult
    ): F[Unit] = {
      for {
        _ <- logger.debug(s"Closing lottery ${result.lotteryDate}")
        _ <- redis.del(key(OPEN, result.lotteryDate))
        _ <- logger.debug(s"Storing lottery result for ${result.lotteryDate}")
        _ <- redis.set(key(CLOSED, result.lotteryDate), result.asJson.noSpaces)
      } yield ()
    }
  }
}
