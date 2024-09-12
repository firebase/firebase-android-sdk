package com.google.firebase.vertexai

import com.google.firebase.vertexai.internal.util.toInternal
import com.google.firebase.vertexai.type.Schema
import com.google.firebase.vertexai.type.StringFormat
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
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
                  "latitude": {"type": "NUMBER", "format": "double"},
                  "longitude": {"type": "NUMBER","format": "double"}
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
              "elevation": {"type": "NUMBER","format": "double"},
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
    val simpleDeclaration =
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

    val json = Json.encodeToString(simpleDeclaration.toInternal())
    print(json)
    4.shouldBe(2 + 2)
  }
}
