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

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.*
import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import kotlinx.coroutines.runBlocking

/**
 * A JUnit test rule that creates instances of [FirebaseDataConnect] for use during testing, and
 * closes them upon test completion.
 */
class TestDataConnectFactory(val firebaseAppFactory: TestFirebaseAppFactory) :
  FactoryTestRule<FirebaseDataConnect, TestDataConnectFactory.Params>() {

  fun newInstance(config: ConnectorConfig): FirebaseDataConnect =
    config.run {
      newInstance(Params(connector = connector, location = location, serviceId = serviceId))
    }

  fun newInstance(backend: DataConnectBackend): FirebaseDataConnect =
    newInstance(Params(backend = backend))

  fun newInstance(firebaseApp: FirebaseApp, config: ConnectorConfig): FirebaseDataConnect =
    newInstance(
      Params(
        firebaseApp = firebaseApp,
        connector = config.connector,
        location = config.location,
        serviceId = config.serviceId
      )
    )

  override fun createInstance(params: Params?): FirebaseDataConnect {
    val instanceId = Random.nextAlphanumericString(length = 10)

    val firebaseApp = params?.firebaseApp ?: firebaseAppFactory.newInstance()

    val connectorConfig =
      ConnectorConfig(
        connector = params?.connector ?: "TestConnector$instanceId",
        location = params?.location ?: "TestLocation$instanceId",
        serviceId = params?.serviceId ?: "TestService$instanceId",
      )

    val backend = params?.backend ?: DataConnectBackend.fromInstrumentationArguments()
    return backend.getDataConnect(firebaseApp, connectorConfig)
  }

  override fun destroyInstance(instance: FirebaseDataConnect) {
    runBlocking { instance.suspendingClose() }
  }

  data class Params(
    val firebaseApp: FirebaseApp? = null,
    val connector: String? = null,
    val location: String? = null,
    val serviceId: String? = null,
    val backend: DataConnectBackend? = null,
  )
}
