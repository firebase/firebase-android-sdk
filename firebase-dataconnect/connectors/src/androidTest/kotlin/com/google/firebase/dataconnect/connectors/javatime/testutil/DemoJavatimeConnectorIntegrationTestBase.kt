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

////////////////////////////////////////////////////////////////////////////////
// WARNING: THIS FILE IS GENERATED FROM DemoConnectorIntegrationTestBase.kt
// DO NOT MODIFY THIS FILE BY HAND BECAUSE MANUAL CHANGES WILL GET OVERWRITTEN
// THE NEXT TIME THAT THIS FILE IS REGENERATED. TO REGENERATE THIS FILE, RUN:
// ./gradlew generateDataConnectTestingSources
////////////////////////////////////////////////////////////////////////////////
package com.google.firebase.dataconnect.connectors.javatime.testutil

import com.google.firebase.dataconnect.connectors.javatime.DemoJavatimeConnector
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import org.junit.Rule

////////////////////////////////////////////////////////////////////////////////
// WARNING: THIS FILE IS GENERATED FROM DemoConnectorIntegrationTestBase.kt
// DO NOT MODIFY THIS FILE BY HAND BECAUSE MANUAL CHANGES WILL GET OVERWRITTEN
// THE NEXT TIME THAT THIS FILE IS REGENERATED. TO REGENERATE THIS FILE, RUN:
// ./gradlew generateDataConnectTestingSources
////////////////////////////////////////////////////////////////////////////////
abstract class DemoJavatimeConnectorIntegrationTestBase : DataConnectIntegrationTestBase() {

  @get:Rule
  val connectorFactory = DemoJavatimeConnectorFactory(firebaseAppFactory, dataConnectFactory)

  val connector: DemoJavatimeConnector by lazy { connectorFactory.newInstance() }
}
