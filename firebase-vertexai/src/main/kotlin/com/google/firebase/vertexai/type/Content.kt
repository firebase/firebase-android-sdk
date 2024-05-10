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

/**
 * Represents content sent to and received from the model.
 *
 * @param role the producer of the content. By default, it's "user".
 * @param parts ordered list of [Part] that constitute a single message.
 *
 * @see content
 */
class Content @JvmOverloads constructor(val role: String? = "user", val parts: List<Part>) {

  /** Builder class to facilitate constructing complex [Content] objects. */
  class Builder {

    /** The producer of the content. By default, it's "user". */
    var role: String? = "user"

    /**
     * Mutable list of [Part] comprising a single [Content].
     *
     * Prefer using the provided helper methods over adding elements to the list directly.
     */
    var parts: MutableList<Part> = arrayListOf()

    /** Adds a new [Part] to [parts]. */
    @JvmName("addPart") fun <T : Part> part(data: T) = apply { parts.add(data) }

    /** Wraps the provided text inside a [TextPart] and adds it to [parts] list. */
    @JvmName("addText") fun text(text: String) = part(TextPart(text))

    /**
     * Wraps the provided [blob] and [mimeType] inside a [BlobPart] and adds it to the [parts] list.
     */
    @JvmName("addBlob") fun blob(mimeType: String, blob: ByteArray) = part(BlobPart(mimeType, blob))

    /** Wraps the provided [image] inside an [ImagePart] and adds it to the [parts] list. */
    @JvmName("addImage") fun image(image: Bitmap) = part(ImagePart(image))

    /**
     * Wraps the provided Google Cloud Storage for Firebase [uri] and [mimeType] inside a
     * [FileDataPart] and adds it to the [parts] list.
     */
    @JvmName("addFileData")
    fun fileData(uri: String, mimeType: String) = part(FileDataPart(uri, mimeType))

    /** Returns a new [Content] using the defined [role] and [parts]. */
    fun build(): Content = Content(role, parts)
  }
}

/**
 * Function to construct content sent to and received in a DSL-like manner.
 *
 * Contains a collection of text, image, and binary parts.
 *
 * Example usage:
 * ```
 * content("user") {
 *   text("Example string")
 * )
 * ```
 */
fun content(role: String? = "user", init: Content.Builder.() -> Unit): Content {
  val builder = Content.Builder()
  builder.role = role
  builder.init()
  return builder.build()
}
