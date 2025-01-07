/*
 * Copyright 2025 Google LLC
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

/** Represents the aspect ratio that the generated image should conform to. */
@Suppress("EnumEntryName")
public enum class ImagenAspectRatio(internal val internalVal: String) {
  /** A square image, useful for icons, profile pictures, etc. */
  SQUARE_1x1("1:1"),

  /** A portrait image in 3:4, the aspect ratio of older TVs. */
  PORTRAIT_3x4("3:4"),

  /** A landscape image in 4:3, the aspect ratio of older TVs. */
  LANDSCAPE_4x3("4:3"),

  /** A portrait image in 9:16, the aspect ratio of modern monitors and phone screens. */
  PORTRAIT_9x16("9:16"),

  /** A landscape image in 16:9, the aspect ratio of modern monitors and phone screens. */
  LANDSCAPE_16x9("16:9")
}
