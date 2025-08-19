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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/** Interface representing data sent to and received from requests. */
public interface Part {
  public val isThought: Boolean
}

/** Represents text or string based data sent to and received from requests. */
public class TextPart
internal constructor(public val text: String, public override val isThought: Boolean = false) :
  Part {

  public constructor(text: String) : this(text, false)

  @Serializable
  internal data class Internal(val text: String, val thought: Boolean? = null) : InternalPart
}

public class CodeExecutionResultPart
internal constructor(
  public val outcome: String,
  public val output: String,
  public override val isThought: Boolean = false
) : Part {

  public constructor(outcome: String, output: String) : this(outcome, output, false)

  @Serializable
  internal data class Internal(
    @SerialName("codeExecutionResult") val codeExecutionResult: CodeExecutionResult
  ) : InternalPart {

    @Serializable
    internal data class CodeExecutionResult(
      @SerialName("outcome") val outcome: String,
      val output: String,
      val thought: Boolean? = null
    )
  }
}

public class ExecutableCodePart
internal constructor(
  public val language: String,
  public val code: String,
  public override val isThought: Boolean = false
) : Part {

  public constructor(language: String, code: String) : this(language, code, false)

  @Serializable
  internal data class Internal(@SerialName("executableCode") val executableCode: ExecutableCode) :
    InternalPart {

    @Serializable
    internal data class ExecutableCode(
      @SerialName("language") val language: String,
      val code: String,
      val thought: Boolean? = null
    )
  }
}

/**
 * Represents image data sent to and received from requests. The image is converted client-side to
 * JPEG encoding at 80% quality before being sent to the server.
 *
 * @param image [Bitmap] to convert into a [Part]
 */
public class ImagePart
internal constructor(public val image: Bitmap, public override val isThought: Boolean = false) :
  Part {

  public constructor(image: Bitmap) : this(image, false)

  internal fun toInlineDataPart() =
    InlineDataPart(
      android.util.Base64.decode(encodeBitmapToBase64Jpeg(image), BASE_64_FLAGS),
      "image/jpeg",
      isThought
    )
}

/**
 * Represents binary data with an associated MIME type sent to and received from requests.
 *
 * @param inlineData the binary data as a [ByteArray]
 * @param mimeType an IANA standard MIME type. For supported values, see the
 * [Vertex AI documentation](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/send-multimodal-prompts#media_requirements)
 */
public class InlineDataPart
internal constructor(
  public val inlineData: ByteArray,
  public val mimeType: String,
  public override val isThought: Boolean = false
) : Part {

  public constructor(inlineData: ByteArray, mimeType: String) : this(inlineData, mimeType, false)

  @Serializable
  internal data class Internal(@SerialName("inlineData") val inlineData: InlineData) :
    InternalPart {

    @Serializable
    internal data class InlineData(
      @SerialName("mimeType") val mimeType: String,
      val data: Base64,
      val thought: Boolean? = null
    )
  }
}

/**
 * Represents function call name and params received from requests.
 *
 * @param name the name of the function to call
 * @param args the function parameters and values as a [Map]
 * @param id Unique id of the function call. If present, the returned [FunctionResponsePart] should
 * have a matching `id` field.
 */
public class FunctionCallPart
internal constructor(
  public val name: String,
  public val args: Map<String, JsonElement>,
  public val id: String? = null,
  public override val isThought: Boolean = false
) : Part {

  @JvmOverloads
  public constructor(
    name: String,
    args: Map<String, JsonElement>,
    id: String? = null
  ) : this(name, args, id, false)

  @Serializable
  internal data class Internal(val functionCall: FunctionCall) : InternalPart {

    @Serializable
    internal data class FunctionCall(
      val name: String,
      val args: Map<String, JsonElement?>? = null,
      val id: String? = null,
      val thought: Boolean? = null
    )
  }
}

/**
 * Represents function call output to be returned to the model when it requests a function call.
 *
 * @param name The name of the called function.
 * @param response The response produced by the function as a [JSONObject].
 * @param id Matching `id` for a [FunctionCallPart], if one was provided.
 */
public class FunctionResponsePart
internal constructor(
  public val name: String,
  public val response: JsonObject,
  public val id: String? = null,
  public override val isThought: Boolean = false
) : Part {

  @JvmOverloads
  public constructor(
    name: String,
    response: JsonObject,
    id: String? = null
  ) : this(name, response, id, false)

  @Serializable
  internal data class Internal(val functionResponse: FunctionResponse) : InternalPart {

    @Serializable
    internal data class FunctionResponse(
      val name: String,
      val response: JsonObject,
      val id: String? = null,
      val thought: Boolean? = null
    )
  }

  internal fun toInternalFunctionCall(): Internal.FunctionResponse {
    return Internal.FunctionResponse(name, response, id, isThought)
  }
}

/**
 * Represents file data stored in Cloud Storage for Firebase, referenced by URI.
 *
 * @param uri The `"gs://"`-prefixed URI of the file in Cloud Storage for Firebase, for example,
 * `"gs://bucket-name/path/image.jpg"`
 * @param mimeType an IANA standard MIME type. For supported MIME type values see the
 * [Firebase documentation](https://firebase.google.com/docs/vertex-ai/input-file-requirements).
 */
public class FileDataPart
internal constructor(
  public val uri: String,
  public val mimeType: String,
  public override val isThought: Boolean = false
) : Part {

  public constructor(uri: String, mimeType: String) : this(uri, mimeType, false)

  @Serializable
  internal data class Internal(@SerialName("file_data") val fileData: FileData) : InternalPart {

    @Serializable
    internal data class FileData(
      @SerialName("mime_type") val mimeType: String,
      @SerialName("file_uri") val fileUri: String,
      val thought: Boolean? = null
    )
  }
}

/** Returns the part as a [String] if it represents text, and null otherwise */
public fun Part.asTextOrNull(): String? = (this as? TextPart)?.text

/** Returns the part as a [Bitmap] if it represents an image, and null otherwise */
public fun Part.asImageOrNull(): Bitmap? = (this as? ImagePart)?.image

/** Returns the part as a [InlineDataPart] if it represents inline data, and null otherwise */
public fun Part.asInlineDataPartOrNull(): InlineDataPart? = this as? InlineDataPart

/** Returns the part as a [FileDataPart] if it represents a file, and null otherwise */
public fun Part.asFileDataOrNull(): FileDataPart? = this as? FileDataPart

internal typealias Base64 = String

internal const val BASE_64_FLAGS = android.util.Base64.NO_WRAP

@Serializable(PartSerializer::class) internal sealed interface InternalPart

internal object PartSerializer :
  JsonContentPolymorphicSerializer<InternalPart>(InternalPart::class) {
  override fun selectDeserializer(element: JsonElement): DeserializationStrategy<InternalPart> {
    val jsonObject = element.jsonObject
    return when {
      "text" in jsonObject -> TextPart.Internal.serializer()
      "executableCode" in jsonObject -> ExecutableCodePart.Internal.serializer()
      "codeExecutionResult" in jsonObject -> CodeExecutionResultPart.Internal.serializer()
      "functionCall" in jsonObject -> FunctionCallPart.Internal.serializer()
      "functionResponse" in jsonObject -> FunctionResponsePart.Internal.serializer()
      "inlineData" in jsonObject -> InlineDataPart.Internal.serializer()
      "fileData" in jsonObject -> FileDataPart.Internal.serializer()
      else -> throw SerializationException("Unknown Part type")
    }
  }
}

internal fun Part.toInternal(): InternalPart {
  return when (this) {
    is TextPart -> TextPart.Internal(text, isThought)
    is ImagePart ->
      InlineDataPart.Internal(
        InlineDataPart.Internal.InlineData("image/jpeg", encodeBitmapToBase64Jpeg(image), isThought)
      )
    is InlineDataPart ->
      InlineDataPart.Internal(
        InlineDataPart.Internal.InlineData(
          mimeType,
          android.util.Base64.encodeToString(inlineData, BASE_64_FLAGS),
          isThought
        )
      )
    is FunctionCallPart ->
      FunctionCallPart.Internal(FunctionCallPart.Internal.FunctionCall(name, args, id, isThought))
    is FunctionResponsePart ->
      FunctionResponsePart.Internal(
        FunctionResponsePart.Internal.FunctionResponse(name, response, id, isThought)
      )
    is FileDataPart ->
      FileDataPart.Internal(
        FileDataPart.Internal.FileData(mimeType = mimeType, fileUri = uri, thought = isThought)
      )
    is ExecutableCodePart ->
      ExecutableCodePart.Internal(
        ExecutableCodePart.Internal.ExecutableCode(language, code, isThought)
      )
    is CodeExecutionResultPart ->
      CodeExecutionResultPart.Internal(
        CodeExecutionResultPart.Internal.CodeExecutionResult(outcome, output, isThought)
      )
    else ->
      throw com.google.firebase.ai.type.SerializationException(
        "The given subclass of Part (${javaClass.simpleName}) is not supported in the serialization yet."
      )
  }
}

private fun encodeBitmapToBase64Jpeg(input: Bitmap): String {
  ByteArrayOutputStream().let {
    input.compress(Bitmap.CompressFormat.JPEG, 80, it)
    return android.util.Base64.encodeToString(it.toByteArray(), BASE_64_FLAGS)
  }
}

internal fun InternalPart.toPublic(): Part {
  return when (this) {
    is TextPart.Internal -> TextPart(text, thought ?: false)
    is InlineDataPart.Internal -> {
      val data = android.util.Base64.decode(inlineData.data, BASE_64_FLAGS)
      if (inlineData.mimeType.contains("image")) {
        ImagePart(decodeBitmapFromImage(data), inlineData.thought ?: false)
      } else {
        InlineDataPart(data, inlineData.mimeType, inlineData.thought ?: false)
      }
    }
    is FunctionCallPart.Internal ->
      FunctionCallPart(
        functionCall.name,
        functionCall.args.orEmpty().mapValues { it.value ?: JsonNull },
        functionCall.id,
        functionCall.thought ?: false
      )
    is FunctionResponsePart.Internal ->
      FunctionResponsePart(
        functionResponse.name,
        functionResponse.response,
        functionResponse.id,
        functionResponse.thought ?: false
      )
    is FileDataPart.Internal ->
      FileDataPart(fileData.mimeType, fileData.fileUri, fileData.thought ?: false)
    is ExecutableCodePart.Internal ->
      ExecutableCodePart(
        executableCode.language,
        executableCode.code,
        executableCode.thought ?: false
      )
    is CodeExecutionResultPart.Internal ->
      CodeExecutionResultPart(
        codeExecutionResult.outcome,
        codeExecutionResult.output,
        codeExecutionResult.thought ?: false
      )
    else ->
      throw com.google.firebase.ai.type.SerializationException(
        "Unsupported part type \"${javaClass.simpleName}\" provided. This model may not be supported by this SDK."
      )
  }
}

private fun decodeBitmapFromImage(input: ByteArray) =
  BitmapFactory.decodeByteArray(input, 0, input.size)
