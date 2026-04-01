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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.core

import app.cash.turbine.test
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.OperationRef
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.copy
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs.DataConnectStream
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.schemas.PastaConnector
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.protobuf.Struct
import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbs.firstName
import io.kotest.property.checkAll
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectGrpcRPCsIntegrationTest : DataConnectIntegrationTestBase() {

  @Test
  fun streamTest() =
    runTest(timeout = Duration.INFINITE) {
      val stringArb = Arb.firstName().map { it.name }
      val callerSdkTypeArb = Arb.enum<FirebaseDataConnect.CallerSdkType>()
      val requestIdArb = Arb.dataConnect.requestId()
      val connector = newConnector()
      checkAll(propTestConfig, stringArb.pair()) { (name1, name2) ->
        val key = connector.insert(name1)
        val dataConnectGrpcRPCs = connector.dataConnectGrpcRPCs
        val queryRef = connector.refs.getByKey(key)

        val stream =
          dataConnectGrpcRPCs.connect(
            streamId = "con" + stringArb.bind(),
            authToken = null,
            appCheckToken = null,
            callerSdkType = callerSdkTypeArb.bind(),
            name = connector.calculateRequestName(),
          )

        val flow: Flow<DataConnectStream.Response> =
          stream.subscribe(
            requestId = requestIdArb.next(randomSource()),
            operationName = queryRef.operationName,
            variables = queryRef.encodeVariables(),
          )

        flow.test {
          withClue("awaitItem1") { awaitItem().shouldHaveName(name1, queryRef) }

          connector.update(key, name2)

          withClue("awaitItem2") { awaitItem().shouldHaveName(name2, queryRef) }
        }
      }
    }

  private fun newConnector(): PastaConnector {
    val connectorConfig = testConnectorConfig.copy(connector = PastaConnector.CONNECTOR_NAME)
    val dataConnect = dataConnectFactory.newInstance(connectorConfig)
    return PastaConnector(dataConnect)
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 5,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private val PastaConnector.dataConnectGrpcRPCs: DataConnectGrpcRPCs
  get() = (this.dataConnect as FirebaseDataConnectImpl).dataConnectGrpcRPCsForTesting

private fun <T> Struct.decodeAsData(ref: OperationRef<T, *>): T =
  decodeFromStruct(this, ref.dataDeserializer, ref.dataSerializersModule)

private fun <T> QueryRef<*, T>.encodeVariables(): Struct =
  encodeToStruct(this.variables, variablesSerializer, variablesSerializersModule)

private fun DataConnectStream.Response.shouldHaveName(
  name: String,
  ref: OperationRef<PastaConnector.Data.Get, *>
) {
  val struct: Struct = withClue("data") { data.shouldNotBeNull() }

  val data: PastaConnector.Data.Get = withClue("decodeAsData") { struct.decodeAsData(ref) }

  data.asClue { it.item.shouldNotBeNull().name shouldBe name }
}

private fun PastaConnector.calculateRequestName(): String = dataConnect.calculateRequestName()

private fun FirebaseDataConnect.calculateRequestName(): String =
  calculateRequestName(app.options.projectId!!, config)
