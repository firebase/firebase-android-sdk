/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.testutil.schemas

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.kotest.property.arbs.firstName
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RealtimeConnectorIntegrationTest : DataConnectIntegrationTestBase() {

  @Test
  fun realtimeConnectorBasicFunctionalityTest() = runTest {
    val connector = RealtimeConnector.getInstance(dataConnectFactory)
    val (name1, name2) = Arb.firstName().map { it.name }.pair().sample(rs).value

    val key = connector.insertString(name1)
    connector.getString(key).shouldNotBeNull().name shouldBe name1
    connector.updateString(key, name2)
    connector.getString(key).shouldNotBeNull().name shouldBe name2
    connector.deleteString(key)
    connector.getString(key).shouldBeNull()
  }
}
