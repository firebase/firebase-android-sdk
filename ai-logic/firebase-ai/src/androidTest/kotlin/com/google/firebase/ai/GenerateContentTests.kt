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

import android.graphics.Bitmap
import com.google.firebase.ai.AIModels.Companion.getModels
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.imageConfig
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GenerateContentTests {
  private val validator = TypesValidator()

  /**
   * Ensures the model can response to prompts and that the structure of this response is expected.
   */
  @Test
  fun testGenerateContent_BasicRequest() {
    for (model in getModels()) {
      runBlocking {
        val response = model.generateContent("pick a random color")
        validator.validateResponse(response)
      }
    }
  }

  /**
   * Ensures that the model can answer very simple questions. Further testing the "logic" of the
   * model and the content of the responses is prone to flaking, this test is also prone to that.
   * This is probably the furthest we can consistently test for reasonable response structure, past
   * sending the request and response back to the model and asking it if it fits our expectations.
   */
  @Test
  fun testGenerateContent_ColorMixing() {
    for (model in getModels()) {
      runBlocking {
        val response = model.generateContent("what color is created when red and yellow are mixed?")
        validator.validateResponse(response)
        response.text.shouldContainIgnoringCase("orange")
      }
    }
  }

  /**
   * Ensures that the model can answer very simple questions. Further testing the "logic" of the
   * model and the content of the responses is prone to flaking, this test is also prone to that.
   * This is probably the furthest we can consistently test for reasonable response structure, past
   * sending the request and response back to the model and asking it if it fits our expectations.
   */
  @Test
  fun testGenerateContent_CanSendImage() {
    for (model in getModels()) {
      runBlocking {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val yellow = Integer.parseUnsignedInt("FFFFFF00", 16)
        bitmap.setPixel(3, 3, yellow)
        bitmap.setPixel(6, 3, yellow)
        bitmap.setPixel(3, 6, yellow)
        bitmap.setPixel(4, 7, yellow)
        bitmap.setPixel(5, 7, yellow)
        bitmap.setPixel(6, 6, yellow)
        val response =
          model.generateContent(
            Content.Builder().text("here is a tiny smile").image(bitmap).build()
          )
        validator.validateResponse(response)
      }
    }
  }

  @Test
  fun testGenerateContent_ImageConfig() {
    val app = AIModels.app()
    val model =
      FirebaseAI.getInstance(app, GenerativeBackend.googleAI())
        .generativeModel(
          modelName = "gemini-3.1-flash-image-preview",
          generationConfig =
            generationConfig {
              responseModalities = listOf(ResponseModality.IMAGE)
              imageConfig = imageConfig {
                aspectRatio = AspectRatio.SQUARE_1x1
                imageSize = ImageSize.SIZE_512
              }
            }
        )

    runBlocking {
      val response = model.generateContent("Generate a landscape picture of a sunset")
      response.candidates.shouldNotBeEmpty()

      val parts = response.candidates.first().content.parts
      val imagePart = parts.filterIsInstance<ImagePart>().firstOrNull()
      imagePart.shouldNotBeNull()

      val bitmap = imagePart.image

      // Verify Aspect Ratio (1:1)
      bitmap.width shouldBe bitmap.height

      // Verify Size (512px)
      bitmap.width shouldBe 512
      bitmap.height shouldBe 512
    }
  }
}
