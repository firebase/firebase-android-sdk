// Copyright 2022 Google LLC
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

package com.google.firebase.authexchange

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TOKEN = "token"
private const val EXPIRE_TIME_MILLIS = 1000L

@RunWith(RobolectricTestRunner::class)
class AuthExchangeTokenTest {
  @Test
  fun `two objects with identical fields are considered equal`() {
    val result1 = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)
    val result2 = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)

    assertThat(result1 == result2).isTrue()
  }

  @Test
  fun `an object is considered equal to itself`() {
    val result = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)

    assertThat(result == result).isTrue()
  }

  @Test
  fun `two objects with different fields are not considered equal`() {
    val result1 = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)
    val result2 = AuthExchangeToken("other_token", EXPIRE_TIME_MILLIS)

    assertThat(result1 == result2).isFalse()
  }

  @Test
  fun `an object is not considered equal to null`() {
    val result = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)

    assertThat(result == null).isFalse()
  }

  @Test
  fun `equal objects have the same hash code`() {
    val result1 = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)
    val result2 = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)

    assertThat(result1.hashCode()).isEqualTo(result2.hashCode())
  }

  @Test
  fun `different objects have different hash codes`() {
    val result1 = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)
    val result2 = AuthExchangeToken("other_token", EXPIRE_TIME_MILLIS)

    assertThat(result1.hashCode()).isNotEqualTo(result2.hashCode())
  }

  @Test
  fun `toString returns expected string`() {
    val result = AuthExchangeToken(TOKEN, EXPIRE_TIME_MILLIS)
    val expectedString = "AuthExchangeToken{token=$TOKEN, expireTimeMillis=$EXPIRE_TIME_MILLIS}"

    assertThat(result.toString()).isEqualTo(expectedString)
  }
}
