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

package com.google.firebase.dataconnect.core

import app.cash.turbine.test
import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.core.DataConnectLoggingImpl.StateImpl
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectLoggingImplUnitTest {

  @Test
  fun `level property, setting and getting`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      DataConnectLoggingImpl.level = newLevel
      DataConnectLoggingImpl.level shouldBe newLevel
    }
  }

  @Test
  fun `flow property, collecting`() = runTest {
    DataConnectLoggingImpl.flow.test {
      withClue("initial item") { awaitItem().level shouldBe DataConnectLoggingImpl.level }

      checkAll(Arb.enum<LogLevel>()) { newLevel ->
        val levelChanged = DataConnectLoggingImpl.level != newLevel
        DataConnectLoggingImpl.level = newLevel
        if (levelChanged) {
          awaitItem().level shouldBe newLevel
        }
      }
    }
  }

  @Test
  fun `state property, should be the current state`() = runTest {
    val arb = Arb.enum<LogLevel>()
    checkAll(arb, arb) { level1, level2 ->
      DataConnectLoggingImpl.level = level1
      withClue("state1") { DataConnectLoggingImpl.state.level shouldBe level1 }
      DataConnectLoggingImpl.level = level2
      withClue("state1") { DataConnectLoggingImpl.state.level shouldBe level2 }
    }
  }

  @Test
  fun `StateImpl level property, equals value given to constructor`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { level ->
      val state = StateImpl(level)
      state.level shouldBe level
    }
  }

  @Test
  fun `StateImpl restore(), should restore level`() = runTest {
    checkAll(Arb.stateImpl()) { state: StateImpl ->
      state.restore()
      DataConnectLoggingImpl.level shouldBe state.level
    }
  }

  @Test
  fun `StateImpl restore(), should restore on each invocation`() = runTest {
    checkAll(Arb.stateImpl(), Arb.enum<LogLevel>()) { state: StateImpl, newLevel ->
      state.restore()
      withClue("restore1") { DataConnectLoggingImpl.level shouldBe state.level }

      DataConnectLoggingImpl.level = newLevel
      state.restore()
      withClue("restore2") { DataConnectLoggingImpl.level shouldBe state.level }
    }
  }

  private companion object {

    fun Arb.Companion.stateImpl(): Arb<StateImpl> = Arb.enum<LogLevel>().map { StateImpl(it) }
  }
}
