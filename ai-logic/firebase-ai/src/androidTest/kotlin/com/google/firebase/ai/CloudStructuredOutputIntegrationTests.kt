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
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.annotations.Generable
import com.google.firebase.ai.annotations.Guide
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
enum class RecipeDifficulty {
  EASY,
  MEDIUM,
  HARD
}

@Serializable
@Generable
data class Ingredient(
  @Guide(description = "The name of the ingredient") val name: String,
  @Guide(description = "The quantity, like '1 cup'") val quantity: String,
  @Guide(description = "Optional preparation step") val preparation: String? = null
) {
  companion object
}

@Serializable
@Generable
data class ComprehensiveRecipe(
  @Guide(description = "The title of the recipe", format = "recipe-title-format") val title: String,
  @Guide(description = "The number of minutes to cook", minimum = 5.0, maximum = 120.0)
  val cookTimeMinutes: Int,
  @Guide(description = "A score from 0.0 to 10.0") val healthScore: Double,
  @Guide(description = "Is this recipe vegetarian?") val isVegetarian: Boolean? = null,
  @Guide(description = "The difficulty of the recipe") val difficulty: RecipeDifficulty,
  @Guide(
    description = "Category of the recipe",
    enumValues = ["Dessert", "Main Course", "Appetizer", "Beverage"]
  )
  val category: String,
  @Guide(description = "The ingredients needed", minItems = 2, maxItems = 10)
  val ingredients: List<Ingredient>
) {
  companion object
}

@RunWith(AndroidJUnit4::class)
class CloudStructuredOutputIntegrationTests {

  @Before
  fun setUp() {
    if (
      FirebaseApp.getApps(
          androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        )
        .isEmpty()
    ) {
      FirebaseApp.initializeApp(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
      )
    }
  }

  private val manualIngredientSchema =
    JsonSchema.obj(
      properties =
        mapOf(
          "name" to JsonSchema.string(description = "The name of the ingredient"),
          "quantity" to JsonSchema.string(description = "The quantity, like '1 cup'"),
          "preparation" to
            JsonSchema.string(description = "Optional preparation step", nullable = true)
        ),
      optionalProperties = listOf("preparation"),
      clazz = Ingredient::class
    )

  private val manualRecipeSchema =
    JsonSchema.obj(
      properties =
        mapOf(
          "title" to
            JsonSchema.string(
              description = "The title of the recipe",
              format = com.google.firebase.ai.type.StringFormat.Custom("recipe-title-format")
            ),
          "cookTimeMinutes" to
            JsonSchema.integer(
              description = "The number of minutes to cook",
              minimum = 5.0,
              maximum = 120.0
            ),
          "healthScore" to JsonSchema.double(description = "A score from 0.0 to 10.0"),
          "isVegetarian" to
            JsonSchema.boolean(description = "Is this recipe vegetarian?", nullable = true),
          "difficulty" to
            JsonSchema.enumeration(
              values = listOf("EASY", "MEDIUM", "HARD"),
              clazz = RecipeDifficulty::class,
              description = "The difficulty of the recipe"
            ),
          "category" to
            JsonSchema.enumeration(
              values = listOf("Dessert", "Main Course", "Appetizer", "Beverage"),
              description = "Category of the recipe"
            ),
          "ingredients" to
            JsonSchema.array(
              items = manualIngredientSchema,
              description = "The ingredients needed",
              minItems = 2,
              maxItems = 10
            )
        ),
      optionalProperties = listOf("isVegetarian"),
      clazz = ComprehensiveRecipe::class
    )

  private val jsonFormat = Json { ignoreUnknownKeys = true }

  @Test
  fun testGenerateContentManualSchema() = runBlocking {
    val config = generationConfig {
      responseMimeType = "application/json"
      responseJsonSchema = manualRecipeSchema
    }
    val generativeModel =
      FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())
        .generativeModel(modelName = "gemini-3.5-flash", generationConfig = config)
    val response = generativeModel.generateContent("Create a recipe for a healthy apple pie")
    val jsonString = response.text
    assertNotNull(jsonString)
    println("========== jsonString: $jsonString")
    val recipe = jsonFormat.decodeFromString<ComprehensiveRecipe>(jsonString!!)
    println("========== recipe: $recipe")

    assertNotNull(recipe.title)
    assertTrue(recipe.ingredients.size in 2..10)
    assertTrue(recipe.cookTimeMinutes in 5..120)
    assertTrue(listOf("Dessert", "Main Course", "Appetizer", "Beverage").contains(recipe.category))
    assertNotNull(recipe.difficulty)
    recipe.ingredients.forEach {
      assertNotNull(it.name)
      assertNotNull(it.quantity)
    }
    // isVegetarian can be null, but we verify it's successfully parsed if present
    assertTrue(recipe.isVegetarian == null || recipe.isVegetarian is Boolean)
  }

  @Test
  fun testGenerateObjectManualSchema() = runBlocking {
    val generativeModel =
      FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())
        .generativeModel(modelName = "gemini-3.5-flash")

    val response =
      generativeModel.generateObject<ComprehensiveRecipe>(
        manualRecipeSchema,
        "Create a recipe for a healthy apple pie"
      )
    val recipe = response.getObject()
    println("========== recipe: $recipe")

    assertNotNull(recipe)
    assertNotNull(recipe!!.title)
    assertTrue(recipe.ingredients.size in 2..10)
    assertTrue(recipe.cookTimeMinutes in 5..120)
    assertTrue(listOf("Dessert", "Main Course", "Appetizer", "Beverage").contains(recipe.category))
    assertNotNull(recipe.difficulty)
    recipe.ingredients.forEach {
      assertNotNull(it.name)
      assertNotNull(it.quantity)
    }
    assertTrue(recipe.isVegetarian == null || recipe.isVegetarian is Boolean)
  }

  @Test
  fun testGenerateContentKspSchema() = runBlocking {
    val config = generationConfig {
      responseMimeType = "application/json"
      responseJsonSchema = ComprehensiveRecipe.firebaseAISchema()
    }
    val generativeModel =
      FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())
        .generativeModel(modelName = "gemini-3.5-flash", generationConfig = config)
    val response = generativeModel.generateContent("Create a recipe for a healthy apple pie")
    val jsonString = response.text
    println("========== jsonString: $jsonString")
    assertNotNull(jsonString)

    val recipe = jsonFormat.decodeFromString<ComprehensiveRecipe>(jsonString!!)
    println("========== recipe: $recipe")

    assertNotNull(recipe.title)
    assertTrue(recipe.ingredients.size in 2..10)
    assertTrue(recipe.cookTimeMinutes in 5..120)
    assertTrue(listOf("Dessert", "Main Course", "Appetizer", "Beverage").contains(recipe.category))
    assertNotNull(recipe.difficulty)
    recipe.ingredients.forEach {
      assertNotNull(it.name)
      assertNotNull(it.quantity)
    }
    assertTrue(recipe.isVegetarian == null || recipe.isVegetarian is Boolean)
  }

  @Test
  fun testGenerateObjectKspSchema() = runBlocking {
    val generativeModel =
      FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())
        .generativeModel(modelName = "gemini-3.5-flash")

    val response =
      generativeModel.generateObject<ComprehensiveRecipe>(
        ComprehensiveRecipe.firebaseAISchema(),
        "Create a recipe for a healthy apple pie"
      )
    val recipe = response.getObject()
    println("========== recipe: $recipe")

    assertNotNull(recipe)
    assertNotNull(recipe!!.title)
    assertTrue(recipe.ingredients.size in 2..10)
    assertTrue(recipe.cookTimeMinutes in 5..120)
    assertTrue(listOf("Dessert", "Main Course", "Appetizer", "Beverage").contains(recipe.category))
    assertNotNull(recipe.difficulty)
    recipe.ingredients.forEach {
      assertNotNull(it.name)
      assertNotNull(it.quantity)
    }
    assertTrue(recipe.isVegetarian == null || recipe.isVegetarian is Boolean)
  }
}
