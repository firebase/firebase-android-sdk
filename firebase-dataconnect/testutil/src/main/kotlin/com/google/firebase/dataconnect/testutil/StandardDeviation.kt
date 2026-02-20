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
package com.google.firebase.dataconnect.testutil

import kotlin.math.sqrt

enum class StandardDeviationMode {
  /**
   * Population Standard Deviation (σ) is calculated when you have data for the entire population
   * you are studying. For example, if you were calculating the standard deviation of the heights of
   * all students in a single, specific classroom, you would use the population standard deviation
   * because you have all the data for that defined group. It gives a precise measure of the spread
   * for that specific population.
   */
  Population,

  /**
   * Sample Standard deviation (s): This is used when you have a sample of data from a larger
   * population, and you want to estimate the standard deviation of that entire population. For
   * instance, if you measured the heights of 100 students to estimate the height variation of all
   * students in a country, you would use the sample standard deviation. The formula for the sample
   * standard deviation is slightly different from the population standard deviation (it divides by
   * n-1 instead of n) to provide a more accurate and unbiased estimate of the population's standard
   * deviation.
   */
  Sample,
}

/** Calculates the standard deviation of [Int] values. */
fun Collection<Int>.standardDeviation(mode: StandardDeviationMode): Double {
  val mean = average()
  val sumOfSquaredDifferences = sumOf { (it - mean).let { diff -> diff * diff } }
  val divisor =
    when (mode) {
      StandardDeviationMode.Population -> size
      StandardDeviationMode.Sample -> size - 1
    }
  val variance = sumOfSquaredDifferences / divisor
  return sqrt(variance)
}
