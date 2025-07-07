package com.lottery.routes

import cats.MonadThrow
import cats.effect.*
import cats.implicits.*
import com.lottery.domain.Participant
import com.lottery.domain.Participant.participantCodec
import com.lottery.domain.error.ApiError
import com.lottery.domain.request.{
  RegisterParticipantRequest,
  SubmitBallotsRequest
}
import com.lottery.logging.Logging
import com.lottery.service.LotteryService
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder, Json}
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import java.time.LocalDate
import org.http4s.dsl.*

class LotteryRoutes[F[_]: Async: Concurrent](service: LotteryService[F])
    extends Logging[F]
    with Http4sDsl[F] {

  private object LocalDateVar {
    def unapply(str: String): Option[LocalDate] = {
      Either.catchNonFatal(LocalDate.parse(str)).toOption
    }
  }

  private def withErrorHandling(
      request: Request[F]
  )(block: => F[Response[F]]): F[Response[F]] = {
    block
      .onError(error =>
        logger.error(error)(s"${request.method} ${request.uri} Failed")
      )
      .recoverWith {
        case apiError: ApiError => apiError.toResponse
        case e                  => InternalServerError(s"Unhandled Exception: $e")
      }
  }

  extension(e: ApiError) {
    def toResponse: F[Response[F]] = {
      e match {
        case ApiError.BadRequest(details) => BadRequest(details)
        case ApiError.Conflict(details)   => Conflict(details)
        case ApiError.InternalServerError(details) =>
          InternalServerError(details)
      }
    }
  }

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

    case GET -> Root / "lotteries" / LocalDateVar(date) / "winner" => ???
//      LotteryApplicationService.getWinner(date).flatMap {
//        case Some(result) => Ok(result)
//        case None => NotFound(s"No winner found for lottery on $date")
//      }

    // TODO move to draw-service
//    case POST -> Root / "lotteries" / LocalDateVar(date) / "performDraw" => ???
  }
}
