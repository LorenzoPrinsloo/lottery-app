package com.lottery.domain.request

import io.circe.Codec

case class SubmitBallotsRequest(
    email: String,
    noBallots: Int
) derives Codec
