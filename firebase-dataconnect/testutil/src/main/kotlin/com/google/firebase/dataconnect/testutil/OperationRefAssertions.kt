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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.OperationRef
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

fun OperationRef<*, *>.shouldHaveSamePropertyInstancesAs(
  other: OperationRef<*, *>,
  dataConnect: FirebaseDataConnect = other.dataConnect,
  operationName: String = other.operationName,
  variables: Any? = other.variables,
  dataDeserializer: DeserializationStrategy<*> = other.dataDeserializer,
  variablesSerializer: SerializationStrategy<*> = other.variablesSerializer,
  callerSdkType: CallerSdkType = other.callerSdkType,
  dataSerializersModule: SerializersModule? = other.dataSerializersModule,
  variablesSerializersModule: SerializersModule? = other.variablesSerializersModule,
) =
  shouldHavePropertyInstances(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    callerSdkType = callerSdkType,
    dataSerializersModule = dataSerializersModule,
    variablesSerializersModule = variablesSerializersModule,
  )

fun OperationRef<*, *>.shouldHavePropertyInstances(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  variables: Any?,
  dataDeserializer: DeserializationStrategy<*>,
  variablesSerializer: SerializationStrategy<*>,
  callerSdkType: CallerSdkType,
  dataSerializersModule: SerializersModule?,
  variablesSerializersModule: SerializersModule?,
) {
  assertSoftly {
    withClue("dataConnect") { this.dataConnect shouldBeSameInstanceAs dataConnect }
    withClue("operationName") { this.operationName shouldBeSameInstanceAs operationName }
    withClue("variables") { this.variables shouldBeSameInstanceAs variables }
    withClue("dataDeserializer") { this.dataDeserializer shouldBeSameInstanceAs dataDeserializer }
    withClue("variablesSerializer") {
      this.variablesSerializer shouldBeSameInstanceAs variablesSerializer
    }
    withClue("callerSdkType") { this.callerSdkType shouldBeSameInstanceAs callerSdkType }

    withClue("dataSerializersModule") {
      if (dataSerializersModule === null) {
        this.dataSerializersModule.shouldBeNull()
      } else {
        this.dataSerializersModule shouldBeSameInstanceAs dataSerializersModule
      }
    }

    withClue("variablesSerializersModule") {
      if (variablesSerializersModule === null) {
        this.variablesSerializersModule.shouldBeNull()
      } else {
        this.variablesSerializersModule shouldBeSameInstanceAs variablesSerializersModule
      }
    }
  }
}
