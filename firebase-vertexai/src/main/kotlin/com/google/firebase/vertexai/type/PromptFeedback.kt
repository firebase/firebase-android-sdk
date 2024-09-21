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
 * Feedback on the prompt provided in the request.
 *
 * @param blockReason The reason that content was blocked, if at all.
 * @param safetyRatings A list of relevant [SafetyRating].
 * @param blockReasonMessage A message describing the reason that content was blocked, if any.
 */
class PromptFeedback(
  val blockReason: BlockReason?,
  val safetyRatings: List<SafetyRating>,
  val blockReasonMessage: String?
)

/** Describes why content was blocked. */
enum class BlockReason {
  /** A new and not yet supported value. */
  UNKNOWN,

  /** Content was blocked for an unspecified reason. */
  UNSPECIFIED,

  /** Content was blocked for violating provided [SafetySetting]. */
  SAFETY,

  /** Content was blocked for another reason. */
  OTHER
}
