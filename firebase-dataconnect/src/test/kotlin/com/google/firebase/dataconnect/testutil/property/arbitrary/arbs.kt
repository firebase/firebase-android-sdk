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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.mockk.mockk

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
