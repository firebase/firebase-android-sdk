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
import org.json.JSONObject

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
 * @param blob the binary data as a [ByteArray]
 */
class BlobPart(val mimeType: String, val blob: ByteArray) : Part

/**
 * Represents function call name and params received from requests.
 *
 * @param name the name of the function to call
 * @param args the function parameters and values as a [Map]
 */
class FunctionCallPart(val name: String, val args: Map<String, String?>) : Part

/**
 * Represents function call output to be returned to the model when it requests a function call.
 *
 * @param name the name of the called function
 * @param response the response produced by the function as a [JSONObject]
 */
class FunctionResponsePart(val name: String, val response: JSONObject) : Part

/**
 * Represents file data stored in Cloud Storage for Firebase, referenced by URI.
 *
 * @param uri The `"gs://"`-prefixed URI of the file in Cloud Storage for Firebase, for example,
 * `"gs://bucket-name/path/image.jpg"`
 * @param mimeType an IANA standard MIME type. For supported value see the
 * [vertex documentation](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/send-multimodal-prompts#media_requirements)
 */
class FileDataPart(val uri: String, val mimeType: String) : Part

/** Returns the part as a [String] if it represents text, and null otherwise */
fun Part.asTextOrNull(): String? = (this as? TextPart)?.text

/** Returns the part as a [Bitmap] if it represents an image, and null otherwise */
fun Part.asImageOrNull(): Bitmap? = (this as? ImagePart)?.image

/** Returns the part as a [BlobPart] if it represents a blob, and null otherwise */
fun Part.asBlobPartOrNull(): BlobPart? = this as? BlobPart

/** Returns the part as a [FileDataPart] if it represents a file, and null otherwise */
fun Part.asFileDataOrNull(): FileDataPart? = this as? FileDataPart
