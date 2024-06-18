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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.util.nextAlphanumericString
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.next

fun Arb.Companion.keyedString(id: String, key: String, length: Int = 8): Arb<String> =
  arbitrary { rs ->
    "${id}_${key}_${rs.random.nextAlphanumericString(length = length)}"
  }

fun Arb.Companion.connectorConfig(
  key: String,
  connector: Arb<String> = connectorName(key),
  location: Arb<String> = connectorLocation(key),
  serviceId: Arb<String> = connectorServiceId(key)
): Arb<ConnectorConfig> = arbitrary { rs ->
  ConnectorConfig(
    connector = connector.next(rs),
    location = location.next(rs),
    serviceId = serviceId.next(rs),
  )
}

fun Arb.Companion.connectorName(key: String): Arb<String> = keyedString("connector", key)

fun Arb.Companion.connectorLocation(key: String): Arb<String> = keyedString("location", key)

fun Arb.Companion.connectorServiceId(key: String): Arb<String> = keyedString("serviceId", key)

fun Arb.Companion.accessToken(key: String): Arb<String> =
  keyedString("accessToken", key, length = 20)

fun Arb.Companion.requestId(key: String): Arb<String> = keyedString("requestId", key)
