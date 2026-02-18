/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.util.AlphanumericStringUtil.toAlphaNumericString
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AlphanumericStringUtilUnitTest {

  @Test
  fun `toAlphaNumericString() interprets the alphabet`() {
    val byteArray =
      byteArrayOf(
        0,
        68,
        50,
        20,
        -57,
        66,
        84,
        -74,
        53,
        -49,
        -124,
        101,
        58,
        86,
        -41,
        -58,
        117,
        -66,
        119,
        -33
      )
    // This string is `ALPHANUMERIC_ALPHABET` in `Util.kt`
    byteArray.toAlphaNumericString() shouldBe "23456789abcdefghjkmnopqrstuvwxyz"
  }

  @Test
  fun `toAlphaNumericString() where the final 5-bit chunk is 1 bit`() {
    byteArrayOf(75, 50).let { it.toAlphaNumericString() shouldBe "bet2" }
    byteArrayOf(75, 51).let { it.toAlphaNumericString() shouldBe "bet3" }
  }

  @Test
  fun `toAlphaNumericString() where the final 5-bit chunk is 2 bits`() {
    byteArrayOf(117, -40, -116, -66, -105, -61, 18, -117, -52).let {
      it.toAlphaNumericString() shouldBe "greathorsebarn2"
    }
    byteArrayOf(117, -40, -116, -66, -105, -61, 18, -117, -49).let {
      it.toAlphaNumericString() shouldBe "greathorsebarn5"
    }
  }

  @Test
  fun `toAlphaNumericString() where the final 5-bit chunk is 3 bits`() {
    byteArrayOf(64).let { it.toAlphaNumericString() shouldBe "a2" }
    byteArrayOf(71).let { it.toAlphaNumericString() shouldBe "a9" }
  }

  @Test
  fun `toAlphaNumericString() where the final 5-bit chunk is 4 bits`() {
    byteArrayOf(-58, 117, 48).let { it.toAlphaNumericString() shouldBe "stun2" }
    byteArrayOf(-58, 117, 63).let { it.toAlphaNumericString() shouldBe "stunh" }
  }

  @Test
  fun `toAlphaNumericString() on empty byte array`() {
    val emptyByteArray = byteArrayOf()
    emptyByteArray.toAlphaNumericString() shouldBe ""
  }

  @Test
  fun `toAlphaNumericString() on byte array with 1 element of value 0`() {
    val byteArray = byteArrayOf(0)
    byteArray.toAlphaNumericString() shouldBe "22"
  }

  @Test
  fun `toAlphaNumericString() on byte array with 1 element of value 1`() {
    val byteArray = byteArrayOf(1)
    byteArray.toAlphaNumericString() shouldBe "23"
  }

  @Test
  fun `toAlphaNumericString() on byte array with 1 element of value 0xff`() {
    val byteArray = byteArrayOf(0xff.toByte())
    byteArray.toAlphaNumericString() shouldBe "z9"
  }

  @Test
  fun `toAlphaNumericString() on byte array with 1 element of value -1`() {
    val byteArray = byteArrayOf(-1)
    byteArray.toAlphaNumericString() shouldBe "z9"
  }

  @Test
  fun `toAlphaNumericString() on byte array with 1 element of value MIN_VALUE`() {
    val byteArray = byteArrayOf(Byte.MIN_VALUE)
    byteArray.toAlphaNumericString() shouldBe "j2"
  }

  @Test
  fun `toAlphaNumericString() on byte array with 1 element of value MAX_VALUE`() {
    val byteArray = byteArrayOf(Byte.MAX_VALUE)
    byteArray.toAlphaNumericString() shouldBe "h9"
  }

  @Test
  fun `toAlphaNumericString() on byte array containing all possible values`() {
    val byteArray =
      buildList {
          for (i in 0 until 512) {
            add(i.toByte())
          }
        }
        .toByteArray()
    byteArray.toAlphaNumericString() shouldBe
      "222j62s62o52g42b3a7js5ag3wa346jn4jcke7ss56f3q92x5shm2ab46em4cbk972omocte7or4ye3k8atnafbq" +
        "8ww5mgkv9jynwhu2a7368k47at5ojmccbf86unmhc3ap6ouocpd7gq4tdbfpsrcydxj84sn5ekmqetvaf7p8qv" +
        "5fftrr2wdmgfu9cxnrh3wroyvwhpz9z263jc3sb3e8jy6an4odkm8sx5wjm8bb976pmudtk8eunggbv9ozo4ju" +
        "7ax6oqnchc7bpcputdfgpysd5epnqmuvffxsr8xdrh7xruzw3jg4sh4edkq9t56wpmyetr9ezo8kudbxbpgquz" +
        "efnqqvvngxxrz2w9kg9t97wvnykuhcxhqgvvrhy5sz7wzoyrvhhy9tzdxztzhyzw2242j52j4je3sa3672q52f" +
        "3s9k26am4ec3c7jr52eko8sw5oh3ya336akmabb86wo4mckd7jqmwdtj86t58f3p8svnjgbu9ey5uhkza32o6j" +
        "u6ap56gm4bbb7osncgbxa74omnckcpepusd7f7qr4xdthq2sd4efm8ctn9f3oqouvefpr8yw5kgbtraxdqgxw9" +
        "mynvhkyrwzw2j83a9367ju5sk4eckg8av5ohm4at76womqdbh86tncftt9eynyjc5ap5ommufbxap8pcrd7fpu" +
        "rv3efmqguddfprr4wvpgxwrqzdzj83sd3wbkg8sz6enmqdtn8wxnyju9bf9p8puvdxkqguvhgfvrqzw5jy7sz6" +
        "wrnghu9bxdpytvhgxzsh5wrnynuzfxzsz9xhrz9xzvz3"
  }

  @Test
  fun `toAlphaNumericString() with random byte arrays`() = runTest {
    val byteArrayArb = Arb.byteArray(length = Arb.int(0, 100), content = Arb.byte())
    checkAll(100_000, byteArrayArb) { byteArray -> byteArray.toAlphaNumericString() }
  }
}

/*
The Python script below can be used to generate the byte arrays.

Just replace the argument to toBase32BitString() with the string you want to encode. If the length
of the resulting bit string is not a multiple of 8 then you will need to pad the string, like this:
  bitstring = toBase32BitString("aa") + "000000" # Add zeroes to pad the string to a valid length

import io

ALPHABET = "23456789abcdefghjkmnopqrstuvwxyz"

def toBase32BitString(s):
  buf = io.StringIO()
  for c in s:
    alphabetIndex = ALPHABET.index(c)
    buf.write(f"{alphabetIndex:05b}")
  return buf.getvalue()

bitstring = toBase32BitString("badmoods")

values = []
for i in range(0, len(bitstring), 8):
  chunk = bitstring[i:i+8]
  if len(chunk) != 8:
    raise ValueError(f"invalid chunk size at {i}: {len(chunk)} (expected exactly 8)")
  intvalue = int(chunk, 2)
  values.append(intvalue if intvalue <= 127 else (intvalue - 256))

print(values)
*/
