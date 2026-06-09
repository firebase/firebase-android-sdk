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
package com.google.firebase.dataconnect

import app.cash.turbine.test
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.DataConnectBidiConnectStream
import com.google.firebase.dataconnect.core.DataConnectBidiConnectStream.ExecuteResponse
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector.GetStringByKeyQuery
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Test

/** Integration tests for the [DataConnectGrpcRPCs.connect] method and its return value. */
class DataConnectGrpcRPCsConnectIntegrationTest : DataConnectIntegrationTestBase() {

  private val requestIdArb = Arb.dataConnect.requestId()
  private val streamIdArb = Arb.dataConnect.streamId()
  private val callerSdkTypeArb = Arb.enum<CallerSdkType>()
  private val keyArb = Arb.uuid().map(RealtimeConnector::Key)
  private val nameArb = Arb.string(size = 4, Codepoint.az()).map { "name_$it" }

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun subscribeEmitsInitialResult() = runTest {
    val connector = RealtimeConnector.getInstance(dataConnectFactory)
    val dataConnectGrpcRPCs = connector.dataConnectGrpcRPCs
    val key = keyArb.sample()

    val stream = dataConnectGrpcRPCs.connect()
    val executeResponseFlow: Flow<ExecuteResponse> =
      stream.subscribe(
        requestId = requestIdArb.sample(),
        operationName = GetStringByKeyQuery.OPERATION_NAME,
        variables = key.encodeToGetStringByKeyQueryVariables(),
      )

    executeResponseFlow.test {
      awaitItem().shouldBeGetStringByKeyQueryResponse(expectedName = null)
    }
  }

  @Test
  fun subscribeEmitsUpdatedResult() = runTest {
    val connector = RealtimeConnector.getInstance(dataConnectFactory)
    val dataConnectGrpcRPCs = connector.dataConnectGrpcRPCs
    val (name1, name2) = nameArb.pair().sample()
    val key = connector.insertString(name = name1)

    val stream = dataConnectGrpcRPCs.connect()
    val executeResponseFlow: Flow<ExecuteResponse> =
      stream.subscribe(
        requestId = requestIdArb.sample(),
        operationName = GetStringByKeyQuery.OPERATION_NAME,
        variables = key.encodeToGetStringByKeyQueryVariables(),
      )

    executeResponseFlow.test {
      awaitItem().shouldBeGetStringByKeyQueryResponse(expectedName = name1)
      connector.updateString(key, name = name2)
      awaitItem().shouldBeGetStringByKeyQueryResponse(expectedName = name2)
      connector.deleteString(key)
      awaitItem().shouldBeGetStringByKeyQueryResponse(expectedName = null)
    }
  }

  private suspend fun DataConnectGrpcRPCs.connect(): DataConnectBidiConnectStream =
    connect(
      streamId = streamIdArb.sample(),
      callerSdkType = callerSdkTypeArb.sample(),
      dataConnectAuth = mockk(relaxed = true) { coEvery { getToken(any()) } returns null },
      dataConnectAppCheck = mockk(relaxed = true) { coEvery { getToken(any()) } returns null },
      idStringGenerator = IdStringGenerator(Random.Default),
    )
}

private fun GetStringByKeyQuery.Variables.encodeToStruct(): Struct =
  encodeToStruct(this, serializer(), null)

private fun RealtimeConnector.Key.encodeToGetStringByKeyQueryVariables(): Struct =
  GetStringByKeyQuery.Variables(this).encodeToStruct()

private fun Struct.decodeAsGetStringByKeyQueryData(): GetStringByKeyQuery.Data =
  decodeFromStruct(this, serializer(), null)

private fun ExecuteResponse.shouldBeGetStringByKeyQueryResponse(
  expectedName: String?,
  expectedErrors: List<GraphqlErrorProto> = emptyList(),
) {
  assertSoftly {
    withClue("data") { data.shouldNotBeNull().shouldBeGetStringByKeyQueryData(expectedName) }
    withClue("errors") { errors shouldContainExactlyInAnyOrder expectedErrors }
  }
}

private fun Struct.shouldBeGetStringByKeyQueryData(expectedName: String?) {
  val data: GetStringByKeyQuery.Data = decodeAsGetStringByKeyQueryData()
  if (expectedName === null) {
    data.item.shouldBeNull()
  } else {
    data.item.shouldNotBeNull() shouldBe GetStringByKeyQuery.Data.Item(expectedName)
  }
}
