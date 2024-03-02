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

import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.nextAlphanumericString
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.Companion.installAllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.Companion.installPersonSchema
import kotlin.random.Random
import kotlinx.coroutines.runBlocking

/**
 * A JUnit test rule that creates instances of [FirebaseDataConnect] for use during testing, and
 * closes them upon test completion.
 */
class TestDataConnectFactory :
  FactoryTestRule<FirebaseDataConnect, TestDataConnectFactory.Params>() {

  val personSchema: PersonSchema by lazy { runBlocking { installPersonSchema() } }
  val allTypesSchema: AllTypesSchema by lazy { runBlocking { installAllTypesSchema() } }

  fun newInstance(
    service: String? = null,
    location: String? = null,
    connector: String? = null
  ): FirebaseDataConnect =
    newInstance(Params(service = service, location = location, connector = connector))

  override fun createInstance(params: Params?): FirebaseDataConnect {
    val instanceId = Random.nextAlphanumericString()
    val serviceConfig =
      ConnectorConfig(
        connector = params?.connector ?: "TestConnector$instanceId",
        location = params?.location ?: "TestLocation$instanceId",
        service = params?.service ?: "TestService$instanceId",
      )
    return FirebaseDataConnect.getInstance(
      serviceConfig,
      DataConnectSettings(host = "10.0.2.2:9510", sslEnabled = false)
    )
  }

  override fun destroyInstance(instance: FirebaseDataConnect) {
    instance.close()
  }

  data class Params(
    val connector: String? = null,
    val location: String? = null,
    val service: String? = null,
  )
}
