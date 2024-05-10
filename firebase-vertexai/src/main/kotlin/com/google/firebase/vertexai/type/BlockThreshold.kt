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

/**
 * Represents the threshold for some [HarmCategory] that is allowed and blocked by [SafetySetting].
 */
enum class BlockThreshold {
  /** The threshold was not specified. */
  UNSPECIFIED,

  /** Content with negligible harm is allowed. */
  LOW_AND_ABOVE,

  /** Content with negligible to low harm is allowed. */
  MEDIUM_AND_ABOVE,

  /** Content with negligible to medium harm is allowed. */
  ONLY_HIGH,

  /** All content is allowed regardless of harm. */
  NONE
}
