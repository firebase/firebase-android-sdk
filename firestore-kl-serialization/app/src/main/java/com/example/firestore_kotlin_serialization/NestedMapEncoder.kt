package com.example.firestore_kotlin_serialization

import com.example.firestore_kotlin_serialization.annotations.DocumentId
import com.google.firebase.firestore.DocumentReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
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
    private var descriptor: SerialDescriptor? = null,
) : AbstractEncoder() {

    private val ROOT_LEVEL: Int = 1

    init {
        if (depth == ROOT_LEVEL) map[ROOT_LEVEL] = mutableMapOf()
    }

    fun serializedResult() = map[ROOT_LEVEL]!!

    private var elementIndex: Int = 0

    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun encodeNull() {
        val key: String = list[elementIndex++] as String
        map[depth]!!.put(key, null)
    }

    override fun encodeValue(value: Any) {
        val elementAnnotations: List<Annotation> = descriptor!!.getElementAnnotations(elementIndex)
        val elementKind = descriptor!!.getElementDescriptor(elementIndex).kind
        val skipDocumentId = elementAnnotations?.any { it is DocumentId }
        if (skipDocumentId && elementKind != PrimitiveKind.STRING) {
            // TODO: DocumentReference is not a primitive type, so I need to make it @Serializable so I can have it
            throw IllegalArgumentException("Field is annotated with @DocumentId but is class ${elementKind} instead of String or DocumentReference.")
        }
        if (skipDocumentId && depth == ROOT_LEVEL) {
            elementIndex++ // skip encoding any field annotated with @DocumentId at root level
        } else {
            val key: String = list[elementIndex++] as String
            map[depth]!!.put(key, value)
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (depth != ROOT_LEVEL) {
            map.remove(depth--)
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        //TODO: @DocumentID and @ServerTimeStamp should not be applied on Structures
        var listOfElementsToBeEncoded: MutableList<Any> = mutableListOf()
        if (!descriptor.elementNames.toList().isNullOrEmpty()) {
            listOfElementsToBeEncoded = descriptor.elementNames.toList() as MutableList<Any>
        }

        if (depth == 0) {
            return NestedMapEncoder(
                map,
                depth + 1,
                listOfElementsToBeEncoded,
                descriptor = descriptor
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
                    depth + 1,
                    listOfElementsToBeEncoded,
                    descriptor = descriptor
                )
            }
            StructureKind.LIST -> {
                var emptyList = mutableListOf<Any>()
                val innerListKey: String = list[elementIndex++] as String
                map[depth]!!.put(innerListKey, emptyList)
                return NestListEncoder(map, depth + 0, emptyList)
            }
            else -> {
                throw Exception(
                    "Incorrect format of nested object provided: <$descriptor.kind>"
                )
            }
        }
    }
}

class NestListEncoder(
    private var map: MutableMap<Int, MutableMap<String, Any?>> = mutableMapOf(),
    private var depth: Int = 0,
    private var list: MutableList<Any> = mutableListOf(),
) : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule

    private var elementIndex: Int = 0

    override fun encodeValue(value: Any) {
        list.add(value)
        elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        when (descriptor.kind) {
            StructureKind.CLASS -> {
                var nextDepth = depth + 1
                map[nextDepth] = mutableMapOf()
                list.add(map[nextDepth]!!)
                return NestedMapEncoder(
                    map, nextDepth,
                    descriptor.elementNames.toList() as MutableList<Any>,
                    descriptor = descriptor,
                )
            }
            else -> {
                throw Exception(
                    "Incorrect format of nested object provided: <$descriptor.kind>"
                )
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

inline fun <reified T> DocumentReference.set(value: T): Unit {
    val encodedMap = encodeToMap<T>(value)
    set(encodedMap)
}

fun main() {
    @Serializable
    data class TestList(
        val list: List<String> = listOf("a", "b")
    )
}