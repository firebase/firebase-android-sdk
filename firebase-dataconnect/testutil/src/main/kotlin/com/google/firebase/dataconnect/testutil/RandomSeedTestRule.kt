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

import io.kotest.property.RandomSource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit test rule that prints the seed of a [RandomSource] if the test fails, enabling replaying
 * the test with the same seed to investigate failures. If the [Lazy] is never initialized, then the
 * random seed is _not_ printed.
 */
class RandomSeedTestRule(val rs: Lazy<RandomSource>) : TestRule {

  constructor() : this(lazy(LazyThreadSafetyMode.PUBLICATION) { RandomSource.default() })

  override fun apply(base: Statement, description: Description) =
    object : Statement() {
      override fun evaluate() {
        val result = base.runCatching { evaluate() }
        result.onFailure {
          if (rs.isInitialized()) {
            println(
              "WARNING[55negqf33k]: RandomSeedTestRule: Test failed using " +
                "RandomSource with seed=${rs.value.seed}: ${description.displayName}"
            )
          }
        }
        result.getOrThrow()
      }
    }
}
