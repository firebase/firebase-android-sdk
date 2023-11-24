// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UtilTest {

  @Test
  fun `ByteArray toHexString() on empty byte array`() {
    val emptyByteArray = byteArrayOf()
    assertThat(emptyByteArray.toHexString()).isEqualTo("")
  }

  @Test
  fun `ByteArray toHexString() on byte array with 1 element of value 0`() {
    val byteArray = byteArrayOf(0)
    assertThat(byteArray.toHexString()).isEqualTo("00")
  }

  @Test
  fun `ByteArray toHexString() on byte array with 1 element of value 1`() {
    val byteArray = byteArrayOf(1)
    assertThat(byteArray.toHexString()).isEqualTo("01")
  }

  @Test
  fun `ByteArray toHexString() on byte array with 1 element of value 0xff`() {
    val byteArray = byteArrayOf(0xff.toByte())
    assertThat(byteArray.toHexString()).isEqualTo("ff")
  }

  @Test
  fun `ByteArray toHexString() on byte array with 1 element of value -1`() {
    val byteArray = byteArrayOf(-1)
    assertThat(byteArray.toHexString()).isEqualTo("ff")
  }

  @Test
  fun `ByteArray toHexString() on byte array with 1 element of value MIN_VALUE`() {
    val byteArray = byteArrayOf(Byte.MIN_VALUE)
    assertThat(byteArray.toHexString()).isEqualTo("80")
  }

  @Test
  fun `ByteArray toHexString() on byte array with 1 element of value MAX_VALUE`() {
    val byteArray = byteArrayOf(Byte.MAX_VALUE)
    assertThat(byteArray.toHexString()).isEqualTo("7f")
  }

  @Test
  fun `ByteArray toHexString() on byte array containing all possible values`() {
    val byteArray =
      buildList<Byte> {
          for (i in 0 until 512) {
            add(i.toByte())
          }
        }
        .toByteArray()
    assertThat(byteArray.toHexString())
      .isEqualTo(
        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2" +
          "b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455" +
          "565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f8" +
          "08182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aa" +
          "abacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d" +
          "5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" +
          "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292" +
          "a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f5051525354" +
          "55565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7" +
          "f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9" +
          "aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d" +
          "4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"
      )
  }
}
