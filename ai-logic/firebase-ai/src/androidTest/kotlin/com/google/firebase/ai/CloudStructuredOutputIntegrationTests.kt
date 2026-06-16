package com.google.firebase.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.annotations.Generable
import com.google.firebase.ai.annotations.Guide
import com.google.firebase.ai.type.GenerationConfig
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
  EASY, MEDIUM, HARD
}

@Serializable
@Generable
data class Ingredient(
  @Guide(description = "The name of the ingredient")
  val name: String,
  @Guide(description = "The quantity, like '1 cup'")
  val quantity: String,
  @Guide(description = "Optional preparation step")
  val preparation: String? = null
) {
  companion object
}

@Serializable
@Generable
data class ComprehensiveRecipe(
  @Guide(description = "The title of the recipe")
  val title: String,
  @Guide(description = "The number of minutes to cook")
  val cookTimeMinutes: Int,
  @Guide(description = "A score from 0.0 to 10.0")
  val healthScore: Double,
  @Guide(description = "Is this recipe vegetarian?")
  val isVegetarian: Boolean,
  val difficulty: RecipeDifficulty,
  val ingredients: List<Ingredient>
) {
  companion object
}

@RunWith(AndroidJUnit4::class)
class CloudStructuredOutputIntegrationTests {

  @Before
  fun setUp() {
    if (FirebaseApp.getApps(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext).isEmpty()) {
      FirebaseApp.initializeApp(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
    }
  }

  private val manualIngredientSchema = JsonSchema.obj(
    properties = mapOf(
      "name" to JsonSchema.string(description = "The name of the ingredient"),
      "quantity" to JsonSchema.string(description = "The quantity, like '1 cup'"),
      "preparation" to JsonSchema.string(description = "Optional preparation step", nullable = true)
    ),
    optionalProperties = listOf("preparation"),
    clazz = Ingredient::class
  )

  private val manualRecipeSchema = JsonSchema.obj(
    properties = mapOf(
      "title" to JsonSchema.string(description = "The title of the recipe"),
      "cookTimeMinutes" to JsonSchema.integer(description = "The number of minutes to cook"),
      "healthScore" to JsonSchema.double(description = "A score from 0.0 to 10.0"),
      "isVegetarian" to JsonSchema.boolean(description = "Is this recipe vegetarian?"),
      "difficulty" to JsonSchema.enumeration(listOf("EASY", "MEDIUM", "HARD"), clazz = RecipeDifficulty::class),
      "ingredients" to JsonSchema.array(items = manualIngredientSchema)
    ),
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
        .generativeModel(
          modelName = "gemini-3.5-flash",
          generationConfig = config
        )
    val response = generativeModel.generateContent("Create a recipe for a healthy apple pie")
    val jsonString = response.text
    assertNotNull(jsonString)
    
    val recipe = jsonFormat.decodeFromString<ComprehensiveRecipe>(jsonString!!)
    assertNotNull(recipe.title)
    assertTrue(recipe.ingredients.isNotEmpty())
  }

  @Test
  fun testGenerateObjectManualSchema() = runBlocking {
    val generativeModel =
      FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())
        .generativeModel(modelName = "gemini-3.5-flash")
    
    val response = generativeModel.generateObject<ComprehensiveRecipe>(
      manualRecipeSchema,
      "Create a recipe for a healthy apple pie"
    )
    val recipe = response.getObject()
    
    assertNotNull(recipe)
    assertNotNull(recipe!!.title)
    assertTrue(recipe.ingredients.isNotEmpty())
  }

  @Test
  fun testGenerateContentKspSchema() = runBlocking {
    val config = generationConfig {
      responseMimeType = "application/json"
      responseJsonSchema = ComprehensiveRecipe.firebaseAISchema()
    }
    val generativeModel =
      FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())
        .generativeModel(
          modelName = "gemini-3.5-flash",
          generationConfig = config
        )
    val response = generativeModel.generateContent("Create a recipe for a healthy apple pie")
    val jsonString = response.text
    assertNotNull(jsonString)
    
    val recipe = jsonFormat.decodeFromString<ComprehensiveRecipe>(jsonString!!)
    assertNotNull(recipe.title)
    assertTrue(recipe.ingredients.isNotEmpty())
  }

  @Test
  fun testGenerateObjectKspSchema() = runBlocking {
    val generativeModel =
      FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())
        .generativeModel(modelName = "gemini-3.5-flash")
    
    val response = generativeModel.generateObject<ComprehensiveRecipe>(
      ComprehensiveRecipe.firebaseAISchema(),
      "Create a recipe for a healthy apple pie"
    )
    val recipe = response.getObject()
    
    assertNotNull(recipe)
    assertNotNull(recipe!!.title)
    assertTrue(recipe.ingredients.isNotEmpty())
  }
}
