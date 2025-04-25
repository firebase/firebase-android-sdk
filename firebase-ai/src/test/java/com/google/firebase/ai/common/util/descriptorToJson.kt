/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.common.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Returns a [JsonObject] representing the classes in the hierarchy of a serialization [descriptor].
 *
 * The format of the JSON object is similar to that of a Discovery Document, but restricted to these
 * fields:
 * - id
 * - type
 * - properties
 * - items
 * - $ref
 *
 * @param descriptor The [SerialDescriptor] to process.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun descriptorToJson(descriptor: SerialDescriptor): JsonObject {
  return buildJsonObject {
    put("id", simpleNameFromSerialName(descriptor.serialName))
    put("type", typeNameFromKind(descriptor.kind))
    if (descriptor.kind != StructureKind.CLASS) {
      throw UnsupportedOperationException("Only classes can be serialized to JSON for now.")
    }
    // For top-level enums, add them directly.
    if (descriptor.serialName == "FirstOrdinalSerializer") {
      addEnumDescription(descriptor)
    } else {
      addObjectProperties(descriptor)
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun JsonObjectBuilder.addListDescription(descriptor: SerialDescriptor) =
  putJsonObject("items") {
    val itemDescriptor = descriptor.elementDescriptors.first()
    val nestedIsPrimitive = (descriptor.elementsCount == 1 && itemDescriptor.kind is PrimitiveKind)
    if (nestedIsPrimitive) {
      put("type", typeNameFromKind(itemDescriptor.kind))
    } else {
      put("\$ref", simpleNameFromSerialName(itemDescriptor.serialName))
    }
  }

@OptIn(ExperimentalSerializationApi::class)
internal fun JsonObjectBuilder.addEnumDescription(descriptor: SerialDescriptor): JsonElement? {
  put("type", typeNameFromKind(SerialKind.ENUM))
  return put("enum", JsonArray(descriptor.elementNames.map { JsonPrimitive(it) }))
}

@OptIn(ExperimentalSerializationApi::class)
internal fun JsonObjectBuilder.addObjectProperties(descriptor: SerialDescriptor): JsonElement? {
  return putJsonObject("properties") {
    for (i in 0 until descriptor.elementsCount) {
      val elementDescriptor = descriptor.getElementDescriptor(i)
      val elementName = descriptor.getElementName(i)
      putJsonObject(elementName) {
        when (elementDescriptor.kind) {
          StructureKind.LIST -> {
            put("type", typeNameFromKind(elementDescriptor.kind))
            addListDescription(elementDescriptor)
          }
          StructureKind.CLASS -> {
            if (elementDescriptor.serialName.startsWith("FirstOrdinalSerializer")) {
              addEnumDescription(elementDescriptor)
            } else {
              put("\$ref", simpleNameFromSerialName(elementDescriptor.serialName))
            }
          }
          StructureKind.MAP -> {
            put("type", typeNameFromKind(elementDescriptor.kind))
            putJsonObject("additionalProperties") {
              put(
                "\$ref",
                simpleNameFromSerialName(elementDescriptor.getElementDescriptor(1).serialName)
              )
            }
          }
          else -> {
            put("type", typeNameFromKind(elementDescriptor.kind))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun typeNameFromKind(kind: SerialKind): String {
  return when (kind) {
    PrimitiveKind.BOOLEAN -> "boolean"
    PrimitiveKind.BYTE -> "integer"
    PrimitiveKind.CHAR -> "string"
    PrimitiveKind.DOUBLE -> "number"
    PrimitiveKind.FLOAT -> "number"
    PrimitiveKind.INT -> "integer"
    PrimitiveKind.LONG -> "integer"
    PrimitiveKind.SHORT -> "integer"
    PrimitiveKind.STRING -> "string"
    StructureKind.CLASS -> "object"
    StructureKind.LIST -> "array"
    SerialKind.ENUM -> "string"
    StructureKind.MAP -> "object"
    /* Only add new cases if they show up in actual test scenarios. */
    else -> TODO()
  }
}

/**
 * Extracts the name expected for a class from its serial name.
 *
 * Our serialization classes are nested within the public-facing classes, and that's the name we
 * want in the json output. There are two class names
 *
 * - `com.google.firebase.ai.type.Content.Internal` for regular scenarios
 * - `com.google.firebase.ai.type.Content.Internal.SomeClass` for nested classes in the serializer.
 *
 * For the later time we need the second to last component, for the former we need the last
 * component.
 *
 * Additionally, given that types can be nullable, we need to strip the `?` from the end of the
 * name.
 */
internal fun simpleNameFromSerialName(serialName: String): String =
  serialName
    .split(".")
    .let {
      if (it.last().startsWith("Internal")) {
        it[it.size - 2]
      } else {
        it.last()
      }
    }
    .replace("?", "")
