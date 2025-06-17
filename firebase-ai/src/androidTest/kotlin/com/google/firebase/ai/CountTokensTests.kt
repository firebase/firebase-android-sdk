package com.google.firebase.ai

import android.graphics.Bitmap
import com.google.firebase.ai.AIModels.Companion.getModels
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContentModality
import com.google.firebase.ai.type.CountTokensResponse
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CountTokensTests {

  /** Ensures that the token count is expected for simple words. */
  @Test
  fun testCountTokensAmount() {
    for (model in getModels()) {
      runBlocking {
        val response = model.countTokens("this is five different words")
        assert(response.totalTokens == 5)
        assert(response.promptTokensDetails.size == 1)
        assert(response.promptTokensDetails[0].modality == ContentModality.TEXT)
        assert(response.promptTokensDetails[0].tokenCount == 5)
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
        assert(response.promptTokensDetails.size == 1)
        assert(containsModality(response, ContentModality.TEXT))
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
        assert(response.promptTokensDetails.size == 1)
        assert(containsModality(response, ContentModality.IMAGE))
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
        assert(response.promptTokensDetails.size == 2)
        assert(containsModality(response, ContentModality.TEXT))
        assert(containsModality(response, ContentModality.IMAGE))
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
        assert(response.totalTokens == 3)
        assert(response.promptTokensDetails.size == 1)
        assert(containsModality(response, ContentModality.TEXT))
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
        assert(response.promptTokensDetails.size == 1)
        assert(containsModality(response, ContentModality.IMAGE))
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
        assert(response.totalTokens == 0)
        assert(response.promptTokensDetails.size == 1)
        assert(containsModality(response, ContentModality.TEXT))
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
        assert(response.promptTokensDetails.isEmpty())
        assert(response.totalTokens == 0)
      }
    }
  }

  fun checkTokenCountsMatch(response: CountTokensResponse) {
    assert(sumTokenCount(response) == response.totalTokens)
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
