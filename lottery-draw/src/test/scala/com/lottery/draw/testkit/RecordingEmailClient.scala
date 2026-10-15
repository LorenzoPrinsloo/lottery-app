package com.lottery.draw.testkit

import cats.effect.{IO, Ref}
import com.lottery.draw.client.EmailClient
import org.simplejavamail.api.email.Email

/** Records every email instead of sending it. */
class RecordingEmailClient(val sent: Ref[IO, List[Email]]) extends EmailClient[IO] {
  override def sendMail(email: Email): IO[Unit] = sent.update(_ :+ email)
}

object RecordingEmailClient {
  def make: IO[RecordingEmailClient] = Ref.of[IO, List[Email]](Nil).map(new RecordingEmailClient(_))
}
