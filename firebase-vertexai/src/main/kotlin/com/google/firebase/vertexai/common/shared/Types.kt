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

package com.google.firebase.vertexai.common.shared

import com.google.firebase.vertexai.common.util.FirstOrdinalSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal object HarmCategorySerializer :
  KSerializer<HarmCategory> by FirstOrdinalSerializer(HarmCategory::class)

@Serializable(HarmCategorySerializer::class)
internal enum class HarmCategory {
  UNKNOWN,
  @SerialName("HARM_CATEGORY_HARASSMENT") HARASSMENT,
  @SerialName("HARM_CATEGORY_HATE_SPEECH") HATE_SPEECH,
  @SerialName("HARM_CATEGORY_SEXUALLY_EXPLICIT") SEXUALLY_EXPLICIT,
  @SerialName("HARM_CATEGORY_DANGEROUS_CONTENT") DANGEROUS_CONTENT
}

typealias Base64 = String

@ExperimentalSerializationApi
@Serializable
internal data class Content(@EncodeDefault val role: String? = "user", val parts: List<Part>)

@Serializable(PartSerializer::class) internal sealed interface Part

@Serializable internal data class TextPart(val text: String) : Part

@Serializable internal data class BlobPart(@SerialName("inline_data") val inlineData: Blob) : Part

@Serializable internal data class FunctionCallPart(val functionCall: FunctionCall) : Part

@Serializable
internal data class FunctionResponsePart(val functionResponse: FunctionResponse) : Part

@Serializable internal data class ExecutableCodePart(val executableCode: ExecutableCode) : Part

@Serializable
internal data class CodeExecutionResultPart(val codeExecutionResult: CodeExecutionResult) : Part

@Serializable internal data class FunctionResponse(val name: String, val response: JsonObject)

@Serializable
internal data class FunctionCall(val name: String, val args: Map<String, String?>? = null)

@Serializable
internal data class FileDataPart(@SerialName("file_data") val fileData: FileData) : Part

@Serializable
internal data class FileData(
  @SerialName("mime_type") val mimeType: String,
  @SerialName("file_uri") val fileUri: String,
)

@Serializable
internal data class Blob(@SerialName("mime_type") val mimeType: String, val data: Base64)

@Serializable internal data class ExecutableCode(val language: String, val code: String)

@Serializable internal data class CodeExecutionResult(val outcome: Outcome, val output: String)

@Serializable
internal enum class Outcome {
  @SerialName("OUTCOME_UNSPECIFIED") UNSPECIFIED,
  OUTCOME_OK,
  OUTCOME_FAILED,
  OUTCOME_DEADLINE_EXCEEDED,
}

@Serializable
internal data class SafetySetting(
  val category: HarmCategory,
  val threshold: HarmBlockThreshold,
  val method: HarmBlockMethod? = null,
)

@Serializable
internal enum class HarmBlockThreshold {
  @SerialName("HARM_BLOCK_THRESHOLD_UNSPECIFIED") UNSPECIFIED,
  BLOCK_LOW_AND_ABOVE,
  BLOCK_MEDIUM_AND_ABOVE,
  BLOCK_ONLY_HIGH,
  BLOCK_NONE,
}

@Serializable
internal enum class HarmBlockMethod {
  @SerialName("HARM_BLOCK_METHOD_UNSPECIFIED") UNSPECIFIED,
  SEVERITY,
  PROBABILITY,
}

internal object PartSerializer : JsonContentPolymorphicSerializer<Part>(Part::class) {
  override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Part> {
    val jsonObject = element.jsonObject
    return when {
      "text" in jsonObject -> TextPart.serializer()
      "functionCall" in jsonObject -> FunctionCallPart.serializer()
      "functionResponse" in jsonObject -> FunctionResponsePart.serializer()
      "inlineData" in jsonObject -> BlobPart.serializer()
      "fileData" in jsonObject -> FileDataPart.serializer()
      "executableCode" in jsonObject -> ExecutableCodePart.serializer()
      "codeExecutionResult" in jsonObject -> CodeExecutionResultPart.serializer()
      else -> throw SerializationException("Unknown Part type")
    }
  }
}
