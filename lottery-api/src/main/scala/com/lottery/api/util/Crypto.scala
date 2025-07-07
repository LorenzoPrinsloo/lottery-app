package com.lottery.api.util

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

object Crypto {
  /**
   * Hashes a string using the SHA-256 algorithm.
   * @param input The string to hash
   * @return A hexadecimal string representation of the hash.
   */
  def sha256(input: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8))
    
    hashBytes.map("%02x".format(_)).mkString
  }
}
