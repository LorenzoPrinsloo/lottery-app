package com.lottery.util

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

class CryptoSpec extends ScalaCheckSuite {

  test("sha256 matches the published test vectors") {
    assertEquals(Crypto.sha256(""), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    assertEquals(Crypto.sha256("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
  }

  property("sha256 is always 64 lowercase hex characters") {
    forAll((s: String) => Crypto.sha256(s).matches("[0-9a-f]{64}"))
  }

  property("different inputs give different hashes") {
    forAll((a: String, b: String) => a == b || Crypto.sha256(a) != Crypto.sha256(b))
  }
}
