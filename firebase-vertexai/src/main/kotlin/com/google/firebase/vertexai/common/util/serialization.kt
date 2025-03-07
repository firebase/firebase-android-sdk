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

package com.google.firebase.vertexai.common.util

import android.util.Log
import com.google.firebase.vertexai.common.SerializationException
import kotlin.reflect.KClass
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Serializer for enums that defaults to the first ordinal on unknown types.
 *
 * Convention is that the first enum be named `UNKNOWN`, but any name is valid.
 *
 * When an unknown enum value is found, the enum itself will be logged to stderr with a message
 * about opening an issue on GitHub regarding the new enum value.
 */
internal class FirstOrdinalSerializer<T : Enum<T>>(private val enumClass: KClass<T>) :
  KSerializer<T> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("FirstOrdinalSerializer") {
      for (enumValue in enumClass.enumValues()) {
        element<String>(enumValue.toString())
      }
    }

  override fun deserialize(decoder: Decoder): T {
    val name = decoder.decodeString()
    val values = enumClass.enumValues()

    return values.firstOrNull { it.serialName == name }
      ?: values.first().also { printWarning(name) }
  }

  private fun printWarning(name: String) {
    Log.e(
      "FirstOrdinalSerializer",
      """
        |Unknown enum value found: $name"
        |This usually means the backend was updated, and the SDK needs to be updated to match it.
        |Check if there's a new version for the SDK, otherwise please open an issue on our
        |GitHub to bring it to our attention:
        |https://github.com/google/google-ai-android
       """
        .trimMargin(),
    )
  }

  override fun serialize(encoder: Encoder, value: T) {
    encoder.encodeString(value.serialName)
  }
}

/**
 * Provides the name to be used in serialization for this enum value.
 *
 * By default an enum is serialized to its [name][Enum.name], and can be overwritten by providing a
 * [SerialName] annotation.
 */
internal val <T : Enum<T>> T.serialName: String
  get() = declaringJavaClass.getField(name).getAnnotation<SerialName>()?.value ?: name

/**
 * Variant of [kotlin.enumValues] that provides support for [KClass] instances of enums.
 *
 * @throws SerializationException if the class is not a valid enum. Beyond runtime emily magic, this
 * shouldn't really be possible.
 */
internal fun <T : Enum<T>> KClass<T>.enumValues(): Array<T> =
  java.enumConstants ?: throw SerializationException("$simpleName is not a valid enum type.")

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
    if (descriptor.kind == StructureKind.CLASS) {
      if (descriptor.serialName == "FirstOrdinalSerializer") {
        put("type", typeNameFromKind(SerialKind.ENUM))
        addEnumDescription(descriptor)
      } else {
        putJsonObject("properties") {
          for (i in 0 until descriptor.elementsCount) {
            val elementDescriptor = descriptor.getElementDescriptor(i)
            val elementName = descriptor.getElementName(i)
            putJsonObject(elementName) {
              if (elementDescriptor.serialName.startsWith("FirstOrdinalSerializer")) {
                put("type", typeNameFromKind(SerialKind.ENUM))
                addEnumDescription(elementDescriptor)
              } else if (elementDescriptor.kind == StructureKind.LIST) {
                put("type", typeNameFromKind(elementDescriptor.kind))
                addListDescription(elementDescriptor)
              } else if (elementDescriptor.kind == StructureKind.CLASS) {
                put("\$ref", simpleNameFromSerialName(elementDescriptor.serialName))
              } else if (elementDescriptor.kind == StructureKind.MAP) {
                put("type", typeNameFromKind(elementDescriptor.kind))
                putJsonObject("additionalProperties") {
                  put(
                    "\$ref",
                    simpleNameFromSerialName(elementDescriptor.getElementDescriptor(1).serialName)
                  )
                }
              } else {
                put("type", typeNameFromKind(elementDescriptor.kind))
              }
            }
          }
        }
      }
    } else if (descriptor.kind == StructureKind.LIST) {
      addListDescription(descriptor)
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
internal fun JsonObjectBuilder.addEnumDescription(descriptor: SerialDescriptor) =
  put("enum", JsonArray(descriptor.elementNames.map { JsonPrimitive(it) }))

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
    else -> TODO()
  }
}

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
