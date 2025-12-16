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

package com.google.firebase.ai.type

/** Specifies the size of generated images. */
public class ImageSize private constructor(internal val internalVal: String) {

  public companion object {
    @JvmField public val SIZE_1K: ImageSize = ImageSize("1K")

    @JvmField public val SIZE_2K: ImageSize = ImageSize("2K")

    @JvmField public val SIZE_4K: ImageSize = ImageSize("4K")
  }
}
