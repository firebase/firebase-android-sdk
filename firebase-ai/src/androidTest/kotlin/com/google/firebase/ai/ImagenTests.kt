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
package com.google.firebase.ai

import com.google.firebase.ai.AIModels.Companion.app
import com.google.firebase.ai.type.ImagenBackgroundMask
import com.google.firebase.ai.type.ImagenEditMode
import com.google.firebase.ai.type.ImagenEditingConfig
import com.google.firebase.ai.type.ImagenRawImage
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.runBlocking
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
class ImagenTests {
  @Test
  fun testGenerateAndEditImage() {
    val imageGenerationModel = FirebaseAI.getInstance(app()).imagenModel("imagen-3.0-generate-002")
    val imageEditingModel = FirebaseAI.getInstance(app()).imagenModel("imagen-3.0-capability-001")

    runBlocking {
      val catImage = imageGenerationModel.generateImages("A cat").images.first()
      val editedCatImage =
        imageEditingModel.editImage(
          listOf(ImagenRawImage(catImage), ImagenBackgroundMask()),
          "A cat flying through space",
          ImagenEditingConfig(ImagenEditMode.INPAINT_INSERTION)
        )
      assert(editedCatImage.images.size == 1)
    }
  }
}
