// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect

import android.util.Log
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import google.internal.firebase.firemat.v0.DataServiceGrpc
import google.internal.firebase.firemat.v0.DataServiceOuterClass.ExecuteQueryRequest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class FirebaseDataConnectTest {

  @Test
  fun helloWorld() {
    val logger = mock(Logger::class.java)
    val managedChannel =
      createManagedChannel(
        getApplicationContext(),
        "10.0.2.2:9510",
        GrpcConnectionEncryption.PLAINTEXT,
        logger
      )

    val stub = DataServiceGrpc.newBlockingStub(managedChannel)
    val projectId = "ZzyzxTestProject"
    val location = "ZzyzxTestLocation"

    val request =
      ExecuteQueryRequest.newBuilder().run {
        name =
          "projects/${projectId}/locations/${location}/services/s/operationSets/crud/revisions/r"
        operationName = "listPosts"
        build()
      }

    Log.w("zzyzx", "Sending request: ${request}")
    val response = stub.executeQuery(request)
    Log.w("zzyzx", "Got response: ${response}")
  }
}
