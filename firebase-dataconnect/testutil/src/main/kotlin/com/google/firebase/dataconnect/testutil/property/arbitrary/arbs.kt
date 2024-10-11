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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.string

object DataConnectArb {
  val anyScalar: AnyScalarArb = AnyScalarArb
}

val Arb.Companion.dataConnect: DataConnectArb
  get() = DataConnectArb

fun DataConnectArb.connectorName(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "connector_${string.bind()}" }

fun DataConnectArb.connectorLocation(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "location_${string.bind()}" }

fun DataConnectArb.connectorServiceId(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "serviceId_${string.bind()}" }

fun DataConnectArb.connectorConfig(
  prefix: String? = null,
  connector: Arb<String> = connectorName(),
  location: Arb<String> = connectorLocation(),
  serviceId: Arb<String> = connectorServiceId(),
): Arb<ConnectorConfig> {
  val wrappedConnector = prefix?.let { connector.withPrefix(it) } ?: connector
  val wrappedLocation = prefix?.let { location.withPrefix(it) } ?: location
  val wrappedServiceId = prefix?.let { serviceId.withPrefix(it) } ?: serviceId
  return arbitrary {
    ConnectorConfig(
      connector = wrappedConnector.bind(),
      location = wrappedLocation.bind(),
      serviceId = wrappedServiceId.bind(),
    )
  }
}

fun DataConnectArb.accessToken(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "accessToken_${string.bind()}" }

fun DataConnectArb.requestId(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "requestId_${string.bind()}" }

fun DataConnectArb.operationName(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "operationName_${string.bind()}" }

fun DataConnectArb.projectId(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "projectId_${string.bind()}" }

fun DataConnectArb.host(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "host_${string.bind()}" }

fun DataConnectArb.dataConnectSettings(
  prefix: String? = null,
  host: Arb<String> = host(),
  sslEnabled: Arb<Boolean> = Arb.boolean(),
): Arb<DataConnectSettings> {
  val wrappedHost = prefix?.let { host.withPrefix(it) } ?: host
  return arbitrary {
    DataConnectSettings(host = wrappedHost.bind(), sslEnabled = sslEnabled.bind())
  }
}

fun Arb.Companion.callerSdkType(boolean: Arb<Boolean> = Arb.boolean()): Arb<CallerSdkType> =
  arbitrary {
    if (boolean.bind()) CallerSdkType.Base else CallerSdkType.Generated
  }

fun DataConnectArb.tag(
  string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
): Arb<String> = arbitrary { "tag_${string.bind()}" }
