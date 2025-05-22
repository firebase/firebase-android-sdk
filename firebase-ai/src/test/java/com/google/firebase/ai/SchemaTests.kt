/*
 * Copyright 2024 Google LLC
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

import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.StringFormat
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

internal class SchemaTests {
  @Test
  fun `basic schema declaration`() {
    val schemaDeclaration =
      Schema.array(
        Schema.obj(
          mapOf(
            "name" to Schema.string(),
            "country" to Schema.string(),
            "population" to Schema.integer(),
            "coordinates" to
              Schema.obj(
                mapOf(
                  "latitude" to Schema.double(),
                  "longitude" to Schema.double(),
                )
              ),
            "hemisphere" to
              Schema.obj(
                mapOf(
                  "latitudinal" to Schema.enumeration(listOf("N", "S")),
                  "longitudinal" to Schema.enumeration(listOf("E", "W")),
                )
              ),
            "elevation" to Schema.double(),
            "isCapital" to Schema.boolean(),
            "foundingDate" to Schema.string(nullable = true, format = StringFormat.Custom("date")),
          ),
          optionalProperties = listOf("population")
        )
      )

    val expectedJson =
      """
        {
          "type": "ARRAY",
          "items": {
            "type": "OBJECT",
            "properties": {
              "name": {"type": "STRING"},
              "country": {"type": "STRING"},
              "population": {"type": "INTEGER", "format": "int32"},
              "coordinates": {
                "type": "OBJECT",
                "properties": {
                  "latitude": {"type": "NUMBER"},
                  "longitude": {"type": "NUMBER"}
                },
                "required": ["latitude","longitude"]
              },
              "hemisphere": {
                "type": "OBJECT",
                "properties": {
                  "latitudinal": {"type": "STRING","format": "enum","enum": ["N","S"]},
                  "longitudinal": {"type": "STRING","format": "enum","enum": ["E","W"]}
                },
                "required": ["latitudinal","longitudinal"]
              },
              "elevation": {"type": "NUMBER"},
              "isCapital": {"type": "BOOLEAN"},
              "foundingDate": {"type": "STRING","format": "date","nullable": true}
            },
            "required": [
              "name","country","coordinates","hemisphere","elevation",
              "isCapital","foundingDate"]
          }
        }
    """
        .trimIndent()

    Json.encodeToString(schemaDeclaration.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `full schema declaration`() {
    val schemaDeclaration =
      Schema.array(
        Schema.obj(
          description = "generic description",
          nullable = true,
          properties =
            mapOf(
              "name" to Schema.string(description = null, nullable = false, format = null),
              "country" to
                Schema.string(
                  description = "country name",
                  nullable = true,
                  format = StringFormat.Custom("custom format")
                ),
              "population" to Schema.long(description = "population count", nullable = true),
              "coordinates" to
                Schema.obj(
                  description = "coordinates",
                  nullable = true,
                  properties =
                    mapOf(
                      "latitude" to Schema.double(description = "latitude", nullable = false),
                      "longitude" to Schema.double(description = "longitude", nullable = false),
                    )
                ),
              "hemisphere" to
                Schema.obj(
                  description = "hemisphere",
                  nullable = false,
                  properties =
                    mapOf(
                      "latitudinal" to
                        Schema.enumeration(
                          listOf("N", "S"),
                          description = "latitudinal",
                          nullable = true
                        ),
                      "longitudinal" to
                        Schema.enumeration(
                          listOf("E", "W"),
                          description = "longitudinal",
                          nullable = true
                        ),
                    ),
                ),
              "elevation" to Schema.float(description = "elevation", nullable = false),
              "isCapital" to
                Schema.boolean(
                  description = "True if the city is the capital of the country",
                  nullable = false
                ),
              "foundingDate" to
                Schema.string(
                  description = "Founding date",
                  nullable = true,
                  format = StringFormat.Custom("date")
                ),
            )
        )
      )

    val expectedJson =
      """
          {
            "type": "ARRAY",
            "items": {
              "type": "OBJECT",
              "description": "generic description",
              "nullable": true,
              "properties": {
                "name": {"type": "STRING"},
                "country": {"type": "STRING", "description": "country name", "format": "custom format", "nullable": true},
                "population": {"type": "INTEGER", "description": "population count", "nullable": true},
                "coordinates": {
                  "type": "OBJECT",
                  "description": "coordinates",
                  "nullable": true,
                  "properties": {
                    "latitude": {"type": "NUMBER", "description": "latitude"},
                    "longitude": {"type": "NUMBER", "description": "longitude"}
                  },
                  "required": ["latitude","longitude"]
                },
                "hemisphere": {
                  "type": "OBJECT",
                  "description": "hemisphere",
                  "properties": {
                    "latitudinal": {
                      "type": "STRING",
                      "description": "latitudinal",
                      "format": "enum",
                      "nullable": true,
                      "enum": ["N","S"]
                    },
                    "longitudinal": {
                      "type": "STRING",
                      "description": "longitudinal",
                      "format": "enum",
                      "nullable": true,
                      "enum": ["E","W"]
                    }
                  },
                  "required": ["latitudinal","longitudinal"]
                },
                "elevation": {"type": "NUMBER", "description": "elevation", "format": "float"},
                "isCapital": {"type": "BOOLEAN", "description": "True if the city is the capital of the country"},
                "foundingDate": {"type": "STRING", "description": "Founding date", "format": "date", "nullable": true}
              },
              "required": [
                "name","country","population","coordinates","hemisphere",
                "elevation","isCapital","foundingDate"
              ]
            }
          }

      """
        .trimIndent()

    Json.encodeToString(schemaDeclaration.toInternal()).shouldEqualJson(expectedJson)
  }

  enum class TestEnum {
    RED,
    GREEN,
    BLUE
  }

  enum class TestEnumWithValues(val someValue: String) {
    RED("FF0000"),
    GREEN("00FF00"),
    BLUE("0000FF")
  }

  @Test
  fun `basic Kotlin enum class`() {
    val schema = Schema.fromEnum<TestEnum>()
    val expectedJson =
      """
      {
        "type": "STRING",
        "format": "enum",
        "enum": ["RED", "GREEN", "BLUE"]
      }
    """
        .trimIndent()

    Json.encodeToString(schema.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `basic Java enum`() {
    val schema = Schema.fromEnum(TestEnum::class.java)
    val expectedJson =
      """
      {
        "type": "STRING",
        "format": "enum",
        "enum": ["RED", "GREEN", "BLUE"]
      }
    """
        .trimIndent()

    Json.encodeToString(schema.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `Kotlin enum with values`() {
    val schema = Schema.fromEnum<TestEnumWithValues>()
    val expectedJson =
      """
      {
        "type": "STRING",
        "format": "enum",
        "enum": ["RED", "GREEN", "BLUE"]
      }
    """
        .trimIndent()

    Json.encodeToString(schema.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `Java enum with values`() {
    val schema = Schema.fromEnum(TestEnumWithValues::class.java)
    val expectedJson =
      """
      {
        "type": "STRING",
        "format": "enum",
        "enum": ["RED", "GREEN", "BLUE"]
      }
    """
        .trimIndent()

    Json.encodeToString(schema.toInternal()).shouldEqualJson(expectedJson)
  }
}
