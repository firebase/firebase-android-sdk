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

package com.google.firebase.vertexai.type

import kotlinx.serialization.Serializable

/**
 * Represents token counting info for a single modality.
 *
 * @property modality The modality associated with this token count.
 * @property tokenCount The number of tokens counted.
 */
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class ModalityTokenCount
private constructor(public val modality: ContentModality, public val tokenCount: Int) {

  public operator fun component1(): ContentModality = modality

  public operator fun component2(): Int = tokenCount

  @Serializable
  internal data class Internal(
    val modality: ContentModality.Internal,
    val tokenCount: Int? = null
  ) {
    internal fun toPublic() = ModalityTokenCount(modality.toPublic(), tokenCount ?: 0)
  }
}
