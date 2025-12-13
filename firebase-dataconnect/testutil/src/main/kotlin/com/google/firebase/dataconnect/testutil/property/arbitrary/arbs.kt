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
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.DataConnectSettings
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arabic
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.ascii
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.cyrillic
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.hex
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.mockk.mockk
import kotlinx.serialization.modules.SerializersModule

@Suppress("MemberVisibilityCanBePrivate")
object DataConnectArb {
  val anyScalar: AnyScalarArb = AnyScalarArb
  val javaTime: JavaTimeArbs = JavaTimeArbs

  val codepoints: Arb<Codepoint> =
    Codepoint.ascii()
      .merge(Codepoint.egyptianHieroglyphs())
      .merge(Codepoint.arabic())
      .merge(Codepoint.cyrillic())
      // Do not produce character code 0 because it's not supported by Postgresql:
      // https://www.postgresql.org/docs/current/datatype-character.html
      .filterNot { it.value == 0 }

  fun string(length: IntRange = 0..100, codepoints: Arb<Codepoint>? = null): Arb<String> =
    Arb.string(length, codepoints ?: DataConnectArb.codepoints)

  fun float(): Arb<Double> = Arb.double().filterNot { it.isNaN() || it.isInfinite() }

  fun id(length: Int = 20): Arb<String> = Arb.string(size = length, Codepoint.alphanumeric())

  fun uuid(): Arb<String> = Arb.string(size = 32, Codepoint.hex())

  fun connectorName(
    string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
  ): Arb<String> = arbitrary { "connector_${string.bind()}" }

  fun connectorLocation(
    string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
  ): Arb<String> = arbitrary { "location_${string.bind()}" }

  fun connectorServiceId(
    string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
  ): Arb<String> = arbitrary { "serviceId_${string.bind()}" }

  fun connectorConfig(
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

  fun accessToken(
    string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
  ): Arb<String> = arbitrary { "accessToken_${string.bind()}" }

  fun requestId(string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())): Arb<String> =
    arbitrary {
      "requestId_${string.bind()}"
    }

  fun operationName(
    string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())
  ): Arb<String> = arbitrary { "operationName_${string.bind()}" }

  fun projectId(string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())): Arb<String> =
    arbitrary {
      "projectId_${string.bind()}"
    }

  fun host(string: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric())): Arb<String> =
    arbitrary {
      "host_${string.bind()}"
    }

  fun dataConnectSettings(
    prefix: String? = null,
    host: Arb<String> = host(),
    sslEnabled: Arb<Boolean> = Arb.boolean(),
  ): Arb<DataConnectSettings> {
    val wrappedHost = prefix?.let { host.withPrefix(it) } ?: host
    return arbitrary {
      DataConnectSettings(host = wrappedHost.bind(), sslEnabled = sslEnabled.bind())
    }
  }

  fun tag(string: Arb<String> = Arb.string(size = 50, Codepoint.alphanumeric())): Arb<String> =
    arbitrary {
      "tag_${string.bind()}"
    }

  fun serializersModule(): Arb<SerializersModule?> =
    arbitrary<SerializersModule> { mockk() }.orNull(nullProbability = 0.333)

  fun fieldPathSegment(string: Arb<String> = string()): Arb<DataConnectPathSegment.Field> =
    string.map { DataConnectPathSegment.Field(it) }

  fun listIndexPathSegment(int: Arb<Int> = Arb.int()): Arb<DataConnectPathSegment.ListIndex> =
    int.map { DataConnectPathSegment.ListIndex(it) }

  fun pathSegment(
    field: Arb<DataConnectPathSegment.Field> = fieldPathSegment(),
    fieldWeight: Int = 1,
    listIndex: Arb<DataConnectPathSegment.ListIndex> = listIndexPathSegment(),
    listIndexWeight: Int = 1,
  ): Arb<DataConnectPathSegment> = Arb.choose(fieldWeight to field, listIndexWeight to listIndex)

  fun errorPath(
    pathSegment: Arb<DataConnectPathSegment> = pathSegment(),
    range: IntRange = 0..10,
  ): Arb<List<DataConnectPathSegment>> = Arb.list(pathSegment, range)
}

val Arb.Companion.dataConnect: DataConnectArb
  get() = DataConnectArb

inline fun <reified T : Any> Arb.Companion.mock(): Arb<T> = arbitrary { mockk<T>(relaxed = true) }

fun <T> Arb<T>.next(rs: RandomSource, edgeCaseProbability: Float): T {
  require(edgeCaseProbability in 0.0f..1.0f) {
    "invalid edgeCaseProbability: $edgeCaseProbability (must be between 0.0 and 1.0, inclusive)"
  }
  return if (rs.random.nextFloat() < edgeCaseProbability) {
    edgecase(rs)!!
  } else {
    sample(rs).value
  }
}
