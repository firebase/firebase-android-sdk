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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.FirebaseDataConnect

/**
 * A JUnit test rule that creates instances of [FirebaseDataConnect] for use during testing, and
 * closes them upon test completion.
 */
class TestDataConnectFactory :
  FactoryTestRule<FirebaseDataConnect, TestDataConnectFactory.Params>() {

  fun newInstance(location: String? = null, service: String? = null): FirebaseDataConnect =
    newInstance(Params(location = location, service = service))

  override fun createInstance(instanceId: String, params: Params?) =
    FirebaseDataConnect.getInstance(
      location = params?.location ?: "TestLocation$instanceId",
      service = params?.service ?: "TestService$instanceId"
    ) {
      connectToEmulator()
    }

  override fun destroyInstance(instance: FirebaseDataConnect) {
    instance.close()
  }

  data class Params(val location: String? = null, val service: String? = null)
}
