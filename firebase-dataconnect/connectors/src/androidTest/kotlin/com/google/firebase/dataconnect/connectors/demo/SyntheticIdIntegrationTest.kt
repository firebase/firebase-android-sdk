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

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyntheticIdIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun syntheticIdShouldBeGeneratedIfNoExplicitlySpecifiedInGQL() = runTest {
    val value = Arb.dataConnect.string().next(rs)

    val id = connector.insertSyntheticId.execute(value).data.key.id

    val queryResult = connector.getSyntheticIdById.execute(id)
    queryResult.data.syntheticId shouldBe
      GetSyntheticIdByIdQuery.Data.SyntheticId(id = id, value = value)
  }
}
