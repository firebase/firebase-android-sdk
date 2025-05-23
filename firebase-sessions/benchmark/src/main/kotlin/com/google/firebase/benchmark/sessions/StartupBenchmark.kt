/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.benchmark.sessions

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun startup() =
    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(StartupTimingMetric()),
      iterations = 5,
      startupMode = StartupMode.COLD,
    ) {
      pressHome()
      startActivityAndWait()
    }

  @Test
  fun startup_clearAppData() =
    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(StartupTimingMetric()),
      iterations = 5,
      startupMode = StartupMode.COLD,
    ) {
      pressHome()
      InstrumentationRegistry.getInstrumentation()
        .uiAutomation
        .executeShellCommand("pm clear $PACKAGE_NAME")
        .close()
      startActivityAndWait()
    }

  private companion object {
    const val PACKAGE_NAME = "com.google.firebase.testing.sessions"
  }
}
