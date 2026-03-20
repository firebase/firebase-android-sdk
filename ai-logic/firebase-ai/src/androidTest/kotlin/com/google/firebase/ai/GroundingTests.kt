package com.google.firebase.ai

import com.google.firebase.ai.AIModels.Companion.app
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.RetrievalConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GroundingTests {

  @Test
  fun groundingTests_canRecognizeAreas() {
    runBlocking {
      val model = setupModel()
      val response =
        model.generateContent("Where is a good place to grab a coffee near Alameda, CA?")
      validateMapsGrounding(response)
    }
  }

  @Test
  fun groundingTests_canRecognizeLatLng() {
    runBlocking {
      val model =
        setupModel(
          config =
            ToolConfig(
              retrievalConfig =
                RetrievalConfig(
                  latLng = LatLng(latitude = 30.2672, longitude = -97.7431),
                  languageCode = "en_US",
                ),
            )
        )
      val response = model.generateContent("Find bookstores in my area.")
      validateMapsGrounding(response)
    }
  }

  companion object {

    fun validateMapsGrounding(response: GenerateContentResponse) {
      response.candidates.isEmpty() shouldBe false
      response.candidates[0].groundingMetadata?.groundingChunks?.none { it.maps != null } shouldBe
        false
    }
    @JvmStatic
    fun setupModel(config: ToolConfig = ToolConfig()): GenerativeModel {
      val model =
        FirebaseAI.getInstance(app(), GenerativeBackend.vertexAI())
          .generativeModel(
            modelName = "gemini-2.5-flash",
            toolConfig = config,
            tools = listOf(Tool.googleMaps()),
          )
      return model
    }
  }
}
