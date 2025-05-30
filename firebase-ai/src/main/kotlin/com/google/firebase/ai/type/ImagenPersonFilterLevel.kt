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

/** A filter used to prevent images from containing depictions of children or people. */
@PublicPreviewAPI
public class ImagenPersonFilterLevel private constructor(internal val internalVal: String) {
  public companion object {
    /**
     * Allow generation of images containing people of all ages.
     *
     * > Important: Generation of images containing people or faces may require your use case to be
     * reviewed and approved by Cloud support; see the [Responsible AI and usage guidelines]
     * (https://cloud.google.com/vertex-ai/generative-ai/docs/image/responsible-ai-imagen#person-face-gen)
     * for more details.
     */
    @JvmField public val ALLOW_ALL: ImagenPersonFilterLevel = ImagenPersonFilterLevel("allow_all")
    /**
     * Allow generation of images containing adults only; images of children are filtered out.
     *
     * > Important: Generation of images containing people or faces may require your use case to be
     * reviewed and approved by Cloud support; see the [Responsible AI and usage guidelines]
     * (https://cloud.google.com/vertex-ai/generative-ai/docs/image/responsible-ai-imagen#person-face-gen)
     * for more details.
     */
    @JvmField
    public val ALLOW_ADULT: ImagenPersonFilterLevel = ImagenPersonFilterLevel("allow_adult")
    /**
     * Disallow generation of images containing people or faces; images of people are filtered out.
     */
    @JvmField public val BLOCK_ALL: ImagenPersonFilterLevel = ImagenPersonFilterLevel("dont_allow")
  }
}
