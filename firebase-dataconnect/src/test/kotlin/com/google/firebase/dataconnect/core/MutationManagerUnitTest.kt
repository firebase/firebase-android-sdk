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

@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalKotest::class)

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectOperationException
import com.google.firebase.dataconnect.DataConnectUntypedVariables
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectAppCheck
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectAuth
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.property.arbitrary.mutationRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.requestIdGenerator
import com.google.firebase.dataconnect.testutil.property.arbitrary.scalarValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Test

class MutationManagerUnitTest {

  @Test
  fun `execute() handles DataConnectUntypedVariables`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl<Unit, Unit>(),
      variablesStructArb(),
      executeMutationResponseArb()
    ) { baseMutationRef, variablesStruct, executeMutationResponse ->
      val requestSlot: CapturingSlot<ExecuteMutationRequest> = slot()
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        coEvery { executeMutation(any(), capture(requestSlot), any(), any(), any()) } returns
          executeMutationResponse
      }
      val mutationManager = newMutationManager(dataConnectGrpcRPCs)
      val untypedVariables = DataConnectUntypedVariables(variablesStruct.toMap())
      val mutationRef =
        baseMutationRef
          .withVariablesSerializer(untypedVariables, DataConnectUntypedVariables.Serializer)
          .withDataDeserializer(serializer<Unit>())

      mutationManager.execute(mutationRef)

      requestSlot.captured.variables shouldBe variablesStruct
    }
  }

  @Test
  fun `execute() throws when response data is null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl<Unit, Unit>()) { baseMutationRef ->
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk {
        coEvery { executeMutation(any(), any(), any(), any(), any()) } returns
          ExecuteMutationResponse.getDefaultInstance()
      }
      val mutationManager = newMutationManager(dataConnectGrpcRPCs)
      val mutationRef =
        baseMutationRef
          .withVariablesSerializer(Unit, serializer())
          .withDataDeserializer(serializer<Unit>())

      val exception =
        shouldThrow<DataConnectOperationException> { mutationManager.execute(mutationRef) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "no data was included in the response"
        exception.response.asClue {
          withClue("data") { it.data.shouldBeNull() }
          withClue("rawData") { it.rawData.shouldBeNull() }
          it.errors.shouldBeEmpty()
        }
      }
    }
  }

  // TODO: Add more test coverage, like the coverage provided by QueryManager

}

private val propTestConfig =
  PropTestConfig(
    iterations = 100,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun PropertyContext.newMutationManager(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs
): MutationManager =
  mutationManagerArb(dataConnectGrpcRPCs = Arb.constant(dataConnectGrpcRPCs)).bind()

private fun mutationManagerArb(
  connectorResourceName: Arb<String> = Arb.dataConnect.connectorResourceName(),
  dataConnectGrpcRPCs: Arb<DataConnectGrpcRPCs> = Arb.mock(),
  dataConnectAuth: Arb<DataConnectAuth> = Arb.dataConnect.dataConnectAuth(),
  dataConnectAppCheck: Arb<DataConnectAppCheck> = Arb.dataConnect.dataConnectAppCheck(),
  cpuDispatcher: Arb<CoroutineDispatcher> = Arb.constant(Dispatchers.Default),
  requestIdGenerator: Arb<RequestIdGenerator> = Arb.dataConnect.requestIdGenerator(),
  logger: Arb<Logger> = Arb.dataConnect.string().map { newMockLogger("logger_$it") },
): Arb<MutationManager> =
  Arb.bind(
    connectorResourceName,
    dataConnectGrpcRPCs,
    dataConnectAuth,
    dataConnectAppCheck,
    cpuDispatcher,
    requestIdGenerator,
    logger,
    ::MutationManager,
  )

private fun executeMutationResponseArb(
  struct: Arb<Struct> = Arb.proto.struct().map { it.struct }
): Arb<ExecuteMutationResponse> =
  struct.map { ExecuteMutationResponse.newBuilder().setData(it).build() }

private fun variablesStructArb(): Arb<Struct> =
  Arb.proto.struct(scalarValue = Arb.proto.scalarValue(exclude = Value.KindCase.KIND_NOT_SET)).map {
    it.struct
  }
