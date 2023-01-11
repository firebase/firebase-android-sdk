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

private val AUTH_EXCHANGE_TOKEN = AuthExchangeToken(token = "token", expireTimeMillis = 1000L)
private const val PROVIDER_ID_TOKEN = "provider_id_token"
private const val PROVIDER_REFRESH_TOKEN = "provider_refresh_token"

@RunWith(RobolectricTestRunner::class)
class AuthExchangeResultTest {
  @Test
  fun `two objects with identical fields are considered equal`() {
    val result1 = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)
    val result2 = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)

    assertThat(result1 == result2).isTrue()
  }

  @Test
  fun `an object is considered equal to itself`() {
    val result = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)

    assertThat(result == result).isTrue()
  }

  @Test
  fun `two objects with different fields are not considered equal`() {
    val result1 = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)
    val result2 =
      AuthExchangeResult(
        AUTH_EXCHANGE_TOKEN,
        PROVIDER_ID_TOKEN,
        /* providerRefreshToken= */ "other_token"
      )

    assertThat(result1 == result2).isFalse()
  }

  @Test
  fun `an object is not considered equal to null`() {
    val result = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)

    assertThat(result == null).isFalse()
  }

  @Test
  fun `equal objects have the same hash code`() {
    val result1 = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)
    val result2 = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)

    assertThat(result1.hashCode()).isEqualTo(result2.hashCode())
  }

  @Test
  fun `different objects have different hash codes`() {
    val result1 = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)
    val result2 =
      AuthExchangeResult(
        AUTH_EXCHANGE_TOKEN,
        PROVIDER_ID_TOKEN,
        providerRefreshToken = "other_token"
      )

    assertThat(result1.hashCode()).isNotEqualTo(result2.hashCode())
  }

  @Test
  fun `toString with fully populated object`() {
    val result = AuthExchangeResult(AUTH_EXCHANGE_TOKEN, PROVIDER_ID_TOKEN, PROVIDER_REFRESH_TOKEN)
    val expectedString =
      "AuthExchangeResult{authExchangeToken=$AUTH_EXCHANGE_TOKEN, providerIdToken=$PROVIDER_ID_TOKEN, providerRefreshToken=$PROVIDER_REFRESH_TOKEN}"

    assertThat(result.toString()).isEqualTo(expectedString)
  }

  @Test
  fun `toString with null values`() {
    val result =
      AuthExchangeResult(AUTH_EXCHANGE_TOKEN, /* providerIdToken= */ null, PROVIDER_REFRESH_TOKEN)
    val expectedString =
      "AuthExchangeResult{authExchangeToken=$AUTH_EXCHANGE_TOKEN, providerIdToken=null, providerRefreshToken=$PROVIDER_REFRESH_TOKEN}"

    assertThat(result.toString()).isEqualTo(expectedString)
  }
}
