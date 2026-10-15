package com.lottery.testkit

import com.lottery.domain.Ballot.BallotId
import com.lottery.domain.{Ballot, LotteryResult, Participant}
import org.scalacheck.Gen
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

object Fixtures {
  val today: LocalDate = LocalDate.of(2030, 6, 15)
  val noon: LocalDateTime = today.atTime(12, 0)

  def participant(name: String = "Ada", email: String = "ada@example.com"): Participant =
    Participant(name, email, noon)

  def ballot(email: String = "ada@example.com", date: LocalDate = today): Ballot =
    Ballot(BallotId(UUID.randomUUID()), email, date, noon)

  def result(date: LocalDate = today, email: String = "ada@example.com"): LotteryResult =
    LotteryResult(date, BallotId(UUID.randomUUID()), email, date.plusDays(1).atTime(0, 1))

  val dates: Gen[LocalDate] = Gen.choose(0L, 20000L).map(LocalDate.of(2000, 1, 1).plusDays)
  val times: Gen[LocalDateTime] = for {
    d <- dates
    s <- Gen.choose(0, 86399)
  } yield d.atStartOfDay.plusSeconds(s.toLong)
  val emails: Gen[String] = for {
    user <- Gen.alphaLowerStr.suchThat(_.nonEmpty).map(_.take(12))
    host <- Gen.alphaLowerStr.suchThat(_.nonEmpty).map(_.take(10))
  } yield s"$user@$host.com"
  val ballots: Gen[Ballot] = for {
    id <- Gen.uuid
    e <- emails
    d <- dates
    t <- times
  } yield Ballot(BallotId(id), e, d, t)
}
