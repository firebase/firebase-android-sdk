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

package com.google.firebase.vertexai.common.server

import com.google.firebase.vertexai.common.shared.Content
import com.google.firebase.vertexai.common.shared.HarmCategory
import com.google.firebase.vertexai.common.util.FirstOrdinalSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

internal object BlockReasonSerializer :
  KSerializer<BlockReason> by FirstOrdinalSerializer(BlockReason::class)

internal object HarmProbabilitySerializer :
  KSerializer<HarmProbability> by FirstOrdinalSerializer(HarmProbability::class)

internal object HarmSeveritySerializer :
  KSerializer<HarmSeverity> by FirstOrdinalSerializer(HarmSeverity::class)

internal object FinishReasonSerializer :
  KSerializer<FinishReason> by FirstOrdinalSerializer(FinishReason::class)

@Serializable
internal data class PromptFeedback(
  val blockReason: BlockReason? = null,
  val safetyRatings: List<SafetyRating>? = null,
  val blockReasonMessage: String? = null,
)

@Serializable(BlockReasonSerializer::class)
internal enum class BlockReason {
  UNKNOWN,
  @SerialName("BLOCKED_REASON_UNSPECIFIED") UNSPECIFIED,
  SAFETY,
  OTHER
}

@Serializable
internal data class Candidate(
  val content: Content? = null,
  val finishReason: FinishReason? = null,
  val safetyRatings: List<SafetyRating>? = null,
  val citationMetadata: CitationMetadata? = null,
  val groundingMetadata: GroundingMetadata? = null,
)

@Serializable
internal data class CitationMetadata
@OptIn(ExperimentalSerializationApi::class)
internal constructor(@JsonNames("citations") val citationSources: List<CitationSources>)

@Serializable
internal data class CitationSources(
  val title: String? = null,
  val startIndex: Int = 0,
  val endIndex: Int,
  val uri: String? = null,
  val license: String? = null,
  val publicationDate: Date? = null,
)

@Serializable
internal data class Date(
  /** Year of the date. Must be between 1 and 9999, or 0 for no year. */
  val year: Int? = null,
  /** 1-based index for month. Must be from 1 to 12, or 0 to specify a year without a month. */
  val month: Int? = null,
  /**
   * Day of a month. Must be from 1 to 31 and valid for the year and month, or 0 to specify a year
   * by itself or a year and month where the day isn't significant.
   */
  val day: Int? = null,
)

@Serializable
internal data class SafetyRating(
  val category: HarmCategory,
  val probability: HarmProbability,
  val blocked: Boolean? = null, // TODO(): any reason not to default to false?
  val probabilityScore: Float? = null,
  val severity: HarmSeverity? = null,
  val severityScore: Float? = null,
)

@Serializable
internal data class GroundingMetadata(
  @SerialName("web_search_queries") val webSearchQueries: List<String>?,
  @SerialName("search_entry_point") val searchEntryPoint: SearchEntryPoint?,
  @SerialName("retrieval_queries") val retrievalQueries: List<String>?,
  @SerialName("grounding_attribution") val groundingAttribution: List<GroundingAttribution>?,
)

@Serializable
internal data class SearchEntryPoint(
  @SerialName("rendered_content") val renderedContent: String?,
  @SerialName("sdk_blob") val sdkBlob: String?,
)

@Serializable
internal data class GroundingAttribution(
  val segment: Segment,
  @SerialName("confidence_score") val confidenceScore: Float?,
)

@Serializable
internal data class Segment(
  @SerialName("start_index") val startIndex: Int,
  @SerialName("end_index") val endIndex: Int,
)

@Serializable(HarmProbabilitySerializer::class)
internal enum class HarmProbability {
  UNKNOWN,
  @SerialName("HARM_PROBABILITY_UNSPECIFIED") UNSPECIFIED,
  NEGLIGIBLE,
  LOW,
  MEDIUM,
  HIGH
}

@Serializable(HarmSeveritySerializer::class)
internal enum class HarmSeverity {
  UNKNOWN,
  @SerialName("HARM_SEVERITY_UNSPECIFIED") UNSPECIFIED,
  @SerialName("HARM_SEVERITY_NEGLIGIBLE") NEGLIGIBLE,
  @SerialName("HARM_SEVERITY_LOW") LOW,
  @SerialName("HARM_SEVERITY_MEDIUM") MEDIUM,
  @SerialName("HARM_SEVERITY_HIGH") HIGH
}

@Serializable(FinishReasonSerializer::class)
internal enum class FinishReason {
  UNKNOWN,
  @SerialName("FINISH_REASON_UNSPECIFIED") UNSPECIFIED,
  STOP,
  MAX_TOKENS,
  SAFETY,
  RECITATION,
  OTHER
}

@Serializable
internal data class GRpcError(
  val code: Int,
  val message: String,
  val details: List<GRpcErrorDetails>? = null
)

@Serializable
internal data class GRpcErrorDetails(
  val reason: String? = null,
  val domain: String? = null,
  val metadata: Map<String, String>? = null
)
