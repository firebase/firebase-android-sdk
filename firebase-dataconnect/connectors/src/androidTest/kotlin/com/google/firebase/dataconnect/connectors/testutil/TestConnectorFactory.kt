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

package com.google.firebase.dataconnect.connectors.testutil

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.generated.*
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.FactoryTestRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory

/**
 * A JUnit test rule that creates instances of a connector for use during testing, and closes their
 * underlying [FirebaseDataConnect] instances upon test completion.
 */
abstract class TestConnectorFactory<T : GeneratedConnector>(
  private val firebaseAppFactory: TestFirebaseAppFactory,
  private val dataConnectFactory: TestDataConnectFactory
) : FactoryTestRule<T, Nothing>() {

  abstract fun createConnector(firebaseApp: FirebaseApp, settings: DataConnectSettings): T

  override fun createInstance(params: Nothing?): T {
    val firebaseApp = firebaseAppFactory.newInstance()

    val dataConnectSettings = DataConnectBackend.fromInstrumentationArguments().dataConnectSettings
    val connector = createConnector(firebaseApp, dataConnectSettings)

    // Get the instance of `FirebaseDataConnect` from the `TestDataConnectFactory` so that it will
    // register the instance and set any settings required for talking to the backend.
    val dataConnect = dataConnectFactory.newInstance(firebaseApp, connector.dataConnect.config)

    check(dataConnect === connector.dataConnect) {
      "DemoConnector.getInstance() returned an instance " +
        "associated with FirebaseDataConnect instance ${connector.dataConnect}, " +
        "but expected it to be associated with instance $dataConnect"
    }

    return connector
  }

  override fun destroyInstance(instance: T) {
    // Do nothing in `destroyInstance()` since `TestDataConnectFactory` will do all the work of
    // closing the `FirebaseDataConnect` instance.
  }
}
