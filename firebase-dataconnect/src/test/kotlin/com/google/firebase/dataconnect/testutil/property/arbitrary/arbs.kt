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
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.MutationRefImpl
import com.google.firebase.dataconnect.core.QueryRefImpl
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl
import com.google.protobuf.Struct
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
