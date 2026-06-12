package com.google.firebase.ai.type

import com.google.common.truth.Truth.assertThat
import com.google.firebase.ai.InferenceSource
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GenerateObjectResponseTests {

  @Serializable
  data class SimpleRecipe(val recipeName: String, val ingredientCount: Int)

  @Serializable
  data class ComplexRecipe(val recipeName: String, val steps: List<String>)

@OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)
  private fun createGenerateObjectResponse(
    text: String,
    schema: JsonSchema<*>
  ): GenerateObjectResponse<*> {
    val candidate = Candidate(
      content = Content(parts = listOf(TextPart(text))),
      safetyRatings = emptyList(),
      citationMetadata = null,
      finishReason = FinishReason.STOP,
      finishMessage = null,
      groundingMetadata = null,
      urlContextMetadata = null
    )
    val generateContentResponse = GenerateContentResponse(
      candidates = listOf(candidate),
      promptFeedback = null,
      usageMetadata = null
    )
    // Cast is safe because this is just a test helper
    @Suppress("UNCHECKED_CAST")
    return GenerateObjectResponse(generateContentResponse, schema as JsonSchema<Any>)
  }

  @Test
  fun getObject_pureJson_deserializesCorrectly() {
    val jsonString = """
      {
        "recipeName": "Chocolate Chip Cookies",
        "ingredientCount": 5
      }
    """.trimIndent()
    val schema = JsonSchema.obj(emptyMap(), SimpleRecipe::class)
    
    val response = createGenerateObjectResponse(jsonString, schema)
    
    val obj = response.getObject() as SimpleRecipe
    assertThat(obj.recipeName).isEqualTo("Chocolate Chip Cookies")
    assertThat(obj.ingredientCount).isEqualTo(5)
  }

  @Test
  fun getObject_markdownJsonCodeBlock_stripsMarkdownAndDeserializes() {
    val jsonString = """
      ```json
      {
        "recipeName": "Banana Bread",
        "ingredientCount": 7
      }
      ```
    """.trimIndent()
    val schema = JsonSchema.obj(emptyMap(), SimpleRecipe::class)
    
    val response = createGenerateObjectResponse(jsonString, schema)
    
    val obj = response.getObject() as SimpleRecipe
    assertThat(obj.recipeName).isEqualTo("Banana Bread")
    assertThat(obj.ingredientCount).isEqualTo(7)
  }

  @Test
  fun getObject_markdownGenericCodeBlock_stripsMarkdownAndDeserializes() {
    val jsonString = """
      ```
      {
        "recipeName": "Apple Pie",
        "ingredientCount": 10
      }
      ```
    """.trimIndent()
    val schema = JsonSchema.obj(emptyMap(), SimpleRecipe::class)
    
    val response = createGenerateObjectResponse(jsonString, schema)
    
    val obj = response.getObject() as SimpleRecipe
    assertThat(obj.recipeName).isEqualTo("Apple Pie")
    assertThat(obj.ingredientCount).isEqualTo(10)
  }

  @Test
  fun getObject_withUnknownKeys_deserializesCorrectlyDueToLenientParsing() {
    val jsonString = """
      {
        "recipeName": "Brownies",
        "ingredientCount": 4,
        "hallucinatedExtraField": "This should be ignored by com.google.firebase.ai.common.JSON"
      }
    """.trimIndent()
    val schema = JsonSchema.obj(emptyMap(), SimpleRecipe::class)
    
    val response = createGenerateObjectResponse(jsonString, schema)
    
    val obj = response.getObject() as SimpleRecipe
    assertThat(obj.recipeName).isEqualTo("Brownies")
    assertThat(obj.ingredientCount).isEqualTo(4)
  }

  @Test
  fun getObject_complexNestedJson_deserializesCorrectly() {
    val jsonString = """
      {
        "recipeName": "Complex Cake",
        "steps": [
          "Mix flour",
          "Bake at 350",
          "Frost cake"
        ]
      }
    """.trimIndent()
    val schema = JsonSchema.obj(emptyMap(), ComplexRecipe::class)
    
    val response = createGenerateObjectResponse(jsonString, schema)
    
    val obj = response.getObject() as ComplexRecipe
    assertThat(obj.recipeName).isEqualTo("Complex Cake")
    assertThat(obj.steps).containsExactly("Mix flour", "Bake at 350", "Frost cake").inOrder()
  }

  @Test
  fun getObject_emptyText_returnsNull() {
    val jsonString = ""
    val schema = JsonSchema.obj(emptyMap(), SimpleRecipe::class)
    
    val response = createGenerateObjectResponse(jsonString, schema)
    
    val obj = response.getObject()
    assertThat(obj).isNull()
  }
}
