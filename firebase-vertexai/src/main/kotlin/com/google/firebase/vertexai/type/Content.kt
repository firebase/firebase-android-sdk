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
public class Content
@JvmOverloads
constructor(public val role: String? = "user", public val parts: List<Part>) {

  public fun copy(role: String? = this.role, parts: List<Part> = this.parts): Content {
    return Content(role, parts)
  }

  /** Builder class to facilitate constructing complex [Content] objects. */
  public class Builder {

    /** The producer of the content. By default, it's "user". */
    public var role: String? = "user"

    /**
     * Mutable list of [Part] comprising a single [Content].
     *
     * Prefer using the provided helper methods over adding elements to the list directly.
     */
    public var parts: MutableList<Part> = arrayListOf()

    /** Adds a new [Part] to [parts]. */
    @JvmName("addPart")
    public fun <T : Part> part(data: T): Content.Builder = apply { parts.add(data) }

    /** Wraps the provided text inside a [TextPart] and adds it to [parts] list. */
    @JvmName("addText") public fun text(text: String): Content.Builder = part(TextPart(text))

    /**
     * Wraps the provided [bytes] and [mimeType] inside a [InlineDataPart] and adds it to the
     * [parts] list.
     */
    @JvmName("addInlineData")
    public fun inlineData(mimeType: String, bytes: ByteArray): Content.Builder =
      part(InlineDataPart(bytes, mimeType))

    /** Wraps the provided [image] inside an [ImagePart] and adds it to the [parts] list. */
    @JvmName("addImage") public fun image(image: Bitmap): Content.Builder = part(ImagePart(image))

    /**
     * Wraps the provided Google Cloud Storage for Firebase [uri] and [mimeType] inside a
     * [FileDataPart] and adds it to the [parts] list.
     */
    @JvmName("addFileData")
    public fun fileData(uri: String, mimeType: String): Content.Builder =
      part(FileDataPart(uri, mimeType))

    /** Returns a new [Content] using the defined [role] and [parts]. */
    public fun build(): Content = Content(role, parts)
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
public fun content(role: String? = "user", init: Content.Builder.() -> Unit): Content {
  val builder = Content.Builder()
  builder.role = role
  builder.init()
  return builder.build()
}
