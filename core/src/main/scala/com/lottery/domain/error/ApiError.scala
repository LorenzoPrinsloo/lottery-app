package com.lottery.domain.error

import cats.effect.kernel.Async
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Response
import org.http4s.{DecodeFailure, Response}
import org.http4s.dsl.Http4sDsl

sealed trait ApiError extends Throwable {
  def details: String
  override def getMessage: String = details
}

object ApiError {
  case class BadRequest(details: String) extends ApiError

  case class Conflict(details: String) extends ApiError

  case class InternalServerError(details: String) extends ApiError

  case class NotFound(details: String) extends ApiError

  implicit val codec: Codec[ApiError] = deriveCodec
}
