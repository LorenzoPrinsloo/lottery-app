package com.lottery.draw.client

import cats.effect.Async
import cats.implicits.*
import com.lottery.domain.config.SmtpConfig
import org.simplejavamail.api.email.Email
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.mailer.MailerBuilder

trait EmailClient[F[_]] {
  def sendMail(email: Email): F[Unit]
}
object EmailClient {
  def default[F[_]: Async](config: SmtpConfig): EmailClient[F] =
    new EmailClient[F] {
      private val mailer: Mailer = MailerBuilder
        .withSMTPServer(config.host, config.port)
        .buildMailer()

      override def sendMail(email: Email): F[Unit] = {
        Async[F].blocking(mailer.sendMail(email)).void
      }
    }
}
