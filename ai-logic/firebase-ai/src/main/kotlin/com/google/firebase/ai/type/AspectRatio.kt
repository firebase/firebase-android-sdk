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

package com.google.firebase.ai.type

/** Represents the aspect ratio that the generated image should conform to. */
public class AspectRatio private constructor(internal val value: String) {
  internal fun toInternal() = value

  public companion object {
    /** Square (1:1) aspect ratio. */
    @JvmField public val SQUARE_1x1: AspectRatio = AspectRatio("1:1")

    /** Portrait (2:3) aspect ratio. */
    @JvmField public val PORTRAIT_2x3: AspectRatio = AspectRatio("2:3")

    /** Landscape (3:2) aspect ratio. */
    @JvmField public val LANDSCAPE_3x2: AspectRatio = AspectRatio("3:2")

    /** Portrait full screen (3:4) aspect ratio. */
    @JvmField public val PORTRAIT_3x4: AspectRatio = AspectRatio("3:4")

    /** Fullscreen (4:3) aspect ratio. */
    @JvmField public val LANDSCAPE_4x3: AspectRatio = AspectRatio("4:3")

    /** Portrait (4:5) aspect ratio. */
    @JvmField public val PORTRAIT_4x5: AspectRatio = AspectRatio("4:5")

    /** Landscape (5:4) aspect ratio. */
    @JvmField public val LANDSCAPE_5x4: AspectRatio = AspectRatio("5:4")

    /** Portrait widescreen (9:16) aspect ratio. */
    @JvmField public val PORTRAIT_9x16: AspectRatio = AspectRatio("9:16")

    /** Widescreen (16:9) aspect ratio. */
    @JvmField public val LANDSCAPE_16x9: AspectRatio = AspectRatio("16:9")

    /** Landscape (21:9) aspect ratio. */
    @JvmField public val LANDSCAPE_21x9: AspectRatio = AspectRatio("21:9")
  }
}
