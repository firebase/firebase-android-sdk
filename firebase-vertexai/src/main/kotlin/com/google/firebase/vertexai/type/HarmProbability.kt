/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.vertexai.type

/** Represents the probability that some [HarmCategory] is applicable in a [SafetyRating]. */
public class HarmProbability private constructor(public val ordinal: Int) {
  public companion object {
    /** A new and not yet supported value. */
    @JvmField public val UNKNOWN: HarmProbability = HarmProbability(0)

    /** Probability for harm is negligible. */
    @JvmField public val NEGLIGIBLE: HarmProbability = HarmProbability(1)

    /** Probability for harm is low. */
    @JvmField public val LOW: HarmProbability = HarmProbability(2)

    /** Probability for harm is medium. */
    @JvmField public val MEDIUM: HarmProbability = HarmProbability(3)

    /** Probability for harm is high. */
    @JvmField public val HIGH: HarmProbability = HarmProbability(4)
  }
}
