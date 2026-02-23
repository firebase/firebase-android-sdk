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

import com.google.firebase.dataconnect.CacheSettings
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
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.cyrillic
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.hex
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import io.mockk.mockk
import kotlin.random.nextInt
import kotlinx.serialization.modules.SerializersModule

@Suppress("MemberVisibilityCanBePrivate")
object DataConnectArb {
  val anyScalar: AnyScalarArb = AnyScalarArb
  val javaTime: JavaTimeArbs = JavaTimeArbs

  val codepoints: Arb<Codepoint> =
    Arb.choice(
        Codepoint.ascii(),
        Codepoint.egyptianHieroglyphs(),
        Codepoint.arabic(),
        Codepoint.cyrillic(),
      )
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

  fun cacheSettings(
    storage: Arb<CacheSettings.Storage> = Arb.enum<CacheSettings.Storage>(),
  ): Arb<CacheSettings> = storage.map(::CacheSettings)

  fun dataConnectSettings(
    prefix: String? = null,
    host: Arb<String> = host(),
    sslEnabled: Arb<Boolean> = Arb.boolean(),
    cacheSettings: Arb<CacheSettings?> = cacheSettings().orNull(nullProbability = 0.33),
  ): Arb<DataConnectSettings> {
    val wrappedHost = prefix?.let { host.withPrefix(it) } ?: host
    return Arb.bind(wrappedHost, sslEnabled, cacheSettings, ::DataConnectSettings)
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

  fun dataConnectPath(
    size: IntRange = 0..5,
    field: Arb<DataConnectPathSegment.Field> = fieldPathSegment(),
    listIndex: Arb<DataConnectPathSegment.ListIndex> = listIndexPathSegment(),
  ): Arb<List<DataConnectPathSegment>> =
    DataConnectPathArb(
      sizeArb = Arb.int(size),
      fieldArb = field,
      listIndexArb = listIndex,
    )
}

private class DataConnectPathArb(
  private val sizeArb: Arb<Int>,
  private val fieldArb: Arb<DataConnectPathSegment.Field>,
  private val listIndexArb: Arb<DataConnectPathSegment.ListIndex>,
) : Arb<List<DataConnectPathSegment>>() {

  private val probabilityArb = ProbabilityArb()

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        fieldProbability = probabilityArb.next(rs, edgeCase = false),
        segmentEdgeCaseProbability = probabilityArb.next(rs, edgeCase = false),
        sizeEdgeCaseProbability = probabilityArb.next(rs, edgeCase = false),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): List<DataConnectPathSegment> {
    val edgeCases: Set<EdgeCase> = run {
      val edgeCaseCount = rs.random.nextInt(1..EdgeCase.entries.size)
      EdgeCase.entries.shuffled(rs.random).take(edgeCaseCount).toSet()
    }

    val fieldProbability =
      probabilityArb.next(rs, edgeCase = edgeCases.contains(EdgeCase.FieldProbability))
    val segmentEdgeCaseProbability =
      probabilityArb.next(rs, edgeCase = edgeCases.contains(EdgeCase.SegmentEdgeCaseProbability))
    val sizeEdgeCaseProbability =
      probabilityArb.next(rs, edgeCase = edgeCases.contains(EdgeCase.SizeEdgeCaseProbability))

    return generate(
      rs,
      fieldProbability = fieldProbability,
      segmentEdgeCaseProbability = segmentEdgeCaseProbability,
      sizeEdgeCaseProbability = sizeEdgeCaseProbability,
    )
  }

  fun generate(
    rs: RandomSource,
    fieldProbability: Float,
    segmentEdgeCaseProbability: Float,
    sizeEdgeCaseProbability: Float,
  ): List<DataConnectPathSegment> {
    val size = sizeArb.next(rs, sizeEdgeCaseProbability)
    check(size >= 0) {
      "invalid size generated by $sizeArb: $size " + "(must be greater than or equal to zero)"
    }

    return List(size) {
      val isField = rs.random.nextFloat() < fieldProbability
      val segmentArb = if (isField) fieldArb else listIndexArb
      segmentArb.next(rs, segmentEdgeCaseProbability)
    }
  }

  private enum class EdgeCase {
    FieldProbability,
    SegmentEdgeCaseProbability,
    SizeEdgeCaseProbability,
  }
}

val Arb.Companion.dataConnect: DataConnectArb
  get() = DataConnectArb

inline fun <reified T : Any> Arb.Companion.mock(): Arb<T> = arbitrary { mockk<T>(relaxed = true) }

fun <T> Arb<T>.next(rs: RandomSource, edgeCaseProbability: Float): T {
  require(edgeCaseProbability in 0.0f..1.0f) {
    "invalid edgeCaseProbability: $edgeCaseProbability (must be between 0.0 and 1.0, inclusive)"
  }

  val isEdgeCase =
    when (edgeCaseProbability) {
      0.0f -> false
      1.0f -> true
      else -> rs.random.nextFloat() < edgeCaseProbability
    }

  return next(rs, edgeCase = isEdgeCase)
}

fun <T> Arb<T>.next(rs: RandomSource, edgeCase: Boolean): T =
  if (edgeCase) {
    edgecase(rs) ?: sample(rs).value
  } else {
    sample(rs).value
  }

class ProbabilityArb : Arb<Float>() {
  override fun edgecase(rs: RandomSource) = if (rs.random.nextBoolean()) 1.0f else 0.0f
  override fun sample(rs: RandomSource) = rs.random.nextFloat().asSample()
}
