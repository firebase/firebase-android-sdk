// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.ktx.serialization

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class NestedMapEncoder(
    private var map: MutableMap<Int, MutableMap<String, Any?>> = mutableMapOf(),
    private var depth: Int = 0,
    private var list: MutableList<Any> = mutableListOf(),
    private var descriptor: SerialDescriptor? = null
) : AbstractEncoder() {

    companion object {
        private const val ROOT_LEVEL: Int = 1
    }

    private var elementIndex: Int = 0

    init {
        if (depth == ROOT_LEVEL) map[ROOT_LEVEL] = mutableMapOf()
    }

    fun serializedResult() = map[ROOT_LEVEL]!!

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        enumDescriptor.elementNames.toList().let { encodeValue(it.get(index)) }

    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun encodeNull() {
        val key: String = list[elementIndex++] as String
        map[depth]!!.put(key, null)
    }

    override fun encodeValue(value: Any) {
        // TODO: Handle @DocumentId and @ServerTimestamp annotations from descriptor
        val key: String = list[elementIndex++] as String
        map[depth]!!.put(key, value)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (depth != ROOT_LEVEL) {
            map.remove(depth--)
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        // TODO: @DocumentID and @ServerTimeStamp should not be applied on Structures
        var listOfElementsToBeEncoded: MutableList<Any> = mutableListOf()
        if (!descriptor.elementNames.toList().isNullOrEmpty()) {
            listOfElementsToBeEncoded = descriptor.elementNames.toList() as MutableList<Any>
        }

        if (depth == 0) {
            return NestedMapEncoder(
                map,
                depth + 1,
                listOfElementsToBeEncoded,
                descriptor
            )
        }
        when (descriptor.kind) {
            StructureKind.CLASS -> {
                var nextDepth = depth + 1
                map[nextDepth] = mutableMapOf()
                val innerMapKey: String = list[elementIndex++] as String
                map[depth]!!.put(innerMapKey, map[nextDepth])
                return NestedMapEncoder(
                    map,
                    nextDepth,
                    listOfElementsToBeEncoded,
                    descriptor
                )
            }
            StructureKind.LIST -> {
                var emptyList = mutableListOf<Any?>()
                val innerListKey: String = list[elementIndex++] as String
                map[depth]!!.put(innerListKey, emptyList)
                return NestListEncoder(map, depth + 0, emptyList)
            }
            else -> {
                throw Exception("Incorrect format of nested object provided: <$descriptor.kind>")
            }
        }
    }
}

class NestListEncoder(
    private var map: MutableMap<Int, MutableMap<String, Any?>> = mutableMapOf(),
    private var depth: Int = 0,
    private var list: MutableList<Any?> = mutableListOf()
) : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule

    private var elementIndex: Int = 0

    override fun encodeValue(value: Any) {
        list.add(value)
        elementIndex++
    }
    override fun encodeNull() {
        list.add(null)
        elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        when (descriptor.kind) {
            StructureKind.CLASS -> {
                var nextDepth = depth + 1
                map[nextDepth] = mutableMapOf()
                list.add(map[nextDepth]!!)
                return NestedMapEncoder(
                    map,
                    nextDepth,
                    descriptor.elementNames.toList() as MutableList<Any>,
                    descriptor
                )
            }
            else -> {
                throw Exception("Incorrect format of nested object provided: <$descriptor.kind>")
            }
        }
    }
}

fun <T> encodeToMap(serializer: SerializationStrategy<T>, value: T): MutableMap<String, Any?> {
    val encoder = NestedMapEncoder()
    encoder.encodeSerializableValue(serializer, value)
    return encoder.serializedResult()
}

inline fun <reified T> encodeToMap(value: T): MutableMap<String, Any?> =
    encodeToMap(serializer(), value)
