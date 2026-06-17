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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KspSchemaTestHelper {

  @Test
  fun testKspSchemaMatchesExpected() {
    val kspSchema = ComprehensiveRecipe.firebaseAISchema()

    // Test root object type
    assertEquals("OBJECT", kspSchema.type)

    val properties = kspSchema.properties!!

    // Check required properties. isVegetarian is nullable so it shouldn't be required in our manual
    // schema,
    // but in Ksp it's generated as a property with nullable=true.
    assertTrue(kspSchema.required!!.contains("title"))
    assertTrue(kspSchema.required!!.contains("cookTimeMinutes"))
    assertTrue(
      kspSchema.required!!.contains("isVegetarian")
    ) // KSP sets nullable=true rather than omit from required

    // Check maxItems/minItems for ingredients array
    val ingredientsProp = properties["ingredients"]!!
    assertEquals("ARRAY", ingredientsProp.type)
    assertEquals(2, ingredientsProp.minItems)
    assertEquals(10, ingredientsProp.maxItems)

    val ingredientSchema = ingredientsProp.items!!
    assertEquals("OBJECT", ingredientSchema.type)

    // Check minimum/maximum for cookTimeMinutes
    val cookTimeProp = properties["cookTimeMinutes"]!!
    assertEquals("INTEGER", cookTimeProp.type)
    assertEquals(5.0, cookTimeProp.minimum)
    assertEquals(120.0, cookTimeProp.maximum)

    val titleProp = properties["title"]!!
    assertEquals("STRING", titleProp.type)
    assertEquals("recipe-title-format", titleProp.format)

    // Check enum for difficulty (Kotlin Enum Class)
    val difficultyProp = properties["difficulty"]!!
    assertEquals("STRING", difficultyProp.type)
    assertEquals(listOf("EASY", "MEDIUM", "HARD"), difficultyProp.enum)

    // Check enumValues strings for category
    val categoryProp = properties["category"]!!
    assertEquals("STRING", categoryProp.type)
    assertEquals(listOf("Dessert", "Main Course", "Appetizer", "Beverage"), categoryProp.enum)

    // Check nullable for isVegetarian
    val isVegetarianProp = properties["isVegetarian"]!!
    assertEquals("BOOLEAN", isVegetarianProp.type)
    assertEquals(true, isVegetarianProp.nullable)
  }
}
