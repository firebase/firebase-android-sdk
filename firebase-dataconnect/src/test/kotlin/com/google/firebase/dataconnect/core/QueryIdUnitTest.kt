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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.testutil.deserializeStructVerbatim
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.serializeStructVerbatim
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.NullOutputStream
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.checkAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryIdUnitTest {

  @Test
  fun `calculateQueryId() returns the expected value`() = runTest {
    val stringArb = Arb.dataConnect.string()
    checkAll(propTestConfig, stringArb, Arb.proto.struct(key = stringArb)) {
      operationName,
      variables ->
      val actual = calculateQueryId(operationName, variables.struct)
      val expected = calculateExpectedSha512(operationName, variables.struct)
      actual.bytes.peek() shouldBe expected
    }
  }

  @Test
  fun `calculateQueryId() with precomputed expected values`() = runTest {
    val testCases = loadTestCases(this::class.java.classLoader!!)
    testCases.forEach { (operationName, variables, queryId) ->
      val actual = calculateQueryId(operationName, variables)
      actual shouldBe queryId
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

/**
 * Calculates the Sha512 hash used as the "Query ID" for a query with the given operation name and
 * variables.
 *
 * This method is essentially a copy of the original algorithm used to calculate Query IDs. Since
 * Query IDs are persisted into the database the algorithm MUST NEVER change. By copying the
 * algorithm here into the test we can assure that any refactorings of the algorithm continue to
 * produce the same values.
 */
private fun calculateExpectedSha512(operationName: String, variables: Struct): ByteArray {
  val digest = MessageDigest.getInstance("SHA-512")
  val out = DataOutputStream(DigestOutputStream(NullOutputStream, digest))
  out.writeUTF(operationName)
  calculateExpectedSha512(Value.newBuilder().setStructValue(variables).build(), out)
  return digest.digest()
}

/** Helper method for [calculateExpectedSha512] that performs the recursive SHA512 calculation. */
private fun calculateExpectedSha512(value: Value, out: DataOutputStream) {
  val kind = value.kindCase
  out.writeInt(kind.ordinal)

  when (kind) {
    KindCase.NULL_VALUE,
    KindCase.KIND_NOT_SET -> {
      /* nothing to write for null or kind-not-set */
    }
    KindCase.BOOL_VALUE -> out.writeBoolean(value.boolValue)
    KindCase.NUMBER_VALUE -> out.writeDouble(value.numberValue)
    KindCase.STRING_VALUE -> out.writeUTF(value.stringValue)
    KindCase.LIST_VALUE ->
      value.listValue.valuesList.forEachIndexed { index, elementValue ->
        out.writeInt(index)
        calculateExpectedSha512(elementValue, out)
      }
    KindCase.STRUCT_VALUE ->
      value.structValue.fieldsMap.entries
        .sortedBy { (key, _) -> key }
        .forEach { (key, elementValue) ->
          out.writeUTF(key)
          calculateExpectedSha512(elementValue, out)
        }
  }

  out.writeInt(kind.ordinal)
}

/** Stores a "test case"; that is, an operation name and variables and the expected Query ID. */
private data class TestCase(
  val operationName: String,
  val variables: Struct,
  val queryId: QueryId
) {

  @Suppress("unused")
  fun encode(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    DataOutputStream(byteArrayOutputStream).use { dataOutputStream -> encodeTo(dataOutputStream) }
    return byteArrayOutputStream.toByteArray()
  }

  private fun encodeTo(out: DataOutputStream) {
    out.writeInt(operationName.length)
    out.writeChars(operationName)
    val variablesBytes = serializeStructVerbatim(variables)
    out.writeInt(variablesBytes.size)
    out.write(variablesBytes)
    out.writeInt(queryId.bytes.size)
    out.write(queryId.bytes.peek())
  }

  companion object {

    fun decode(bytes: ByteArray): TestCase {
      val dataInputStream = DataInputStream(ByteArrayInputStream(bytes))
      return decodeFrom(dataInputStream)
    }

    private fun decodeFrom(stream: DataInputStream): TestCase {
      val operationName = run {
        val charCount = stream.readInt()
        stream.readChars(charCount)
      }
      val variables = run {
        val byteCount = stream.readInt()
        val bytes = ByteArray(byteCount)
        stream.readFully(bytes)
        deserializeStructVerbatim(bytes)
      }
      val queryId = run {
        val byteCount = stream.readInt()
        val bytes = ByteArray(byteCount)
        stream.readFully(bytes)
        QueryId(ImmutableByteArray.adopt(bytes))
      }
      return TestCase(operationName, variables, queryId)
    }

    private fun DataInputStream.readChars(charCount: Int): String = buildString {
      repeat(charCount) { append(readChar()) }
    }
  }
}

/** Loads the persisted [TestCase] objects. */
private fun loadTestCases(classLoader: ClassLoader): List<TestCase> {
  classLoader
    .getResourceAsStream("com/google/firebase/dataconnect/core/QueryIdUnitTestTestCases.dat.gz")
    .use { resourceStream ->
      val gzipInputStream = GZIPInputStream(resourceStream)
      val dataInputStream = DataInputStream(gzipInputStream)
      val testCaseCount = dataInputStream.readInt()
      return List(testCaseCount) {
        val testCaseByteCount = dataInputStream.readInt()
        val testCaseBytes = ByteArray(testCaseByteCount)
        dataInputStream.readFully(testCaseBytes)
        TestCase.decode(testCaseBytes)
      }
    }
}
