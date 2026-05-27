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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * A configuration for a [HarmBlockThreshold] of some [HarmCategory] allowed and blocked in
 * responses.
 *
 * @param harmCategory The relevant [HarmCategory].
 * @param threshold The threshold form harm allowable.
 * @param method Specify if the threshold is used for probability or severity score, if not
 * specified it will default to [HarmBlockMethod.PROBABILITY].
 */
public class SafetySetting(
  internal val harmCategory: HarmCategory,
  internal val threshold: HarmBlockThreshold,
  internal val method: HarmBlockMethod? = null,
) {
  internal fun toInternal() =
    Internal(harmCategory.toInternal(), threshold.toInternal(), method?.toInternal())

  @Serializable
  internal data class Internal(
    val category: HarmCategory.Internal,
    val threshold: HarmBlockThreshold.Internal,
    val method: HarmBlockMethod.Internal? = null,
  )
}
