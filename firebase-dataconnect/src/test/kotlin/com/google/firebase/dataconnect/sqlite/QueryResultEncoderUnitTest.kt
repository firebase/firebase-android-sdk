/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.testutil.BuildByteArrayDSL
import com.google.firebase.dataconnect.testutil.beEqualTo
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isRecursivelyEmpty
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingLongerThanUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingShorterThanOrEqualToUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.recursivelyEmptyListValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterationsIfNotNull
import com.google.firebase.dataconnect.testutil.randomlyInsertValue
import com.google.firebase.dataconnect.testutil.randomlyInsertValues
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.structFastEqual
import com.google.firebase.dataconnect.testutil.toListValue
import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.withAddedListIndex
import com.google.firebase.dataconnect.testutil.withAddedPathSegment
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValue
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValues
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.assume
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `bool values`() = runTest {
    data class BoolTestCase(val value: Boolean, val valueTypeIndicator: Byte)
    val arb =
      Exhaustive.of(
        BoolTestCase(true, QueryResultCodec.VALUE_BOOL_TRUE),
        BoolTestCase(false, QueryResultCodec.VALUE_BOOL_FALSE),
      )
    checkAll(propTestConfig, arb) { sample ->
      val struct = Struct.newBuilder().putFields("", sample.value.toValueProto()).build()

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct size
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(sample.valueTypeIndicator)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
    }
  }

  @Test
  fun `number values`() = runTest {
    checkAll(propTestConfig, DoubleEncodingTestCase.arb()) { sample ->
      val struct = Struct.newBuilder().putFields("", sample.value.toValueProto()).build()

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct size
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        sample.encode(this)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
    }
  }

  @Test fun `string values`() = verifyStringValues(StringEncodingTestCase.arb())

  @Test
  fun `long string values`() =
    verifyStringValues(StringEncodingTestCase.longStringsArb(), iterations = 50)

  private fun verifyStringValues(arb: Arb<StringEncodingTestCase>, iterations: Int? = null) =
    runTest {
      checkAll(propTestConfig.withIterationsIfNotNull(iterations), arb) { sample ->
        val struct = Struct.newBuilder().putFields("", sample.string.toValueProto()).build()

        val encodeResult = QueryResultEncoder.encode(struct)

        val expectedEncodedBytes = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(1) // struct size
          put(QueryResultCodec.VALUE_STRING_EMPTY)
          sample.encode(this)
        }

        encodeResult.byteArray shouldBe expectedEncodedBytes
        QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
      }
    }

  @Test
  fun `struct values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `list values`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct(size = 1..2, depth = 1..2)
    val listValueArb =
      Arb.choice(
        Arb.proto.listValue(size = 0..5, depth = 1..2).map { it.listValue },
        Arb.proto.recursivelyEmptyListValue().map { it.listValue },
      )
    checkAll(propTestConfig, structArb, Arb.list(listValueArb, 1..5)) { struct, listValueSamples ->
      val listValues = listValueSamples.map { it.toValueProto() }
      val structWithListValues =
        struct.struct.withRandomlyInsertedValues(
          listValues,
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )
      structWithListValues.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test fun `struct keys`() = verifyStringStructKeys(StringEncodingTestCase.arb())

  @Test
  fun `long struct keys`() =
    verifyStringStructKeys(StringEncodingTestCase.longStringsArb(), iterations = 50)

  private fun verifyStringStructKeys(arb: Arb<StringEncodingTestCase>, iterations: Int? = null) =
    runTest {
      checkAll(propTestConfig.withIterationsIfNotNull(iterations), arb) { sample ->
        val struct =
          Struct.newBuilder().putFields(sample.string, Value.getDefaultInstance()).build()

        val encodeResult = QueryResultEncoder.encode(struct)

        val expectedEncodedBytes = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(1) // struct size
          sample.encode(this)
          put(QueryResultCodec.VALUE_KIND_NOT_SET)
        }

        encodeResult.byteArray shouldBe expectedEncodedBytes
        QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
      }
    }

  @Test
  fun `string encodings round trip`() = runTest {
    val structArb =
      Arb.proto.struct(
        size = 1..10,
        key = StringEncodingTestCase.arb().map { it.string },
        scalarValue = StringEncodingTestCase.arb().map { it.string.toValueProto() },
      )
    checkAll(propTestConfig, structArb) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `long string encodings round trip`() = runTest {
    val structArb =
      Arb.proto.struct(
        size = 1,
        key = StringEncodingTestCase.longStringsArb().map { it.string },
        scalarValue = StringEncodingTestCase.longStringsArb().map { it.string.toValueProto() },
      )
    checkAll(propTestConfig.withIterations(50), structArb) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `entity IDs are encoded using SHA-512`() = runTest {
    checkAll(propTestConfig, StringEncodingTestCase.arb()) { entityId ->
      val entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string)

      val encodeResult = QueryResultEncoder.encode(Struct.getDefaultInstance(), entityIdByPath)

      encodeResult.entities shouldHaveSize 1
      val encodedEntityId = encodeResult.entities[0].encodedId
      encodedEntityId shouldBe entityId.string.calculateExpectedEncodingAsEntityId()
    }
  }

  @Test
  fun `entity ID contains code points with 1, 2, 3, and 4 byte UTF-8 encodings`() = runTest {
    checkAll(propTestConfig, StringEncodingTestCase.arb(), Arb.proto.struct()) { entityId, struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct(
        expectedEntities = listOf(struct.struct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string),
      )
    }
  }

  @Test
  fun `entity ID is a long strings`() = runTest {
    checkAll(
      propTestConfig.withIterations(50),
      StringEncodingTestCase.longStringsArb().map { it.string },
      Arb.proto.struct(),
    ) { entityId, struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct(
        expectedEntities = listOf(struct.struct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityId),
      )
    }
  }

  @Test
  fun `entity is the entire struct`() = runTest {
    checkAll(propTestConfig, EntityIdSample.arb(), Arb.proto.struct()) { entityId, struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct(
        expectedEntities = listOf(struct.struct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string),
      )
    }
  }

  @Test
  fun `entity contains nested entities in struct keys`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()
    checkAll(propTestConfig, Arb.int(2..5)) { entityCount ->
      val generateKey = { structKeyArb.bind() }
      val entities = MutableList(entityCount) { structArb.bind().struct }
      val entityPaths = mutableListOf<DataConnectPath>()
      val rootIsEntity = entityCount % 2 != 0
      val entityIterator = entities.iterator()
      val rootStructBuilder =
        (if (rootIsEntity) entityIterator.next() else structArb.bind().struct).toBuilder()
      val nestedEntityBuilder = entityIterator.next().toBuilder()
      val nestedEntity2RelativePath =
        nestedEntityBuilder.randomlyInsertValue(
          entityIterator.next().toValueProto(),
          randomSource().random,
          generateKey,
        )
      val nestedEntity1Path =
        rootStructBuilder.randomlyInsertValue(
          nestedEntityBuilder.build().toValueProto(),
          randomSource().random,
          generateKey,
        )
      entityPaths.add(nestedEntity1Path)
      entityPaths.add(nestedEntity1Path + nestedEntity2RelativePath)
      while (entityIterator.hasNext()) {
        val entity = entityIterator.next()
        val entityPath =
          rootStructBuilder.randomlyInsertValue(
            entity.toValueProto(),
            randomSource().random,
            generateKey,
          )
        entityPaths.add(entityPath)
      }
      if (rootIsEntity) {
        entityPaths.add(emptyDataConnectPath())
      }
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        entityPaths.forEach { putWithRandomUniqueEntityId(it) }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(
        expectedEntities = entities.toList(),
        entityIdByPath = entityIdByPath,
      )
    }
  }

  @Test
  fun `entity contains lists of lists of entities`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()
    val listValueArb =
      Arb.proto
        .listValue(
          depth = 2..4,
          structSize = IntRange.EMPTY,
          scalarValue = structArb.map { it.toValueProto() }
        )
        .filterNot { it.listValue.isRecursivelyEmpty() }

    checkAll(propTestConfig, structArb, listValueArb) { struct, listValueSample ->
      val entityPathValuePairs = listValueSample.descendants.filterNot { it.value.isListValue }
      val entities = entityPathValuePairs.map { it.value.structValue }
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValueSample.listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        entityPathValuePairs
          .map { it.path }
          .forEach { entitySubPath -> putWithRandomUniqueEntityId(listValuePath + entitySubPath) }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `entity contains lists of lists of entities with empty lists interspersed`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()

    checkAll(propTestConfig, structArb, Arb.int(2..10), Arb.proto.recursivelyEmptyListValue()) {
      struct,
      subEntityCount,
      recursivelyEmptyListValue ->
      val subEntities = List(subEntityCount) { structArb.bind().struct }
      val listValueBuilder = recursivelyEmptyListValue.listValue.toBuilder()
      val entitySubPaths =
        listValueBuilder.randomlyInsertValues(
          subEntities.map { it.toValueProto() },
          randomSource().random,
        )
      val listValue = listValueBuilder.build()
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        putWithRandomUniqueEntityId(emptyDataConnectPath())
        entitySubPaths.forEach { entitySubPath ->
          putWithRandomUniqueEntityId(listValuePath + entitySubPath)
        }
      }
      val entities = buildList {
        add(struct.struct)
        addAll(subEntities)
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `entity contains lists of lists of non-entities with empty lists interspersed`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()
    val entityIdArb = EntityIdSample.arb()

    checkAll(propTestConfig, structArb, Arb.int(2..10), Arb.proto.recursivelyEmptyListValue()) {
      struct,
      nonEntityCount,
      recursivelyEmptyListValue ->
      val nonEntities = List(nonEntityCount) { structArb.bind().struct }
      val listValueBuilder = recursivelyEmptyListValue.listValue.toBuilder()
      listValueBuilder.randomlyInsertValues(
        nonEntities.map { it.toValueProto() },
        randomSource().random,
      )
      val listValue = listValueBuilder.build()
      val rootStruct =
        struct.struct.withRandomlyInsertedValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(
        expectedEntities = listOf(rootStruct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityIdArb.bind().string),
      )
    }
  }

  @Test
  fun `entity contains lists of lists of entities and non-entities should throw`() = runTest {
    val valueExceptRecursivelyEmptyListsArb =
      Arb.proto.value().filterNot { it.isListValue && it.listValue.isRecursivelyEmpty() }
    val structKeyArb = Arb.proto.structKey()
    checkAll(
      propTestConfig,
      Arb.proto.struct(),
      Arb.proto.recursivelyEmptyListValue(),
      Arb.list(Arb.proto.struct(), 1..10),
      Arb.list(valueExceptRecursivelyEmptyListsArb, 1..10),
    ) { rootStructBase, recursivelyEmptyListValue, entities, nonEntities ->
      val listValueBuilder = recursivelyEmptyListValue.listValue.toBuilder()
      val insertPaths =
        listValueBuilder.randomlyInsertValues(
          entities.map { it.toValueProto() } + nonEntities,
          randomSource().random,
        )
      val listValue = listValueBuilder.build()
      val rootStructBuilder = rootStructBase.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        putWithRandomUniqueEntityId(emptyDataConnectPath())
        val entityPaths = insertPaths.take(entities.size)
        entityPaths.forEach { entityPath ->
          putWithRandomUniqueEntityId(listValuePath + entityPath)
        }
      }

      class IntermixedEntityAndNonEntityListInEntityException : Exception()
      val exception =
        shouldThrow<IntermixedEntityAndNonEntityListInEntityException> {
          QueryResultEncoder.encode(rootStruct, entityIdByPath)
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "df9tkx7jk4"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "must be all be entities or must all be non-entities"
      }
    }
  }

  @Test
  fun `list containing only entities`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()

    checkAll(propTestConfig, structArb, Arb.int(1..10)) { struct, entityCount ->
      val entities = List(entityCount) { structArb.bind().struct }
      val listValue = entities.map { it.toValueProto() }.toListValue()
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() },
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        repeat(entityCount) { entityIndex ->
          putWithRandomUniqueEntityId(listValuePath.withAddedListIndex(entityIndex))
        }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `list containing mixed entities and non-entities`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()

    checkAll(propTestConfig, structArb, Arb.int(1..10), Arb.int(1..10)) {
      struct,
      entityCount,
      nonEntityCount ->
      val listElements = List(entityCount + nonEntityCount) { structArb.bind().struct }
      val entityIndices = listElements.indices.shuffled(randomSource().random).take(entityCount)
      val entities = listElements.filterIndexed { index, _ -> entityIndices.contains(index) }
      val listValue = listElements.map { it.toValueProto() }.toListValue()
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() },
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        entityIndices.forEach { entityIndex ->
          putWithRandomUniqueEntityId(listValuePath.withAddedListIndex(entityIndex))
        }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `entity paths of non-struct values are ignored`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      val nonStructPaths =
        struct.struct.walk().filterNot { it.value.isStructValue }.map { it.path }.toList()
      assume(nonStructPaths.isNotEmpty())
      val entityIdByPath = buildEntityIdByPathMap {
        nonStructPaths.forEach { nonStructPath -> putWithRandomUniqueEntityId(nonStructPath) }
      }

      struct.struct.decodingEncodingShouldProduceIdenticalStruct(emptyList(), entityIdByPath)
    }
  }

  @Test
  fun `entity paths of non-existent values are ignored`() = runTest {
    val structKeyArb = Arb.proto.structKey()

    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      val nonExistentPaths = buildList {
        struct.struct.walk().forEach { (path, value) ->
          val nonExistentSubPathsArb =
            if (value.isStructValue) {
              structKeyArb
                .filterNot { value.structValue.containsFields(it) }
                .map(DataConnectPathSegment::Field)
            } else if (value.isListValue) {
              Arb.choice(Arb.negativeInt(), Arb.int(min = value.listValue.valuesCount))
                .map(DataConnectPathSegment::ListIndex)
            } else {
              null
            }
          if (nonExistentSubPathsArb !== null) {
            repeat(3) {
              val nonExistentPath = path.withAddedPathSegment(nonExistentSubPathsArb.bind())
              add(nonExistentPath)
            }
          }
        }
      }
      assume(nonExistentPaths.isNotEmpty())
      val entityIdByPath = buildEntityIdByPathMap {
        nonExistentPaths.forEach { nonExistentPath -> putWithRandomUniqueEntityId(nonExistentPath) }
      }

      struct.struct.decodingEncodingShouldProduceIdenticalStruct(emptyList(), entityIdByPath)
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 1000,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
    shrinkingMode = ShrinkingMode.Off,
  )

/** A test case for encoding a [Double] value into a byte array. */
private sealed class DoubleEncodingTestCase(val value: Double) {

  /** Encodes the [value] into the given [dsl]. */
  abstract fun encode(dsl: BuildByteArrayDSL)

  /** A test case for encoding positive zero, +0.0 */
  data object PositiveZero : DoubleEncodingTestCase(0.0) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_POSITIVE_ZERO)
    }
  }

  /** A test case for encoding negative zero, -0.0 */
  data object NegativeZero : DoubleEncodingTestCase(-0.0) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_NEGATIVE_ZERO)
    }
  }

  /**
   * A test case for encoding a [Double] as a 64-bit floating point number.
   *
   * @property description A description of the value, for debugging purposes.
   */
  class DoubleEncoded(value: Double, val description: String) : DoubleEncodingTestCase(value) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_DOUBLE)
      dsl.putDouble(value)
    }
    override fun toString() = "DoubleEncoded($value, description=$description)"
  }

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 32-bit unsigned
   * integer using variable-length encoding.
   *
   * @property intValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
  data class UInt32Encoded(val intValue: Int, val byteCount: Int) :
    DoubleEncodingTestCase(intValue.toDouble()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_UINT32)
      val actualByteCount = dsl.putUInt32(intValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [qmgvc7th7p]"
      }
    }
    override fun toString() = "UInt32Encoded($intValue, byteCount=$byteCount)"
  }

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 32-bit signed
   * integer using ZigZag variable-length encoding.
   *
   * @property intValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
  data class SInt32Encoded(val intValue: Int, val byteCount: Int) :
    DoubleEncodingTestCase(intValue.toDouble()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_SINT32)
      val actualByteCount = dsl.putSInt32(intValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [m74dahr5s5]"
      }
    }
    override fun toString() = "SInt32Encoded($intValue, byteCount=$byteCount)"
  }

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 32-bit integer
   * using fixed-length encoding.
   *
   * @property intValue The integer value to be encoded.
   */
  data class Fixed32IntEncoded(val intValue: Int) : DoubleEncodingTestCase(intValue.toDouble()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_FIXED32)
      dsl.putInt(intValue)
    }
    override fun toString() = "Fixed32IntEncoded($intValue)"
  }

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 64-bit unsigned
   * integer using variable-length encoding.
   *
   * @property longValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
  data class UInt64Encoded(val longValue: Long, val byteCount: Int) :
    DoubleEncodingTestCase(longValue.toDouble()) {
    init {
      require(longValue.toDouble().toLong() == longValue) {
        "longValue=$longValue, which does not losslessly round-trip to and from a double: " +
          "$longValue.toDouble()=${longValue.toDouble()}, " +
          "$longValue.toDouble().toLong()=${longValue.toDouble().toLong()} [b7xe5d3mez]"
      }
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_UINT64)
      val actualByteCount = dsl.putUInt64(longValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [j7pnjj29fb]"
      }
    }
    override fun toString() = "UInt64Encoded($longValue, byteCount=$byteCount)"
  }

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 64-bit signed
   * integer using ZigZag variable-length encoding.
   *
   * @property longValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
  data class SInt64Encoded(val longValue: Long, val byteCount: Int) :
    DoubleEncodingTestCase(longValue.toDouble()) {
    init {
      require(longValue.toDouble().toLong() == longValue) {
        "longValue=$longValue, which does not losslessly round-trip to and from a double: " +
          "$longValue.toDouble()=${longValue.toDouble()}, " +
          "$longValue.toDouble().toLong()=${longValue.toDouble().toLong()} [zvppmvqt53]"
      }
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_SINT64)
      val actualByteCount = dsl.putSInt64(longValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [mhsh585nq2]"
      }
    }
    override fun toString() = "SInt64Encoded($longValue, byteCount=$byteCount)"
  }

  companion object {

    /** Returns an [Arb] that generates [DoubleEncodingTestCase] instances. */
    fun arb(): Arb<DoubleEncodingTestCase> =
      Arb.choice(
        Arb.of(
          PositiveZero,
          NegativeZero,
          DoubleEncoded(Double.NaN, "NaN"),
          DoubleEncoded(Double.POSITIVE_INFINITY, "POSITIVE_INFINITY"),
          DoubleEncoded(Double.NEGATIVE_INFINITY, "NEGATIVE_INFINITY"),
        ),
        Arb.int(1..127).map { UInt32Encoded(it, byteCount = 1) },
        Arb.int(128..16_383).map { UInt32Encoded(it, byteCount = 2) },
        Arb.int(16_384..2_097_151).map { UInt32Encoded(it, byteCount = 3) },
        Arb.int(2_097_152..Int.MAX_VALUE).map { Fixed32IntEncoded(it) },
        Arb.long(Int.MAX_VALUE.toLong() + 1..34_359_738_367).map {
          UInt64Encoded(it, byteCount = 5)
        },
        Arb.long(34_359_738_368..4_398_046_511_103).map { UInt64Encoded(it, byteCount = 6) },
        Arb.long(4_398_046_511_104..562_949_953_421_311).map { UInt64Encoded(it, byteCount = 7) },
        Arb.long(562_949_953_421_312..Long.MAX_VALUE).map {
          DoubleEncoded(it.toDouble(), "long value $it")
        },
        Arb.int(-64..-1).map { SInt32Encoded(it, byteCount = 1) },
        Arb.int(-8192..-65).map { SInt32Encoded(it, byteCount = 2) },
        Arb.int(-1_048_576..-8193).map { SInt32Encoded(it, byteCount = 3) },
        Arb.int(Int.MIN_VALUE..-1_048_577).map { Fixed32IntEncoded(it) },
        Arb.long(-17_179_869_184 until Int.MIN_VALUE.toLong()).map {
          SInt64Encoded(it, byteCount = 5)
        },
        Arb.long(-2_199_023_255_552..-17_179_869_185).map { SInt64Encoded(it, byteCount = 6) },
        Arb.long(-281_474_976_710_656..-2_199_023_255_553).map { SInt64Encoded(it, byteCount = 7) },
        Arb.long(Long.MIN_VALUE..-281_474_976_710_657).map {
          DoubleEncoded(it.toDouble(), "negative long value $it")
        },
        Arb.double()
          .filterNot { it.toLong().toDouble() == it || it.isNaN() || it.isInfinite() || it == 0.0 }
          .map { DoubleEncoded(it, "double typical case") }
      )
  }
}

/** A test case for encoding a [String] value into a byte array. */
private sealed class StringEncodingTestCase(val string: String) {

  /** Encodes the [string] into the given [dsl]. */
  abstract fun encode(dsl: BuildByteArrayDSL)

  /** A test case for encoding an empty string. */
  data object EmptyString : StringEncodingTestCase("") {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_EMPTY)
    }
  }

  /**
   * A test case for encoding a 1-character string where the character can be represented as a
   * single byte.
   *
   * @property char The character to be encoded.
   */
  class OneByte(val char: Char) : StringEncodingTestCase(char.toString()) {
    init {
      require((char.code.toByte().toInt() and 0xFF) == char.code)
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_1BYTE)
      dsl.put(char.code.toByte())
    }
    override fun toString() = "OneByte(char.code=${char.code})"
  }

  /**
   * A test case for encoding a 2-character string where each character can be represented as a
   * single byte.
   *
   * @property char1 The first character to be encoded.
   * @property char2 The second character to be encoded.
   */
  class TwoBytes(val char1: Char, val char2: Char) :
    StringEncodingTestCase(char1.toString() + char2.toString()) {
    init {
      require((char1.code.toByte().toInt() and 0xFF) == char1.code)
      require((char2.code.toByte().toInt() and 0xFF) == char2.code)
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_2BYTE)
      dsl.put(char1.code.toByte())
      dsl.put(char2.code.toByte())
    }
    override fun toString() = "TwoBytes(char1.code=${char1.code}, char2.code=${char2.code})"
  }

  /**
   * A test case for encoding a 1-character string where the character cannot be represented as a
   * single byte.
   *
   * @property char The character to be encoded.
   * @property description A description of the character, for debugging purposes.
   */
  class OneChar(val char: Char, val description: String) : StringEncodingTestCase(char.toString()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_1CHAR)
      dsl.putChar(char)
    }
    override fun toString() = "OneChar(char.code=${char.code}, description=$description)"
  }

  /**
   * A test case for encoding a 2-character string where at least one character cannot be
   * represented as a single byte.
   *
   * @property char1 The first character to be encoded.
   * @property char2 The second character to be encoded.
   * @property description A description of the characters, for debugging purposes.
   */
  class TwoChars(val char1: Char, val char2: Char, val description: String) :
    StringEncodingTestCase(char1.toString() + char2.toString()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_2CHAR)
      dsl.putChar(char1)
      dsl.putChar(char2)
    }
    override fun toString() =
      "TwoChars(" + "char1.code=${char1.code}, char2.code=${char2.code}, description=$description)"
  }

  /**
   * A test case for encoding a [String] using UTF-8 encoding.
   *
   * @property description A description of the string, for debugging purposes.
   */
  class Utf8Encoding(string: String, val description: String) : StringEncodingTestCase(string) {

    private val utf8EncodingBytes = string.encodeToByteArray()

    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_UTF8)
      dsl.putUInt32(utf8EncodingBytes.size)
      dsl.putUInt32(string.length)
      dsl.put(utf8EncodingBytes)
    }
    override fun toString() =
      "Utf8Encoding(description=$description, " +
        "string=$string, utf8EncodingBytes=${utf8EncodingBytes.to0xHexString()})"
  }

  /**
   * A test case for encoding a [String] using UTF-16BE encoding.
   *
   * @property description A description of the string, for debugging purposes.
   */
  class Utf16Encoding(string: String, val description: String) : StringEncodingTestCase(string) {

    private val utf16EncodingBytes = string.toByteArray(StandardCharsets.UTF_16BE)

    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_UTF16)
      dsl.putUInt32(string.length)
      dsl.put(utf16EncodingBytes)
    }

    override fun toString() =
      "Utf16Encoding(description=$description, string=$string, " +
        "utf16EncodingBytes=${utf16EncodingBytes.to0xHexString()})"
  }

  /**
   * A test case for encoding a [String] that contains lone surrogates using UTF-16BE encoding.
   *
   * @property loneSurrogateCount The number of lone surrogates in the string.
   */
  class Utf16WithLoneSurrogatesEncoding(string: String, val loneSurrogateCount: Int) :
    StringEncodingTestCase(string) {

    private val utf16EncodingBytes =
      ByteBuffer.allocate(string.length * 2).let { byteBuffer ->
        val charBuffer = byteBuffer.asCharBuffer()
        charBuffer.put(string)
        byteBuffer.array()
      }

    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_UTF16)
      dsl.putUInt32(string.length)
      dsl.put(utf16EncodingBytes)
    }
    override fun toString() =
      "Utf16WithLoneSurrogatesEncoding(" +
        "loneSurrogateCount=$loneSurrogateCount, string=$string, " +
        "utf16EncodingBytes=${utf16EncodingBytes.to0xHexString()})"
  }

  companion object {

    /** Returns an [Arb] that generates [StringEncodingTestCase] instances. */
    fun arb(): Arb<StringEncodingTestCase> =
      Arb.choice(
        Arb.constant(EmptyString),
        Arb.int(0..255).map { OneByte(it.toChar()) },
        charArbWithCodeGreaterThan255().map { OneChar(it, "not a lone surrogate") },
        Arb.twoValues(Arb.int(0..255)).map { (codepoint1, codepoint2) ->
          TwoBytes(codepoint1.toChar(), codepoint2.toChar())
        },
        Arb.twoValues(charArbWithCodeGreaterThan255()).map { (char1, char2) ->
          TwoChars(char1, char2, "no lone surrogates")
        },
        // The minimum length is 3, as strings shorter than 3 characters are handled above.
        StringWithEncodingLengthArb(Utf8EncodingShorterThanOrEqualToUtf16, 3..100).map {
          Utf8Encoding(it, "utf-8 encoding shorter than or equal to utf-16 encoding")
        },
        StringWithEncodingLengthArb(Utf8EncodingLongerThanUtf16, 3..100).map {
          Utf16Encoding(it, "utf-16 encoding shorter than utf-8")
        },
        Arb.stringWithLoneSurrogates(3..100).map {
          Utf16WithLoneSurrogatesEncoding(it.string, it.loneSurrogateCount)
        },
        Arb.stringWithLoneSurrogates(1..1).map { OneChar(it.string.single(), "lone surrogate") },
        Arb.stringWithLoneSurrogates(2..2).map {
          TwoChars(it.string[0], it.string[1], "${it.loneSurrogateCount} lone surrogates")
        },
        Arb.string(3..20, Arb.codepointWith1ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 1-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWith2ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 2-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWith3ByteUtf8Encoding()).map {
          Utf16Encoding(it, "string with all chars having 3-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWith4ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 4-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWithEvenNumByteUtf8EncodingDistribution()).map {
          if (it.encodeToByteArray().size <= it.toByteArray(StandardCharsets.UTF_16BE).size) {
            Utf8Encoding(it, "string with chars having various byte length utf-8 encoding")
          } else {
            Utf16Encoding(it, "string with chars having various byte length utf-8 encoding")
          }
        },
      )

    private val longStringLengthRange = 2048..99999

    /**
     * Returns an [Arb] that generates [StringEncodingTestCase] instances with long strings (2048 to
     * 99999 characters).
     */
    fun longStringsArb(): Arb<StringEncodingTestCase> =
      Arb.choice(
        StringWithEncodingLengthArb(Utf8EncodingShorterThanOrEqualToUtf16, longStringLengthRange)
          .map { Utf8Encoding(it, "utf-8 encoding shorter than or equal to utf-16 encoding") },
        StringWithEncodingLengthArb(Utf8EncodingLongerThanUtf16, longStringLengthRange).map {
          Utf16Encoding(it, "utf-16 encoding shorter than utf-8")
        },
        Arb.stringWithLoneSurrogates(longStringLengthRange).map {
          Utf16WithLoneSurrogatesEncoding(it.string, it.loneSurrogateCount)
        },
        Arb.string(longStringLengthRange, Arb.codepointWith1ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 1-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWith2ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 2-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWith3ByteUtf8Encoding()).map {
          Utf16Encoding(it, "string with all chars having 3-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWith4ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 4-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWithEvenNumByteUtf8EncodingDistribution())
          .map {
            if (it.encodeToByteArray().size <= it.toByteArray(StandardCharsets.UTF_16BE).size) {
              Utf8Encoding(it, "string with chars having various byte length utf-8 encoding")
            } else {
              Utf16Encoding(it, "string with chars having various byte length utf-8 encoding")
            }
          },
      )
  }
}

/**
 * Asserts that encoding the receiver [Struct] and then decoding it results in the same [Struct].
 *
 * This method:
 * 1. Encodes the receiver [Struct] into a byte array using [QueryResultEncoder.encode], using the
 * given [entityIdByPath].
 * 2. Verifies that the list of entities returned by the encoder matches the given
 * [expectedEntities], ignoring order.
 * 3. Decodes the byte array back into a [Struct] using [QueryResultDecoder.decode], passing along
 * the entities returned by the encoder in step 1.
 * 4. Verifies that the decoded [Struct] is equal to the original receiver [Struct].
 *
 * @param expectedEntities The entities expected to be extracted during encoding.
 * @param entityIdByPath A map of paths to entity IDs, used by the encoder to identify which
 * sub-structures should be treated as entities.
 */
private fun Struct.decodingEncodingShouldProduceIdenticalStruct(
  expectedEntities: List<Struct> = emptyList(),
  entityIdByPath: Map<DataConnectPath, String>? = null,
) {
  val encodeResult = QueryResultEncoder.encode(this, entityIdByPath)

  withClue("QueryResultEncoder.encode() entities returned") {
    class StructWrapper(val struct: Struct) {
      override fun equals(other: Any?) =
        other is StructWrapper && structFastEqual(struct, other.struct)
      override fun hashCode() = struct.hashCode()
      override fun toString() = struct.toCompactString()
    }

    val actualEntitiesWrapped = encodeResult.entities.map { it.data }.map(::StructWrapper)
    val expectedEntitiesWrapped = expectedEntities.map(::StructWrapper)
    actualEntitiesWrapped shouldContainExactlyInAnyOrder expectedEntitiesWrapped
  }

  val decodeEntities =
    encodeResult.entities.map {
      QueryResultDecoder.Entity(
        encodedId = it.encodedId,
        data = it.data,
      )
    }
  val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, decodeEntities)

  withClue("QueryResultDecoder.decode() return value") {
    decodeResult should beEqualTo(this, structPrinter = { it.toCompactString() })
  }
}

private interface BuildEntityIdByPathContext {
  fun putWithRandomUniqueEntityId(path: DataConnectPath)
}

/**
 * Builds a map that associates [DataConnectPath] instances with unique, randomly generated entity
 * IDs.
 *
 * This helper function is used within tests to construct the `entityIdByPath` map required by
 * [QueryResultEncoder.encode]. It ensures that each path specified in the [block] is mapped to a
 * distinct entity ID string.
 *
 * Example usage:
 * ```
 * val entityIdByPath = buildEntityIdByPathMap {
 *   putWithRandomUniqueEntityId(emptyDataConnectPath())
 *   putWithRandomUniqueEntityId(someOtherPath)
 * }
 * ```
 *
 * @return A [Map] where each [DataConnectPath] provided in the [block] is associated with a unique
 * [String] entity ID.
 */
private fun PropertyContext.buildEntityIdByPathMap(
  block: BuildEntityIdByPathContext.() -> Unit
): Map<DataConnectPath, String> {
  @OptIn(DelicateKotest::class) val distinctEntityIdArb = EntityIdSample.arb().distinct()
  val entityIdByPath = mutableMapOf<DataConnectPath, String>()
  val context =
    object : BuildEntityIdByPathContext {
      override fun putWithRandomUniqueEntityId(path: DataConnectPath) {
        entityIdByPath[path] = distinctEntityIdArb.bind().string
      }
    }
  block(context)
  return entityIdByPath.toMap()
}

/**
 * Calculates and returns the expected byte array encoding for a string being used as an entity ID.
 *
 * This function mimics the expected behavior of the [QueryResultEncoder] when it processes an
 * entity ID. It takes each character of the string, encodes it into a 2-byte representation
 * (similar to UTF-16BE), and then computes a SHA-512 hash of the resulting byte sequence.
 */
private fun String.calculateExpectedEncodingAsEntityId(): ByteArray {
  val byteBuffer = ByteBuffer.allocate(length * 2)
  forEach(byteBuffer::putChar)
  val digest = MessageDigest.getInstance("SHA-512")
  byteBuffer.flip()
  digest.update(byteBuffer)
  return digest.digest()
}

/**
 * Creates and returns an [Arb] that generates characters with code points greater than 255.
 *
 * This is useful for testing string encoding scenarios where characters cannot be represented as a
 * single byte. It includes various boundary and surrogate characters as edge cases.
 */
private fun charArbWithCodeGreaterThan255(): Arb<Char> {
  val charRange = 256.toChar()..Char.MAX_VALUE
  val charEdgeCases: List<Char> =
    listOf(
        charRange.first,
        charRange.last,
        Char.MIN_VALUE,
        Char.MAX_VALUE,
        Char.MIN_HIGH_SURROGATE,
        Char.MAX_HIGH_SURROGATE,
        Char.MIN_LOW_SURROGATE,
        Char.MAX_LOW_SURROGATE,
      )
      .flatMap { listOf(it, it + 1, it - 1) }
      .distinct()
      .filter { it in charRange }

  return Arb.char(charRange).withEdgecases(charEdgeCases)
}

private data class EntityIdSample(val string: String) {
  companion object {
    fun arb(): Arb<EntityIdSample> =
      Arb.string(10..10, Codepoint.alphanumeric()).map(::EntityIdSample)
  }
}
