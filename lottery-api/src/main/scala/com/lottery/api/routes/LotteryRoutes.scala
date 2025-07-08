package com.lottery.api.routes

import cats.MonadThrow
import cats.effect.*
import cats.implicits.*
import com.lottery.domain.Participant
import com.lottery.domain.Participant.participantCodec
import com.lottery.domain.error.ApiError
import com.lottery.api.domain.request.{
  RegisterParticipantRequest,
  SubmitBallotsRequest
}
import com.lottery.logging.Logging
import com.lottery.api.service.LotteryService
import com.lottery.modules.Http.RouteDsl
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder, Json}
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import java.time.LocalDate
import org.http4s.dsl.*

class LotteryRoutes[F[_]: Async: Concurrent](service: LotteryService[F])
    extends RouteDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "participants" =>
      withErrorHandling(req) {
        for {
          _ <- logger.debug(s"POST /participants: Received request")
          createReq <- req.as[RegisterParticipantRequest]
          newParticipant <- service.registerParticipant(
            createReq.asParticipant()
          )
          _ <- logger.debug(
            s"POST /participants: Registered Participant $newParticipant"
          )
          resp <- Created(newParticipant)
        } yield resp
      }

    case req @ POST -> Root / "lotteries" / LocalDateVar(date) / "ballots" =>
      withErrorHandling(req) {
        if (date.isBefore(LocalDate.now())) {
          BadRequest(s"Cannot submit ballots for a past date: $date")
        } else {
          for {
            request <- req.as[SubmitBallotsRequest]
            submitResponse <- service.submitBallots(
              request.email,
              date,
              request.noBallots
            )
            resp <- Ok(submitResponse)
          } yield resp
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
