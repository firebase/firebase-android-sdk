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
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContentModality
import com.google.firebase.ai.type.CountTokensResponse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests countTokens results matching with expected. Google AI counts 1 additional token for all
 * requests, so all checks are one of two values.
 */
class CountTokensTests {

  /** Ensures that the token count is expected for simple words. */
  @Test
  fun testCountTokensAmount() {
    for (model in getModels()) {
      runBlocking {
        val response = model.countTokens("this is five different words")
        response.totalTokens.shouldBeIn(5, 6)
        response.promptTokensDetails.size shouldBe 1
        response.promptTokensDetails[0].modality shouldBe ContentModality.TEXT
        response.promptTokensDetails[0].tokenCount shouldBe response.totalTokens
      }
    }
  }

  /** Ensures that the model returns token counts in the correct modality for text. */
  @Test
  fun testCountTokensTextModality() {
    for (model in getModels()) {
      runBlocking {
        val response = model.countTokens("this is a text prompt")
        checkTokenCountsMatch(response)
        response.promptTokensDetails.size shouldBe 1
        containsModality(response, ContentModality.TEXT) shouldBe true
      }
    }
  }

  /** Ensures that the model returns token counts in the correct modality for bitmap images. */
  @Test
  fun testCountTokensImageModality() {
    for (model in getModels()) {
      runBlocking {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val response = model.countTokens(bitmap)
        checkTokenCountsMatch(response)
        response.promptTokensDetails.size.shouldBeIn(1, 2)
        containsModality(response, ContentModality.IMAGE) shouldBe true
      }
    }
  }

  /**
   * Ensures the model can count tokens for multiple modalities at once, and return the
   * corresponding token modalities correctly.
   */
  @Test
  fun testCountTokensTextAndImageModality() {
    for (model in getModels()) {
      runBlocking {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val response =
          model.countTokens(
            Content.Builder().text("this is text").build(),
            Content.Builder().image(bitmap).build()
          )
        checkTokenCountsMatch(response)
        response.promptTokensDetails.size shouldBe 2
        containsModality(response, ContentModality.TEXT) shouldBe true
        containsModality(response, ContentModality.IMAGE) shouldBe true
      }
    }
  }

  /**
   * Ensures the model can count the tokens for a sent file. Additionally, ensures that the model
   * treats this sent file as the modality of the mime type, in this case, a plaintext file has its
   * tokens counted as `ContentModality.TEXT`.
   */
  @Test
  fun testCountTokensTextFileModality() {
    for (model in getModels()) {
      runBlocking {
        val response =
          model.countTokens(
            Content.Builder().inlineData("this is text".toByteArray(), "text/plain").build()
          )
        checkTokenCountsMatch(response)
        response.totalTokens.shouldBeIn(3, 4)
        response.promptTokensDetails.size shouldBe 1
        containsModality(response, ContentModality.TEXT) shouldBe true
      }
    }
  }

  /**
   * Ensures the model can count the tokens for a sent file. Additionally, ensures that the model
   * treats this sent file as the modality of the mime type, in this case, a PNG encoded bitmap has
   * its tokens counted as `ContentModality.IMAGE`.
   */
  @Test
  fun testCountTokensImageFileModality() {
    for (model in getModels()) {
      runBlocking {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 1, stream)
        val array = stream.toByteArray()
        val response = model.countTokens(Content.Builder().inlineData(array, "image/png").build())
        checkTokenCountsMatch(response)
        response.promptTokensDetails.size.shouldBeIn(1, 2)
        containsModality(response, ContentModality.IMAGE) shouldBe true
      }
    }
  }

  /**
   * Ensures that nothing is free, that is, empty content contains no tokens. For some reason, this
   * is treated as `ContentModality.TEXT`.
   */
  @Test
  fun testCountTokensNothingIsFree() {
    for (model in getModels()) {
      runBlocking {
        val response = model.countTokens(Content.Builder().build())
        checkTokenCountsMatch(response)
        response.totalTokens.shouldBeIn(0, 1)
        response.promptTokensDetails.size shouldBe 1
        containsModality(response, ContentModality.TEXT) shouldBe true
      }
    }
  }

  /**
   * Checks if the model can count the tokens for a sent file. Additionally, ensures that the model
   * treats this sent file as the modality of the mime type, in this case, a JSON file is not
   * recognized, and no tokens are counted. This ensures if/when the model can handle JSON, our
   * testing makes us aware.
   */
  @Test
  fun testCountTokensJsonFileModality() {
    for (model in getModels()) {
      runBlocking {
        val json =
          """
          {
            "foo": "bar",
            "baz": 3,
            "qux": [
              {
                "quux": [
                  1,
                  2
                ]
              }
            ]
          }
        """
            .trimIndent()
        val response =
          model.countTokens(
            Content.Builder().inlineData(json.toByteArray(), "application/json").build()
          )
        checkTokenCountsMatch(response)
        if (response.promptTokensDetails.isEmpty()) {
          // Vertex does not believe JSON is composed of tokens
          response.promptTokensDetails.shouldBeEmpty()
          response.totalTokens shouldBe 0
        } else {
          // GoogleAI, on the other hand, is a firm believer in tokenated JSON
          response.promptTokensDetails.shouldNotBeEmpty()
          response.totalTokens shouldBe 53
        }
      }
    }
  }

  fun checkTokenCountsMatch(response: CountTokensResponse) {
    sumTokenCount(response) shouldBe response.totalTokens
  }

  fun sumTokenCount(response: CountTokensResponse): Int {
    return response.promptTokensDetails.sumOf { it.tokenCount }
  }

  fun containsModality(response: CountTokensResponse, modality: ContentModality): Boolean {
    for (token in response.promptTokensDetails) {
      if (token.modality == modality) {
        return true
      }
    }
    return false
  }
}
