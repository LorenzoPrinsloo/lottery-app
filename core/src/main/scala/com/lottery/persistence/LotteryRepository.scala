package com.lottery.persistence

import cats.effect.kernel.Async
import cats.effect.std.UUIDGen
import cats.implicits.*
import com.lottery.domain.LotteryStatus.{CLOSED, OPEN}
import com.lottery.domain.error.ApiError.InternalServerError
import com.lottery.domain.{Ballot, Lottery, LotteryResult, LotteryStatus}
import com.lottery.logging.Logging
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.ScriptOutputType
import io.circe.parser.decode
import io.circe.syntax.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.*

/** The outcome of trying to start a draw. */
enum DrawClaim {

  /** This draw holds the lottery's ballots, and the lock identified by `token`, until it completes or abandons. */
  case Claimed(lottery: Lottery, token: String)
  case NoBallots
  case AlreadyDrawn
  case InProgress
}

trait LotteryRepository[F[_]] extends Logging[F] {

  /**
   * Adds ballots to a lottery and returns how many it now holds, or None if the lottery is being drawn or has been
   * drawn: those ballots would never take part, so they're refused rather than silently lost. Atomic.
   */
  def submitBallots(lotteryDate: LocalDate, ballots: List[Ballot]): F[Option[Long]]
  def getOpen(lotteryDate: LocalDate): F[Option[Lottery]]
  def getClosed(lotteryDate: LocalDate): F[Option[LotteryResult]]

  /** Atomically takes the lottery's ballots for one draw; a second draw for the same date gets InProgress. */
  def startDraw(lotteryDate: LocalDate): F[DrawClaim]

  /** Stores the result and ends the draw that holds `token`; fails if that draw no longer holds the lock. */
  def completeDraw(result: LotteryResult, token: String): F[Unit]

  /** Ends the draw that holds `token` without a result; its ballots are kept for the next attempt. */
  def abandonDraw(lotteryDate: LocalDate, token: String): F[Unit]
}
object LotteryRepository {

  /** How long a draw may hold its lock before another draw may take over (for example after a crash). */
  val DrawLockTtl: FiniteDuration = 5.minutes

  private val submitScript =
    """if redis.call('EXISTS', KEYS[2]) == 1 or redis.call('EXISTS', KEYS[3]) == 1 then return -1 end
      |return redis.call('LPUSH', KEYS[1], unpack(ARGV))""".stripMargin

  private val startDrawScript =
    """if redis.call('EXISTS', KEYS[4]) == 1 then return {'drawn'} end
      |if not redis.call('SET', KEYS[3], ARGV[1], 'NX', 'EX', ARGV[2]) then return {'busy'} end
      |if redis.call('EXISTS', KEYS[1]) == 1 then
      |  if redis.call('EXISTS', KEYS[2]) == 1 then
      |    local pending = redis.call('LRANGE', KEYS[1], 0, -1)
      |    for i = 1, #pending do redis.call('RPUSH', KEYS[2], pending[i]) end
      |    redis.call('DEL', KEYS[1])
      |  else
      |    redis.call('RENAME', KEYS[1], KEYS[2])
      |  end
      |end
      |local ballots = redis.call('LRANGE', KEYS[2], 0, -1)
      |if #ballots == 0 then redis.call('DEL', KEYS[3]) end
      |table.insert(ballots, 1, 'claimed')
      |return ballots""".stripMargin

  private val completeDrawScript =
    """if redis.call('GET', KEYS[3]) ~= ARGV[2] then return 0 end
      |redis.call('SET', KEYS[1], ARGV[1])
      |redis.call('DEL', KEYS[2], KEYS[3])
      |return 1""".stripMargin

  private val abandonDrawScript =
    """if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]) end
      |return 1""".stripMargin

  def redis[F[_]: Async: UUIDGen](
      redis: RedisCommands[F, String, String]
  ): LotteryRepository[F] = new LotteryRepository[F] {
    private def date(lotteryDate: LocalDate): String = lotteryDate.format(DateTimeFormatter.ISO_DATE)
    private def key(status: LotteryStatus, lotteryDate: LocalDate): String =
      s"lottery:${status.toString.toLowerCase()}:${date(lotteryDate)}"
    private def drawingKey(lotteryDate: LocalDate): String = s"lottery:drawing:${date(lotteryDate)}"
    private def lockKey(lotteryDate: LocalDate): String = s"lottery:lock:${date(lotteryDate)}"

    private def decodeBallots(json: List[String]): F[List[Ballot]] =
      json.traverse(decode[Ballot]) match {
        case Right(ballots) => Async[F].pure(ballots)
        case Left(error) =>
          logger.error(error)("Failed to decode ballots") *>
            Async[F].raiseError(InternalServerError(s"Failed to decode ballots: ${error.getMessage}"))
      }

    override def submitBallots(
        lotteryDate: LocalDate,
        ballots: List[Ballot]
    ): F[Option[Long]] =
      if (ballots.isEmpty) Async[F].pure(Some(0L))
      else
        redis
          .eval(
            submitScript,
            ScriptOutputType.Integer[String],
            List(key(OPEN, lotteryDate), key(CLOSED, lotteryDate), lockKey(lotteryDate)),
            ballots.map(_.asJson.noSpaces)
          )
          .map(count => Option.when(count >= 0)(count))

    override def getOpen(lotteryDate: LocalDate): F[Option[Lottery]] =
      redis.lRange(key(OPEN, lotteryDate), 0, -1).flatMap { ballotJsons =>
        if (ballotJsons.isEmpty) Async[F].pure(None)
        else decodeBallots(ballotJsons).map(ballots => Some(Lottery(lotteryDate, LotteryStatus.OPEN, ballots)))
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

    override def startDraw(lotteryDate: LocalDate): F[DrawClaim] =
      for {
        token <- UUIDGen[F].randomUUID.map(_.toString)
        reply <- redis.eval(
          startDrawScript,
          ScriptOutputType.Multi[String],
          List(key(OPEN, lotteryDate), drawingKey(lotteryDate), lockKey(lotteryDate), key(CLOSED, lotteryDate)),
          List(token, DrawLockTtl.toSeconds.toString)
        )
        claim <- reply match {
          case List("drawn")                   => Async[F].pure(DrawClaim.AlreadyDrawn)
          case List("busy")                    => Async[F].pure(DrawClaim.InProgress)
          case List("claimed")                 => Async[F].pure(DrawClaim.NoBallots)
          case "claimed" :: ballotJsons        =>
            decodeBallots(ballotJsons).map(b => DrawClaim.Claimed(Lottery(lotteryDate, LotteryStatus.OPEN, b), token))
          case other                           =>
            Async[F].raiseError(InternalServerError(s"Unexpected reply when starting a draw: $other"))
        }
      } yield claim

    override def completeDraw(result: LotteryResult, token: String): F[Unit] =
      redis
        .eval(
          completeDrawScript,
          ScriptOutputType.Integer[String],
          List(key(CLOSED, result.lotteryDate), drawingKey(result.lotteryDate), lockKey(result.lotteryDate)),
          List(result.asJson.noSpaces, token)
        )
        .flatMap { stored =>
          Async[F].raiseUnless(stored == 1L)(
            InternalServerError(s"The draw for ${result.lotteryDate} lost its lock before it could store the result")
          )
        }

    override def abandonDraw(lotteryDate: LocalDate, token: String): F[Unit] =
      redis
        .eval(abandonDrawScript, ScriptOutputType.Integer[String], List(lockKey(lotteryDate)), List(token))
        .void
  }
}
