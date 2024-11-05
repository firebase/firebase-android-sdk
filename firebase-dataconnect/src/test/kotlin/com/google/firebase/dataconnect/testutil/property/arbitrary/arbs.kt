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
@file:JvmName("InternalArbs")
@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.DataConnectError
import com.google.firebase.dataconnect.DataConnectError.PathSegment
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.OperationRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.FirebaseDataConnectInternal
import com.google.firebase.dataconnect.core.MutationRefImpl
import com.google.firebase.dataconnect.core.OperationRefImpl
import com.google.firebase.dataconnect.core.QueryRefImpl
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.mockk.mockk
import kotlin.reflect.KClass
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal fun DataConnectArb.dataConnectGrpcMetadata(
  dataConnectAuth: Arb<DataConnectAuth> = Arb.constant(mockk(relaxed = true)),
  dataConnectAppCheck: Arb<DataConnectAppCheck> = Arb.constant(mockk(relaxed = true)),
  connectorLocation: Arb<String> = connectorLocation(),
  kotlinVersion: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric()),
  androidVersion: Arb<Int> = Arb.int(0..100),
  dataConnectSdkVersion: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric()),
  grpcVersion: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric()),
  appId: Arb<String> = Arb.string(size = 8, Codepoint.alphanumeric()),
): Arb<DataConnectGrpcMetadata> = arbitrary {
  DataConnectGrpcMetadata(
    dataConnectAuth = dataConnectAuth.bind(),
    dataConnectAppCheck = dataConnectAppCheck.bind(),
    connectorLocation = connectorLocation.bind(),
    kotlinVersion = kotlinVersion.bind(),
    androidVersion = androidVersion.bind(),
    dataConnectSdkVersion = dataConnectSdkVersion.bind(),
    grpcVersion = grpcVersion.bind(),
    appId = appId.bind(),
    parentLogger = mockk(relaxed = true),
  )
}

internal fun DataConnectArb.pathSegmentType(
  boolean: Arb<Boolean> = Arb.boolean()
): Arb<KClass<out PathSegment>> = arbitrary {
  when (boolean.bind()) {
    true -> PathSegment.Field::class
    false -> PathSegment.ListIndex::class
  }
}

internal fun DataConnectArb.fieldPathSegment(
  string: Arb<String> = string()
): Arb<PathSegment.Field> = arbitrary { PathSegment.Field(string.bind()) }

internal fun DataConnectArb.listIndexPathSegment(
  int: Arb<Int> = Arb.int()
): Arb<PathSegment.ListIndex> = arbitrary { PathSegment.ListIndex(int.bind()) }

internal fun DataConnectArb.pathSegment(): Arb<PathSegment> =
  Arb.choice(fieldPathSegment(), listIndexPathSegment())

internal fun DataConnectArb.sourceLocation(
  line: Arb<Int> = Arb.int(),
  column: Arb<Int> = Arb.int()
): Arb<DataConnectError.SourceLocation> = arbitrary {
  DataConnectError.SourceLocation(line = line.bind(), column = column.bind())
}

internal fun DataConnectArb.dataConnectError(
  message: Arb<String> = string(),
  path: Arb<List<PathSegment>> = Arb.list(pathSegment(), 0..5),
  locations: Arb<List<DataConnectError.SourceLocation>> = Arb.list(sourceLocation(), 0..5)
): Arb<DataConnectError> = arbitrary {
  DataConnectError(message = message.bind(), path = path.bind(), locations = locations.bind())
}

internal fun DataConnectArb.operationResult(
  data: Arb<Struct?> = Arb.proto.struct().orNull(nullProbability = 0.2),
  errors: Arb<List<DataConnectError>> = Arb.list(dataConnectError(), 0..3),
) = arbitrary { DataConnectGrpcClient.OperationResult(data.bind(), errors.bind()) }

internal fun <Data, Variables> DataConnectArb.queryRefImpl(
  variables: Arb<Variables>,
  dataConnect: Arb<FirebaseDataConnectImpl> = arbitrary { mockk() },
  operationName: Arb<String> = string(),
  dataDeserializer: Arb<DeserializationStrategy<Data>> = arbitrary { mockk() },
  variablesSerializer: Arb<SerializationStrategy<Variables>> = arbitrary { mockk() },
  callerSdkType: Arb<CallerSdkType> = Arb.enum<CallerSdkType>(),
  variablesSerializersModule: Arb<SerializersModule?> = serializersModule(),
  dataSerializersModule: Arb<SerializersModule?> = serializersModule(),
): Arb<QueryRefImpl<Data, Variables>> = arbitrary {
  QueryRefImpl(
    dataConnect = dataConnect.bind(),
    operationName = operationName.bind(),
    variables = variables.bind(),
    dataDeserializer = dataDeserializer.bind(),
    variablesSerializer = variablesSerializer.bind(),
    callerSdkType = callerSdkType.bind(),
    variablesSerializersModule = variablesSerializersModule.bind(),
    dataSerializersModule = dataSerializersModule.bind(),
  )
}

internal inline fun <Data, reified Variables> DataConnectArb.queryRefImpl(
  constructorArguments: Arb<OperationRefConstructorArguments<Data, Variables>> =
    operationRefConstructorArguments(),
): Arb<QueryRefImpl<Data, Variables>> = arbitrary {
  val args = constructorArguments.bind()
  QueryRefImpl(
    dataConnect = args.dataConnect,
    operationName = args.operationName,
    variables = args.variables,
    dataDeserializer = args.dataDeserializer,
    variablesSerializer = args.variablesSerializer,
    callerSdkType = args.callerSdkType,
    variablesSerializersModule = args.variablesSerializersModule,
    dataSerializersModule = args.dataSerializersModule,
  )
}

internal fun <Data, Variables> DataConnectArb.mutationRefImpl(
  variables: Arb<Variables>,
  dataConnect: Arb<FirebaseDataConnectImpl> = arbitrary { mockk() },
  operationName: Arb<String> = string(),
  dataDeserializer: Arb<DeserializationStrategy<Data>> = arbitrary { mockk() },
  variablesSerializer: Arb<SerializationStrategy<Variables>> = arbitrary { mockk() },
  callerSdkType: Arb<CallerSdkType> = Arb.enum<CallerSdkType>(),
  variablesSerializersModule: Arb<SerializersModule?> = serializersModule(),
  dataSerializersModule: Arb<SerializersModule?> = serializersModule(),
): Arb<MutationRefImpl<Data, Variables>> = arbitrary {
  MutationRefImpl(
    dataConnect = dataConnect.bind(),
    operationName = operationName.bind(),
    variables = variables.bind(),
    dataDeserializer = dataDeserializer.bind(),
    variablesSerializer = variablesSerializer.bind(),
    callerSdkType = callerSdkType.bind(),
    variablesSerializersModule = variablesSerializersModule.bind(),
    dataSerializersModule = dataSerializersModule.bind(),
  )
}

internal inline fun <Data, reified Variables> DataConnectArb.mutationRefImpl(
  constructorArguments: Arb<OperationRefConstructorArguments<Data, Variables>> =
    operationRefConstructorArguments(),
): Arb<MutationRefImpl<Data, Variables>> = arbitrary {
  val args = constructorArguments.bind()
  MutationRefImpl(
    dataConnect = args.dataConnect,
    operationName = args.operationName,
    variables = args.variables,
    dataDeserializer = args.dataDeserializer,
    variablesSerializer = args.variablesSerializer,
    callerSdkType = args.callerSdkType,
    variablesSerializersModule = args.variablesSerializersModule,
    dataSerializersModule = args.dataSerializersModule,
  )
}

internal fun <Data, Variables> DataConnectArb.operationRefImpl(
  variables: Arb<Variables>,
  dataConnect: Arb<FirebaseDataConnectImpl> = arbitrary { mockk() },
  operationName: Arb<String> = string(),
  dataDeserializer: Arb<DeserializationStrategy<Data>> = arbitrary { mockk() },
  variablesSerializer: Arb<SerializationStrategy<Variables>> = arbitrary { mockk() },
  callerSdkType: Arb<CallerSdkType> = Arb.enum<CallerSdkType>(),
  variablesSerializersModule: Arb<SerializersModule?> = serializersModule(),
  dataSerializersModule: Arb<SerializersModule?> = serializersModule(),
): Arb<StubOperationRefImpl<Data, Variables>> = arbitrary {
  StubOperationRefImpl(
    dataConnect = dataConnect.bind(),
    operationName = operationName.bind(),
    variables = variables.bind(),
    dataDeserializer = dataDeserializer.bind(),
    variablesSerializer = variablesSerializer.bind(),
    callerSdkType = callerSdkType.bind(),
    variablesSerializersModule = variablesSerializersModule.bind(),
    dataSerializersModule = dataSerializersModule.bind(),
  )
}

internal inline fun <Data, reified Variables> DataConnectArb.operationRefImpl(
  constructorArguments: Arb<OperationRefConstructorArguments<Data, Variables>> =
    operationRefConstructorArguments(),
): Arb<OperationRefImpl<Data, Variables>> = arbitrary {
  val args = constructorArguments.bind()
  StubOperationRefImpl(
    dataConnect = args.dataConnect,
    operationName = args.operationName,
    variables = args.variables,
    dataDeserializer = args.dataDeserializer,
    variablesSerializer = args.variablesSerializer,
    callerSdkType = args.callerSdkType,
    variablesSerializersModule = args.variablesSerializersModule,
    dataSerializersModule = args.dataSerializersModule,
  )
}

internal data class OperationRefConstructorArguments<Data, Variables>(
  val dataConnect: FirebaseDataConnectInternal,
  val operationName: String,
  val variables: Variables,
  val dataDeserializer: DeserializationStrategy<Data>,
  val variablesSerializer: SerializationStrategy<Variables>,
  val callerSdkType: CallerSdkType,
  val dataSerializersModule: SerializersModule?,
  val variablesSerializersModule: SerializersModule?,
) {
  constructor(
    source: OperationRefImpl<Data, Variables>
  ) : this(
    dataConnect = source.dataConnect,
    operationName = source.operationName,
    variables = source.variables,
    dataDeserializer = source.dataDeserializer,
    variablesSerializer = source.variablesSerializer,
    callerSdkType = source.callerSdkType,
    dataSerializersModule = source.dataSerializersModule,
    variablesSerializersModule = source.variablesSerializersModule,
  )

  fun <NewVariables> withVariablesSerializer(
    variables: NewVariables,
    variablesSerializer: SerializationStrategy<NewVariables>,
  ): OperationRefConstructorArguments<Data, NewVariables> =
    OperationRefConstructorArguments(
      dataConnect = dataConnect,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
    )

  fun <NewData> withDataDeserializer(
    dataDeserializer: DeserializationStrategy<NewData>,
  ): OperationRefConstructorArguments<NewData, Variables> =
    OperationRefConstructorArguments(
      dataConnect = dataConnect,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
    )
}

internal fun OperationRef<*, *>.shouldHavePropertiesEqualTo(
  expected: OperationRefConstructorArguments<*, *>
) {
  assertSoftly {
    withClue("dataConnect") { dataConnect shouldBe expected.dataConnect }
    withClue("operationName") { operationName shouldBe expected.operationName }
    withClue("variables") { variables shouldBe expected.variables }
    withClue("dataDeserializer") { dataDeserializer shouldBe expected.dataDeserializer }
    withClue("variablesSerializer") { variablesSerializer shouldBe expected.variablesSerializer }
    withClue("callerSdkType") { callerSdkType shouldBe expected.callerSdkType }
    withClue("dataSerializersModule") {
      dataSerializersModule shouldBe expected.dataSerializersModule
    }
    withClue("variablesSerializersModule") {
      variablesSerializersModule shouldBe expected.variablesSerializersModule
    }
  }
}

internal fun <Data, Variables> OperationRef<*, *>.shouldHavePropertiesEqualTo(
  expected: OperationRefImpl<Data, Variables>
) = shouldHavePropertiesEqualTo(OperationRefConstructorArguments(expected))

internal inline fun <Data, reified Variables> DataConnectArb.operationRefConstructorArguments(
  dataConnect: Arb<FirebaseDataConnectInternal> = Arb.mock(),
  operationName: Arb<String> = string(),
  variables: Arb<Variables> = Arb.mock(),
  dataDeserializer: Arb<DeserializationStrategy<Data>> = Arb.mock(),
  variablesSerializer: Arb<SerializationStrategy<Variables>> = Arb.mock(),
  callerSdkType: Arb<CallerSdkType> = Arb.enum(),
  dataSerializersModule: Arb<SerializersModule?> = serializersModule(),
  variablesSerializersModule: Arb<SerializersModule?> = serializersModule(),
): Arb<OperationRefConstructorArguments<Data, Variables>> = arbitrary {
  OperationRefConstructorArguments(
    dataConnect = dataConnect.bind(),
    operationName = operationName.bind(),
    variables = variables.bind(),
    dataDeserializer = dataDeserializer.bind(),
    variablesSerializer = variablesSerializer.bind(),
    callerSdkType = callerSdkType.bind(),
    dataSerializersModule = dataSerializersModule.bind(),
    variablesSerializersModule = variablesSerializersModule.bind(),
  )
}
