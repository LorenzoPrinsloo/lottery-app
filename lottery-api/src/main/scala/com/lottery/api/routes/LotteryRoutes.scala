package com.lottery.api.routes

import cats.effect.*
import cats.implicits.*
import com.lottery.domain.Participant
import com.lottery.domain.Participant.participantCodec
import com.lottery.api.domain.request.{
  RegisterParticipantRequest,
  SubmitBallotsRequest
}
import com.lottery.logging.Logging
import com.lottery.api.service.LotteryService
import com.lottery.modules.Http.RouteDsl
import io.circe.Encoder
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import java.time.{LocalDate, LocalDateTime, ZoneId}

class LotteryRoutes[F[_]: Async: Concurrent](
    service: LotteryService[F],
    zone: ZoneId = ZoneId.systemDefault()
) extends RouteDsl[F] {

  private def now: F[LocalDateTime] =
    Async[F].realTimeInstant.map(LocalDateTime.ofInstant(_, zone))

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "participants" =>
      withErrorHandling(req) {
        for {
          _ <- logger.debug(s"POST /participants: Received request")
          createReq <- req.as[RegisterParticipantRequest]
          registeredAt <- now
          newParticipant <- service.registerParticipant(
            createReq.asParticipant(registeredAt)
          )
          _ <- logger.debug(
            s"POST /participants: Registered Participant $newParticipant"
          )
          resp <- Created(newParticipant)
        } yield resp
      }

    case req @ POST -> Root / "lotteries" / LocalDateVar(date) / "ballots" =>
      withErrorHandling(req) {
        now.map(_.toLocalDate).flatMap { today =>
          if (date.isBefore(today)) {
            BadRequest(s"Cannot submit ballots for a past date: $date")
          } else {
            for {
              request <- req.as[SubmitBallotsRequest]
              submitResponse <- service.submitBallots(
                request.email,
                date,
                request.count
              )
              resp <- Ok(submitResponse)
            } yield resp
          }
        }
      }

    case req @ GET -> Root / "lotteries" / LocalDateVar(date) / "winner" =>
      withErrorHandling(req) {
        for {
          result <- service.fetchLotteryResult(date)
          response <- Ok(result)
        } yield response
      }
  }
}
