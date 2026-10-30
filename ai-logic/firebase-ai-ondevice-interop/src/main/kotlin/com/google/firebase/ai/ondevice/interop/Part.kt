/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.ai.ondevice.interop

import android.graphics.Bitmap

/** Interface representing data sent to and received from requests. */
public interface Part {}

/** Represents text or string based data. */
public class TextPart(public val text: String) : Part

/** Represents image data. */
public class ImagePart(public val bitmap: Bitmap) : Part

/**
 * Represents function call name and parameters emitted by the model or sent in conversation
 * history.
 */
public class FunctionCallPart
@JvmOverloads
constructor(
  public val name: String,
  public val args: Map<String, Any?>,
  public val id: String? = null
) : Part {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FunctionCallPart) return false
    return name == other.name && args == other.args && id == other.id
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + args.hashCode()
    result = 31 * result + (id?.hashCode() ?: 0)
    return result
  }
}

/** Represents function call output to be returned to the model. */
public class FunctionResponsePart
@JvmOverloads
constructor(
  public val name: String,
  public val response: Map<String, Any?>,
  public val id: String? = null
) : Part {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FunctionResponsePart) return false
    return name == other.name && response == other.response && id == other.id
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + response.hashCode()
    result = 31 * result + (id?.hashCode() ?: 0)
    return result
  }
}
