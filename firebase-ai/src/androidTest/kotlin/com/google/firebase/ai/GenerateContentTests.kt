package com.google.firebase.ai

import android.graphics.Bitmap
import com.google.firebase.ai.AIModels.Companion.getModels
import com.google.firebase.ai.type.Content
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
        assert(response.text!!.contains("orange", true))
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
}
