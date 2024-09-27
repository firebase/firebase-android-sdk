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

import android.graphics.Bitmap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Interface representing data sent to and received from requests. */
interface Part

/** Represents text or string based data sent to and received from requests. */
class TextPart(val text: String) : Part

/**
 * Represents image data sent to and received from requests. When this is sent to the server it is
 * converted to jpeg encoding at 80% quality.
 *
 * @param image [Bitmap] to convert into a [Part]
 */
class ImagePart(val image: Bitmap) : Part

/**
 * Represents binary data with an associated MIME type sent to and received from requests.
 *
 * @param mimeType an IANA standard MIME type. For supported values, see the
 * [Vertex AI documentation](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/send-multimodal-prompts#media_requirements)
 * .
 * @param inlineData the binary data as a [ByteArray]
 */
class InlineDataPart(val mimeType: String, val inlineData: ByteArray) : Part

/**
 * Represents a function call request from the model
 *
 * @param functionCall The information provided by the model to call a function.
 */
class FunctionCallPart(val functionCall: FunctionCall) : Part

/**
 * The result of calling a function as requested by the model.
 *
 * @param functionResponse The information to send back to the model as the result of a functions
 * call.
 */
class FunctionResponsePart(val functionResponse: FunctionResponse) : Part

/**
 * The data necessary to invoke function [name] using the arguments [args].
 *
 * @param name the name of the function to call
 * @param args the function parameters and values as a [Map]
 */
class FunctionCall(val name: String, val args: Map<String, JsonElement>)

/**
 * The [response] generated after calling function [name].
 *
 * @param name the name of the called function
 * @param response the response produced by the function as a [JsonObject]
 */
class FunctionResponse(val name: String, val response: JsonObject)

/**
 * Represents file data stored in Cloud Storage for Firebase, referenced by URI.
 *
 * @param uri The `"gs://"`-prefixed URI of the file in Cloud Storage for Firebase, for example,
 * `"gs://bucket-name/path/image.jpg"`
 * @param mimeType an IANA standard MIME type. For supported MIME type values see the
 * [Firebase documentation](https://firebase.google.com/docs/vertex-ai/input-file-requirements).
 */
class FileDataPart(val uri: String, val mimeType: String) : Part

/** Returns the part as a [String] if it represents text, and null otherwise */
fun Part.asTextOrNull(): String? = (this as? TextPart)?.text

/** Returns the part as a [Bitmap] if it represents an image, and null otherwise */
fun Part.asImageOrNull(): Bitmap? = (this as? ImagePart)?.image

/** Returns the part as a [InlineDataPart] if it represents inline data, and null otherwise */
fun Part.asInlineDataPartOrNull(): InlineDataPart? = this as? InlineDataPart

/** Returns the part as a [FileDataPart] if it represents a file, and null otherwise */
fun Part.asFileDataOrNull(): FileDataPart? = this as? FileDataPart
