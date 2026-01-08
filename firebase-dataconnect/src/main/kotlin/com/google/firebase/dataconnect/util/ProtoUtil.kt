/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.toErrorInfoImpl
import com.google.firebase.dataconnect.util.ProtoUtil.nullProtoValue
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import com.google.protobuf.listValueOrNull
import com.google.protobuf.structValueOrNull
import google.firebase.dataconnect.proto.EmulatorInfo
import google.firebase.dataconnect.proto.EmulatorIssue
import google.firebase.dataconnect.proto.EmulatorIssuesResponse
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.ServiceInfo
import java.io.BufferedWriter
import java.io.CharArrayWriter
import java.io.DataOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Holder for "global" functions related to protocol buffers.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructEncoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
internal object ProtoUtil {

  /** Calculates a SHA-512 digest of a [Struct]. */
  fun Struct.calculateSha512(): ByteArray =
    Value.newBuilder().setStructValue(this).build().calculateSha512()

  /** Calculates a SHA-512 digest of a [Value]. */
  fun Value.calculateSha512(): ByteArray {
    val digest = MessageDigest.getInstance("SHA-512")
    val out = DataOutputStream(DigestOutputStream(NullOutputStream, digest))

    val calculateDigest =
      DeepRecursiveFunction<Value, Unit> {
        val kind = it.kindCase
        out.writeInt(kind.ordinal)

        when (kind) {
          KindCase.NULL_VALUE -> {
            /* nothing to write for null */
          }
          KindCase.BOOL_VALUE -> out.writeBoolean(it.boolValue)
          KindCase.NUMBER_VALUE -> out.writeDouble(it.numberValue)
          KindCase.STRING_VALUE -> out.writeUTF(it.stringValue)
          KindCase.LIST_VALUE ->
            it.listValue.valuesList.forEachIndexed { index, elementValue ->
              out.writeInt(index)
              callRecursive(elementValue)
            }
          KindCase.STRUCT_VALUE ->
            it.structValue.fieldsMap.entries
              .sortedBy { (key, _) -> key }
              .forEach { (key, elementValue) ->
                out.writeUTF(key)
                callRecursive(elementValue)
              }
          else -> throw IllegalArgumentException("unsupported kind: $kind")
        }

        out.writeInt(kind.ordinal)
      }

    calculateDigest(this)

    return digest.digest()
  }

  fun Boolean.toValueProto(): Value = Value.newBuilder().setBoolValue(this).build()

  fun Byte.toValueProto(): Value = toInt().toValueProto()

  fun Char.toValueProto(): Value = code.toValueProto()

  fun Double.toValueProto(): Value = Value.newBuilder().setNumberValue(this).build()

  fun Float.toValueProto(): Value = toDouble().toValueProto()

  fun Int.toValueProto(): Value = toDouble().toValueProto()

  fun Long.toValueProto(): Value = toString().toValueProto()

  fun Short.toValueProto(): Value = toInt().toValueProto()

  fun String.toValueProto(): Value = Value.newBuilder().setStringValue(this).build()

  fun ListValue.toValueProto(): Value = Value.newBuilder().setListValue(this).build()

  fun Struct.toValueProto(): Value = Value.newBuilder().setStructValue(this).build()

  val nullProtoValue: Value
    get() {
      return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
    }

  /** A more convenient builder for [Struct] than [com.google.protobuf.struct]. */
  fun buildStructProto(
    initialValues: Struct? = null,
    block: StructProtoBuilder.() -> Unit
  ): Struct = StructProtoBuilder(initialValues).apply(block).build()

  /** Generates and returns a string similar to [Struct.toString] but more compact. */
  fun Struct.toCompactString(keySortSelector: ((String) -> String)? = null): String =
    Value.newBuilder().setStructValue(this).build().toCompactString(keySortSelector)

  /** Generates and returns a string similar to [Struct.toString] but more compact. */
  fun ListValue.toCompactString(keySortSelector: ((String) -> String)? = null): String =
    Value.newBuilder().setListValue(this).build().toCompactString(keySortSelector)

  /** Generates and returns a string similar to [Value.toString] but more compact. */
  fun Value.toCompactString(keySortSelector: ((String) -> String)? = null): String {
    val charArrayWriter = CharArrayWriter()
    val out = BufferedWriter(charArrayWriter)
    var indent = 0

    fun BufferedWriter.writeIndent() {
      repeat(indent * 2) { write(" ") }
    }

    val calculateCompactString =
      DeepRecursiveFunction<Value, Unit> {
        when (val kind = it.kindCase) {
          KindCase.NULL_VALUE -> out.write("null")
          KindCase.BOOL_VALUE -> out.write(if (it.boolValue) "true" else "false")
          KindCase.NUMBER_VALUE -> out.write(it.numberValue.toString())
          KindCase.STRING_VALUE -> out.write("\"${it.stringValue}\"")
          KindCase.LIST_VALUE -> {
            out.write("[")
            indent++
            it.listValue.valuesList.forEach { listElementValue ->
              out.newLine()
              out.writeIndent()
              callRecursive(listElementValue)
            }
            indent--
            out.newLine()
            out.writeIndent()
            out.write("]")
          }
          KindCase.STRUCT_VALUE -> {
            out.write("{")
            indent++
            it.structValue.fieldsMap.entries
              .sortedBy { (key, _) -> keySortSelector?.invoke(key) ?: key }
              .forEach { (structElementKey, structElementValue) ->
                out.newLine()
                out.writeIndent()
                out.write("$structElementKey: ")
                callRecursive(structElementValue)
              }
            indent--
            out.newLine()
            out.writeIndent()
            out.write("}")
          }
          else -> throw IllegalArgumentException("unsupported kind: $kind")
        }
      }

    calculateCompactString(this)

    out.close()
    return charArrayWriter.toString()
  }

  fun ExecuteQueryRequest.toCompactString(): String = toStructProto().toCompactString()

  fun ExecuteQueryRequest.toStructProto(): Struct = buildStructProto {
    put("name", name)
    put("operationName", operationName)
    if (hasVariables()) put("variables", variables)
  }

  fun ExecuteQueryResponse.toCompactString(): String = toStructProto().toCompactString()

  fun ExecuteQueryResponse.toStructProto(): Struct = buildStructProto {
    if (hasData()) put("data", data)
    putList("errors") { errorsList.forEach { add(it.toErrorInfoImpl().toString()) } }
  }

  fun ExecuteMutationRequest.toCompactString(): String = toStructProto().toCompactString()

  fun ExecuteMutationRequest.toStructProto(): Struct = buildStructProto {
    put("name", name)
    put("operationName", operationName)
    if (hasVariables()) put("variables", variables)
  }

  fun ExecuteMutationResponse.toCompactString(): String = toStructProto().toCompactString()

  fun ExecuteMutationResponse.toStructProto(): Struct = buildStructProto {
    if (hasData()) put("data", data)
    putList("errors") { errorsList.forEach { add(it.toErrorInfoImpl().toString()) } }
  }

  fun EmulatorInfo.toStructProto(): Struct = buildStructProto {
    put("version", version)
    putList("services") { servicesList.forEach { add(it.toStructProto()) } }
  }

  fun ServiceInfo.toStructProto(): Struct = buildStructProto {
    put("service_id", serviceId)
    put("connection_string", connectionString)
  }

  fun EmulatorIssuesResponse.toStructProto(): Struct = buildStructProto {
    putList("issues") { issuesList.forEach { add(it.toStructProto()) } }
  }

  fun EmulatorIssue.toStructProto(): Struct = buildStructProto {
    put("kind", kind.name)
    put("severity", severity.name)
    put("message", message)
  }

  fun ListValue.toListOfAny(): List<Any?> = valueToAnyMutualRecursion.anyFromListValue(this)

  fun Struct.toMap(): Map<String, Any?> = valueToAnyMutualRecursion.anyValueFromStruct(this)

  fun Value.toAny(): Any? = valueToAnyMutualRecursion.anyValueFromValue(this)

  fun <T> List<T>.toValueProto(): Value {
    val key = "y8czq9rh75"
    return mapOf(key to this).toStructProto().getFieldsOrThrow(key)
  }

  fun <T> List<T>.toListValueProto(): ListValue = toValueProto().listValue

  fun Map<String, Any?>.toValueProto(): Value =
    Value.newBuilder().setStructValue(toStructProto()).build()

  fun Map<String, Any?>.toStructProto(): Struct = mapToStructProtoMutualRecursion.structForMap(this)

  private val mapToStructProtoMutualRecursion =
    object {
      val listValueForList: DeepRecursiveFunction<List<*>, ListValue> = DeepRecursiveFunction {
        val listValueProtoBuilder = ListValue.newBuilder()
        it.forEach { value ->
          listValueProtoBuilder.addValues(
            when (value) {
              null -> nullProtoValue
              is Boolean -> value.toValueProto()
              is Double -> value.toValueProto()
              is String -> value.toValueProto()
              is List<*> -> callRecursive(value).toValueProto()
              is Map<*, *> -> structForMap.callRecursive(value).toValueProto()
              else ->
                throw IllegalArgumentException(
                  "unsupported type: ${value::class.qualifiedName}; " +
                    "supported types are: Boolean, Double, String, List, and Map"
                )
            }
          )
        }
        listValueProtoBuilder.build()
      }

      val structForMap: DeepRecursiveFunction<Map<*, *>, Struct> = DeepRecursiveFunction {
        val structProtoBuilder = Struct.newBuilder()
        it.entries.forEach { (untypedKey, value) ->
          val key =
            (untypedKey as? String)
              ?: throw IllegalArgumentException(
                "map keys must be string, but got: " +
                  if (untypedKey === null) "null" else untypedKey::class.qualifiedName
              )
          structProtoBuilder.putFields(
            key,
            when (value) {
              null -> nullProtoValue
              is Double -> value.toValueProto()
              is Boolean -> value.toValueProto()
              is String -> value.toValueProto()
              is List<*> -> listValueForList.callRecursive(value).toValueProto()
              is Map<*, *> -> callRecursive(value).toValueProto()
              else ->
                throw IllegalArgumentException(
                  "unsupported type: ${value::class.qualifiedName}; " +
                    "supported types are: Boolean, Double, String, List, and Map"
                )
            }
          )
        }
        structProtoBuilder.build()
      }
    }

  private val valueToAnyMutualRecursion =
    object {
      val anyFromListValue: DeepRecursiveFunction<ListValue, List<Any?>> =
        DeepRecursiveFunction { listValue ->
          buildList {
            for (element in listValue.valuesList) {
              add(anyValueFromValue.callRecursive(element))
            }
          }
        }

      val anyValueFromStruct: DeepRecursiveFunction<Struct, Map<String, Any?>> =
        DeepRecursiveFunction { struct ->
          buildMap {
            for (entry in struct.fieldsMap) {
              put(entry.key, anyValueFromValue.callRecursive(entry.value))
            }
          }
        }

      val anyValueFromValue: DeepRecursiveFunction<Value, Any?> = DeepRecursiveFunction { value ->
        when (value.kindCase) {
          KindCase.BOOL_VALUE -> value.boolValue
          KindCase.NUMBER_VALUE -> value.numberValue
          KindCase.STRING_VALUE -> value.stringValue
          KindCase.LIST_VALUE -> anyFromListValue.callRecursive(value.listValue)
          KindCase.STRUCT_VALUE -> anyValueFromStruct.callRecursive(value.structValue)
          KindCase.NULL_VALUE -> null
          else -> "ERROR: unsupported kindCase: ${value.kindCase}"
        }
      }
    }

  inline fun <reified T> encodeToStruct(value: T): Struct =
    encodeToStruct(value, serializer(), serializersModule = null)

  fun <T> encodeToStruct(
    value: T,
    serializer: SerializationStrategy<T>,
    serializersModule: SerializersModule?
  ): Struct {
    val valueProto = encodeToValue(value, serializer, serializersModule)
    if (valueProto.kindCase == KindCase.KIND_NOT_SET) {
      return Struct.getDefaultInstance()
    }
    require(valueProto.hasStructValue()) {
      "encoding produced ${valueProto.kindCase}, " +
        "but expected ${KindCase.STRUCT_VALUE} or ${KindCase.KIND_NOT_SET}"
    }
    return valueProto.structValue
  }

  inline fun <reified T> encodeToValue(value: T): Value =
    encodeToValue(value, serializer(), serializersModule = null)

  fun <T> encodeToValue(
    value: T,
    serializer: SerializationStrategy<T>,
    serializersModule: SerializersModule?
  ): Value {
    val values = mutableListOf<Value>()
    ProtoValueEncoder(emptyList(), serializersModule ?: EmptySerializersModule(), values::add)
      .encodeSerializableValue(serializer, value)
    if (values.isEmpty()) {
      return Value.getDefaultInstance()
    }
    require(values.size == 1) {
      "encoding produced ${values.size} Value objects, but expected either 0 or 1"
    }
    return values.single()
  }

  inline fun <reified T> decodeFromStruct(struct: Struct): T =
    decodeFromStruct(struct, serializer(), serializersModule = null)

  fun <T> decodeFromStruct(
    struct: Struct,
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?
  ): T {
    val protoValue = Value.newBuilder().setStructValue(struct).build()
    return decodeFromValue(protoValue, deserializer, serializersModule)
  }

  inline fun <reified T> decodeFromValue(value: Value): T =
    decodeFromValue(value, serializer(), serializersModule = null)

  fun <T> decodeFromValue(
    value: Value,
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?
  ): T {
    val decoder =
      ProtoValueDecoder(value, path = emptyList(), serializersModule ?: EmptySerializersModule())
    return decoder.decodeSerializableValue(deserializer)
  }
}

@DslMarker internal annotation class StructProtoBuilderDslMarker

@StructProtoBuilderDslMarker
internal class StructProtoBuilder(struct: Struct? = null) {
  private val builder = struct?.toBuilder() ?: Struct.newBuilder()

  fun build(): Struct = builder.build()

  fun clear() {
    builder.clearFields()
  }

  fun remove(key: String) {
    builder.removeFields(key)
  }

  fun put(key: String, value: Double?) {
    builder.putFields(key, value?.toValueProto() ?: nullProtoValue)
  }

  fun put(key: String, value: Int?) {
    builder.putFields(key, value?.toValueProto() ?: nullProtoValue)
  }

  fun put(key: String, value: Boolean?) {
    builder.putFields(key, value?.toValueProto() ?: nullProtoValue)
  }

  fun put(key: String, value: String?) {
    builder.putFields(key, value?.toValueProto() ?: nullProtoValue)
  }

  fun put(key: String, value: ListValue?) {
    builder.putFields(key, value?.toValueProto() ?: nullProtoValue)
  }

  fun putList(key: String, block: ListValueProtoBuilder.() -> Unit) {
    val initialValue = builder.getFieldsOrDefault(key, Value.getDefaultInstance()).listValueOrNull
    builder.putFields(key, ListValueProtoBuilder(initialValue).apply(block).build().toValueProto())
  }

  fun put(key: String, value: Struct?) {
    builder.putFields(key, value?.toValueProto() ?: nullProtoValue)
  }

  fun putStruct(key: String, block: StructProtoBuilder.() -> Unit) {
    val initialValue = builder.getFieldsOrDefault(key, Value.getDefaultInstance()).structValueOrNull
    builder.putFields(key, StructProtoBuilder(initialValue).apply(block).build().toValueProto())
  }

  fun putNull(key: String) {
    builder.putFields(key, nullProtoValue)
  }
}

@StructProtoBuilderDslMarker
internal class ListValueProtoBuilder(listValue: ListValue? = null) {
  private val builder = listValue?.toBuilder() ?: ListValue.newBuilder()

  fun build(): ListValue = builder.build()

  fun clear() {
    builder.clearValues()
  }

  fun removeAt(index: Int) {
    builder.removeValues(index)
  }

  fun add(value: Double?) {
    builder.addValues(value?.toValueProto() ?: nullProtoValue)
  }

  fun add(value: Int?) {
    builder.addValues(value?.toValueProto() ?: nullProtoValue)
  }

  fun add(value: Boolean?) {
    builder.addValues(value?.toValueProto() ?: nullProtoValue)
  }

  fun add(value: String?) {
    builder.addValues(value?.toValueProto() ?: nullProtoValue)
  }

  fun add(value: ListValue?) {
    builder.addValues(value?.toValueProto() ?: nullProtoValue)
  }

  fun addList(block: ListValueProtoBuilder.() -> Unit) {
    builder.addValues(ListValueProtoBuilder().apply(block).build().toValueProto())
  }

  fun add(value: Struct?) {
    builder.addValues(value?.toValueProto() ?: nullProtoValue)
  }

  fun addStruct(block: StructProtoBuilder.() -> Unit) {
    builder.addValues(StructProtoBuilder().apply(block).build().toValueProto())
  }

  fun addNull() {
    builder.addValues(nullProtoValue)
  }
}
