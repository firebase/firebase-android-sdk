/*
 * Copyright 2024 Google LLC
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

/**
 * Specifies how the block method computes the score that will be compared against the
 * [HarmBlockThreshold] in [SafetySetting].
 */
public enum class HarmBlockMethod {
  /**
   * The harm block method uses both probability and severity scores. See [HarmSeverity] and
   * [HarmProbability].
   */
  SEVERITY,
  /** The harm block method uses the probability score. See [HarmProbability]. */
  PROBABILITY,
}
