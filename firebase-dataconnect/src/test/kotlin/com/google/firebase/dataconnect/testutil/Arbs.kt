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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.DataConnectError
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.MutationRefImpl
import com.google.firebase.dataconnect.core.QueryRefImpl
import com.google.firebase.dataconnect.util.toStructProto
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.string
import io.kotest.property.arbs.firstName
import io.mockk.mockk

internal fun Arb.Companion.dataConnectErrorSourceLocation(): Arb<DataConnectError.SourceLocation> =
  arbitrary {
    val line = Arb.int(1..9999).bind()
    val column = Arb.int(1..9999).bind()
    DataConnectError.SourceLocation(line = line, column = column)
  }

internal fun Arb.Companion.dataConnectError(): Arb<DataConnectError> = arbitrary {
  val message = "sx7s673h4n_" + Arb.string(20, codepoints = Codepoint.alphanumeric()).bind()
  val numPathSegments = Arb.int(1..3).bind()
  val path = List(numPathSegments) { Arb.dataConnectErrorPathSegment().bind() }
  val numLocations = Arb.int(0..3).bind()
  val locations = List(numLocations) { Arb.dataConnectErrorSourceLocation().bind() }
  DataConnectError(
    message = message,
    path = path,
    locations = locations,
  )
}

internal fun Arb.Companion.dataConnectErrorPathSegmentField():
  Arb<DataConnectError.PathSegment.Field> = arbitrary {
  DataConnectError.PathSegment.Field(Arb.firstName().bind().name)
}

internal fun Arb.Companion.dataConnectErrorPathSegmentListIndex():
  Arb<DataConnectError.PathSegment.ListIndex> = arbitrary {
  DataConnectError.PathSegment.ListIndex(Arb.positiveInt().bind())
}

internal fun Arb.Companion.dataConnectErrorPathSegment(): Arb<DataConnectError.PathSegment> =
  arbitrary {
    if (Arb.boolean().bind()) {
      Arb.dataConnectErrorPathSegmentField().bind()
    } else {
      Arb.dataConnectErrorPathSegmentListIndex().bind()
    }
  }

internal fun Arb.Companion.operationResult() = arbitrary {
  val data = Arb.anyMapScalar().orNull(nullProbability = 0.1).bind()?.toStructProto()
  val numErrors = Arb.int(0..3).bind()
  val errors = List(numErrors) { Arb.dataConnectError().bind() }
  DataConnectGrpcClient.OperationResult(data, errors)
}

internal fun <Data, Variables> Arb.Companion.queryRefImpl(
  variablesArb: Arb<Variables>
): Arb<QueryRefImpl<Data, Variables>> = arbitrary {
  val stringArb = Arb.string(6, codepoints = Codepoint.alphanumeric())
  QueryRefImpl(
    dataConnect = mockk(stringArb.bind()),
    operationName = stringArb.bind(),
    variables = variablesArb.bind(),
    dataDeserializer = mockk(stringArb.bind()),
    variablesSerializer = mockk(stringArb.bind()),
    isFromGeneratedSdk = boolean().bind(),
    variablesSerializersModule = mockk(stringArb.bind()),
    dataSerializersModule = mockk(stringArb.bind()),
  )
}

internal fun <Data, Variables> Arb.Companion.mutationRefImpl(
  variablesArb: Arb<Variables>
): Arb<MutationRefImpl<Data, Variables>> = arbitrary {
  val stringArb = Arb.string(6, codepoints = Codepoint.alphanumeric())
  MutationRefImpl(
    dataConnect = mockk(stringArb.bind()),
    operationName = stringArb.bind(),
    variables = variablesArb.bind(),
    dataDeserializer = mockk(stringArb.bind()),
    variablesSerializer = mockk(stringArb.bind()),
    isFromGeneratedSdk = boolean().bind(),
    variablesSerializersModule = mockk(stringArb.bind()),
    dataSerializersModule = mockk(stringArb.bind()),
  )
}

internal fun <Data, Variables> Arb.Companion.operationRefImpl(
  variablesArb: Arb<Variables>
): Arb<StubOperationRefImpl<Data, Variables>> = arbitrary {
  val stringArb = Arb.string(6, codepoints = Codepoint.alphanumeric())
  StubOperationRefImpl(
    dataConnect = mockk(stringArb.bind()),
    operationName = stringArb.bind(),
    variables = variablesArb.bind(),
    dataDeserializer = mockk(stringArb.bind()),
    variablesSerializer = mockk(stringArb.bind()),
    dataSerializersModule = mockk(stringArb.bind()),
    variablesSerializersModule = mockk(stringArb.bind()),
  )
}
