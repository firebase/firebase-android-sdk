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

package com.google.firebase.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.ai.annotations.Generable
import com.google.firebase.ai.annotations.Guide
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(PublicPreviewAPI::class)
@RunWith(AndroidJUnit4::class)
class CloudStructuredOutputIntegrationTests {

  @Test
  fun testCloudStructuredOutput_Comprehensive_GenerateContent() {
    val app = AIModels.app()

    val cloudModel =
      FirebaseAI.getInstance(app, GenerativeBackend.googleAI())
        .generativeModel(
          modelName = "gemini-3.5-flash",
          generationConfig =
            com.google.firebase.ai.type.generationConfig {
              responseMimeType = "application/json"
              responseJsonSchema =
                JsonSchema.obj(
                  properties =
                    mapOf(
                      "productName" to JsonSchema.string("The name of the product"),
                      "price" to JsonSchema.double("Price in USD", minimum = 0.0),
                      "inStock" to JsonSchema.boolean(),
                      "category" to
                        JsonSchema.enumeration(
                          values = listOf("ELECTRONICS", "CLOTHING", "GROCERY", "TOYS")
                        ),
                      "tags" to JsonSchema.array(JsonSchema.string()),
                      "manufacturer" to
                        JsonSchema.obj(
                          properties =
                            mapOf("name" to JsonSchema.string(), "country" to JsonSchema.string())
                        ),
                      "discount" to JsonSchema.integer("Optional discount", nullable = true)
                    ),
                  optionalProperties = listOf("discount")
                )
            }
        )

    val prompt = "Create a JSON object for a Sony PlayStation 5 console."

    runBlocking {
      val response = cloudModel.generateContent(prompt)
      val jsonText = response.text
      assertThat(jsonText).isNotNull()

      val json = Json { ignoreUnknownKeys = true }
      val product = json.decodeFromString<ComprehensiveProduct>(jsonText!!)

      assertThat(product.productName).contains("PlayStation 5")
      assertThat(product.price).isGreaterThan(0.0)
      assertThat(product.category).isEqualTo(ProductCategory.ELECTRONICS)
      assertThat(product.tags).isNotEmpty()
      assertThat(product.manufacturer.name).contains("Sony")
    }
  }

  @Test
  fun testCloudStructuredOutput_Comprehensive_GenerateObject() {
    val app = AIModels.app()

    val cloudModel =
      FirebaseAI.getInstance(app, GenerativeBackend.googleAI())
        .generativeModel(modelName = "gemini-3.5-flash")

    // Uses KSP generated schema via extension function
    val schema = ComprehensiveProduct.firebaseAISchema()

    val prompt = "Create a JSON object for a Sony PlayStation 5 console."

    runBlocking {
      val response = cloudModel.generateObject(schema, prompt)
      val product = requireNotNull(response.getObject())

      assertThat(product.productName).contains("PlayStation 5")
      assertThat(product.price).isGreaterThan(0.0)
      assertThat(product.category).isEqualTo(ProductCategory.ELECTRONICS)
      assertThat(product.tags).isNotEmpty()
      assertThat(product.manufacturer.name).contains("Sony")
    }
  }
}

enum class ProductCategory {
  ELECTRONICS,
  CLOTHING,
  GROCERY,
  TOYS
}

@Serializable data class Manufacturer(val name: String, val country: String)

@Serializable
@Generable
data class ComprehensiveProduct(
  @Guide(description = "The name of the product") val productName: String,
  @Guide(description = "Price in USD", minimum = 0.0) val price: Double,
  val inStock: Boolean,
  val category: ProductCategory,
  val tags: List<String>,
  val manufacturer: Manufacturer,
  @Guide(description = "Optional discount") val discount: Int? = null
) {
  companion object
}
