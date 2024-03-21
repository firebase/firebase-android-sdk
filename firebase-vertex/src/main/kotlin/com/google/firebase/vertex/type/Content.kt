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

package com.google.firebase.vertex.type

import android.graphics.Bitmap

/**
 * Represents content sent to and received from the model.
 *
 * Contains a collection of text, image, and binary parts.
 *
 * @see content
 */
class Content @JvmOverloads constructor(val role: String? = "user", val parts: List<Part>) {

  class Builder {
    var role: String? = "user"

    var parts: MutableList<Part> = arrayListOf()

    @JvmName("addPart") fun <T : Part> part(data: T) = apply { parts.add(data) }

    @JvmName("addText") fun text(text: String) = part(TextPart(text))

    @JvmName("addBlob") fun blob(mimeType: String, blob: ByteArray) = part(BlobPart(mimeType, blob))

    @JvmName("addImage") fun image(image: Bitmap) = part(ImagePart(image))

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
