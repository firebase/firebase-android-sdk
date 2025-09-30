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

/** Represents the aspect ratio that the generated image should conform to. */
public class AspectRatio private constructor(internal val internalVal: String) {
  public companion object {
    @JvmField public val SQUARE_1x1: AspectRatio = AspectRatio("1:1")
    @JvmField public val PORTRAIT_2x3: AspectRatio = AspectRatio("2:3")
    @JvmField public val LANDSCAPE_3x2: AspectRatio = AspectRatio("3:2")
    @JvmField public val PORTRAIT_3x4: AspectRatio = AspectRatio("3:4")
    @JvmField public val LANDSCAPE_4x3: AspectRatio = AspectRatio("4:3")
    @JvmField public val PORTRAIT_4x5: AspectRatio = AspectRatio("4:5")
    @JvmField public val LANDSCAPE_5x4: AspectRatio = AspectRatio("5:4")
    @JvmField public val PORTRAIT_9x16: AspectRatio = AspectRatio("9:16")
    @JvmField public val LANDSCAPE_16x9: AspectRatio = AspectRatio("16:9")
    @JvmField public val LANDSCAPE_21x9: AspectRatio = AspectRatio("21:9")
  }
}
