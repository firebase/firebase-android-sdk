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

package com.google.firebase.ai.generativemodel

import com.google.common.truth.Truth.assertThat
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.Schema
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StructuredOutputSchemaTests {

  @Serializable
  data class PrimitiveData(
    val intVal: Int,
    val doubleVal: Double,
    val boolVal: Boolean,
    val strVal: String
  )

  @Serializable data class EnumData(val status: String)

  @Serializable data class CollectionData(val items: List<String>)

  @Test
  fun `translates primitive types and descriptions`() {
    val schema =
      JsonSchema.obj(
        properties =
          mapOf(
            "intVal" to JsonSchema.integer("An integer", minimum = 0.0, maximum = 100.0),
            "doubleVal" to JsonSchema.double("A double", minimum = -1.5, maximum = 1.5),
            "boolVal" to JsonSchema.boolean("A boolean"),
            "strVal" to JsonSchema.string("A string")
          ),
        clazz = PrimitiveData::class
      )

    val internalJson = schema.toInternalJson() as Schema.InternalJsonNonNull
    assertThat(internalJson.type).isEqualTo("object")

    val props = internalJson.properties!!

    val intProp = props["intVal"] as Schema.InternalJsonNonNull
    assertThat(intProp.type).isEqualTo("integer")
    assertThat(intProp.description).isEqualTo("An integer")

    val doubleProp = props["doubleVal"] as Schema.InternalJsonNonNull
    assertThat(doubleProp.type).isEqualTo("number")
    assertThat(doubleProp.description).isEqualTo("A double")

    val boolProp = props["boolVal"] as Schema.InternalJsonNonNull
    assertThat(boolProp.type).isEqualTo("boolean")

    val strProp = props["strVal"] as Schema.InternalJsonNonNull
    assertThat(strProp.type).isEqualTo("string")
  }

  @Test
  fun `translates enumerations via enumValues`() {
    val schema =
      JsonSchema.obj(
        properties =
          mapOf(
            "status" to JsonSchema.enumeration(listOf("PENDING", "ACTIVE", "CLOSED"), "Status enum")
          ),
        clazz = EnumData::class
      )

    val internalJson = schema.toInternalJson() as Schema.InternalJsonNonNull
    val statusProp = internalJson.properties!!["status"]!! as Schema.InternalJsonNonNull

    assertThat(statusProp.type).isNull()
    assertThat(statusProp.enum).containsExactly("PENDING", "ACTIVE", "CLOSED").inOrder()
  }

  @Test
  fun `translates collections and list boundaries`() {
    val schema =
      JsonSchema.obj(
        properties =
          mapOf(
            "items" to JsonSchema.array(items = JsonSchema.string(), minItems = 1, maxItems = 10)
          ),
        clazz = CollectionData::class
      )

    val internalJson = schema.toInternalJson() as Schema.InternalJsonNonNull
    val itemsProp = internalJson.properties!!["items"]!! as Schema.InternalJsonNonNull

    assertThat(itemsProp.type).isEqualTo("array")

    val itemsItems = itemsProp.items as Schema.InternalJsonNonNull
    assertThat(itemsItems.type).isEqualTo("string")
    assertThat(itemsProp.minItems).isEqualTo(1)
    assertThat(itemsProp.maxItems).isEqualTo(10)
  }
}
