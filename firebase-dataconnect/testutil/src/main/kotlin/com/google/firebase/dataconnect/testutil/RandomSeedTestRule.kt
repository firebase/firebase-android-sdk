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
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit test rule that prints the seed of a [RandomSource] if the test fails, enabling replaying
 * the test with the same seed to investigate failures. If the [Lazy] is never initialized, then the
 * random seed is _not_ printed.
 *
 * @property rs A [Lazy] instance that produces the [RandomSource] to be used in the test.
 */
class RandomSeedTestRule(val rs: Lazy<RandomSource>) : TestRule {

  /**
   * Creates a [RandomSeedTestRule] with a factory function for the [RandomSource].
   *
   * @param createRandomSource A factory function invoked lazily to create the [RandomSource].
   */
  constructor(
    createRandomSource: () -> RandomSource
  ) : this(lazy(LazyThreadSafetyMode.PUBLICATION, createRandomSource))

  /** Creates a [RandomSeedTestRule] with a default, randomly seeded [RandomSource]. */
  constructor() : this(RandomSource::default)

  /**
   * Creates a [RandomSeedTestRule] with a [RandomSource] initialized with the specified [seed].
   *
   * @param seed The specific seed to initialize the [RandomSource] with.
   */
  constructor(seed: Long) : this({ RandomSource.seeded(seed) })

  override fun apply(base: Statement, description: Description) =
    object : Statement() {
      override fun evaluate() {
        try {
          base.evaluate()
        } catch (throwable: Throwable) {
          if (rs.isInitialized() && throwable !is AssumptionViolatedException) {
            System.err.println(
              "WARNING[55negqf33k]: RandomSeedTestRule: Test failed using " +
                "RandomSource with seed=${rs.value.seed}: ${description.displayName}"
            )
          }
          throw throwable
        }
      }
    }
}
