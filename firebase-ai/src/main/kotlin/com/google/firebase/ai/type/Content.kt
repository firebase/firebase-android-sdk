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
import kotlin.collections.filterNot
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Represents content sent to and received from the model.
 *
 * `Content` is composed of a one or more heterogeneous parts that can be represent data in
 * different formats, like text or images.
 *
 * @param role The producer of the content. Must be either `"user"` or `"model"`. By default, it's
 * `"user"`.
 * @param parts An ordered list of [Part] that constitute this content.
 */
public class Content
@JvmOverloads
constructor(public val role: String? = "user", public val parts: List<Part>) {

  /** Returns a copy of this object, with the provided parameters overwriting the originals. */
  public fun copy(role: String? = this.role, parts: List<Part> = this.parts): Content {
    return Content(role, parts)
  }

  /** Builder class to facilitate constructing complex [Content] objects. */
  public class Builder {

    /** The producer of the content. Must be either 'user' or 'model'. By default, it's "user". */
    @JvmField public var role: String? = "user"

    /**
     * The mutable list of [Part]s comprising the [Content].
     *
     * Prefer using the provided helper methods over modifying this list directly.
     */
    @JvmField public var parts: MutableList<Part> = arrayListOf()

    public fun setRole(role: String?): Content.Builder = apply { this.role = role }
    public fun setParts(parts: MutableList<Part>): Content.Builder = apply { this.parts = parts }

    /** Adds a new [Part] to [parts]. */
    @JvmName("addPart")
    public fun <T : Part> part(data: T): Content.Builder = apply { parts.add(data) }

    /** Adds a new [TextPart] with the provided [text] to [parts]. */
    @JvmName("addText") public fun text(text: String): Content.Builder = part(TextPart(text))

    /**
     * Adds a new [InlineDataPart] with the provided [bytes], which should be interpreted by the
     * model based on the [mimeType], to [parts].
     */
    @JvmName("addInlineData")
    public fun inlineData(bytes: ByteArray, mimeType: String): Content.Builder =
      part(InlineDataPart(bytes, mimeType))

    /** Adds a new [ImagePart] with the provided [image] to [parts]. */
    @JvmName("addImage") public fun image(image: Bitmap): Content.Builder = part(ImagePart(image))

    /** Adds a new [FileDataPart] with the provided [uri] and [mimeType] to [parts]. */
    @JvmName("addFileData")
    public fun fileData(uri: String, mimeType: String): Content.Builder =
      part(FileDataPart(uri, mimeType))

    /** Returns a new [Content] using the defined [role] and [parts]. */
    public fun build(): Content = Content(role, parts)
  }

  @OptIn(ExperimentalSerializationApi::class)
  internal fun toInternal() =
    Internal(this.role ?: "user", this.parts.map { it.toInternal() })

  @ExperimentalSerializationApi
  @Serializable
  internal data class Internal(
    @EncodeDefault val role: String? = "user",
    val parts: List<InternalPart>? = null
  ) {
    internal fun toPublic(): Content {
      // Return empty if none of the parts is a known part
      if (parts == null || parts.filterNot { it is UnknownPart.Internal }.isEmpty()) {
        return Content(role, emptyList())
      }
      // From all the known parts, if they are all text and empty, we coalesce them into a single
      // one-character string part so the backend doesn't fail if we send this back as part of a
      // multi-turn interaction.
      val returnedParts =
        parts
          .filterNot { it is UnknownPart.Internal }
          .map { it.toPublic() }
          .filterNot { it is TextPart && it.text.isEmpty() }
      return Content(role, returnedParts.ifEmpty { listOf(TextPart(" ")) })
    }
  }
}

/**
 * Function to build a new [Content] instances in a DSL-like manner.
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
