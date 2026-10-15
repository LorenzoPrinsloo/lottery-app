package com.lottery.domain

import com.lottery.domain.error.ApiError.BadRequest

/** Input rules shared by the services. Each returns the cleaned-up value or a BadRequest explaining what's wrong. */
object Validation {

  val MaxBallotsPerRequest: Int = 1000
  private val MaxNameLength = 100
  private val MaxEmailLength = 254
  private val EmailPattern = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$".r

  /** Emails identify participants, so they're compared trimmed and in lower case. */
  def email(raw: String): Either[BadRequest, String] = {
    val email = raw.trim.toLowerCase
    if (email.length > MaxEmailLength || EmailPattern.matches(email) == false)
      Left(BadRequest(s"'$raw' is not a valid email address"))
    else Right(email)
  }

  def name(raw: String): Either[BadRequest, String] = {
    val name = raw.trim
    if (name.isEmpty) Left(BadRequest("Name can't be empty"))
    else if (name.length > MaxNameLength) Left(BadRequest(s"Name can't be longer than $MaxNameLength characters"))
    else Right(name)
  }

  def ballotCount(count: Int): Either[BadRequest, Int] =
    if (count < 1 || count > MaxBallotsPerRequest)
      Left(BadRequest(s"Ballot count must be between 1 and $MaxBallotsPerRequest, got $count"))
    else Right(count)
}
