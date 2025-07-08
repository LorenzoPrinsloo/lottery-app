package com.lottery.api.domain.request

import io.circe.Codec

case class SubmitBallotsRequest(
   email: String,
   count: Int
) derives Codec
