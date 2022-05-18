package com.example.firestore_kotlin_serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

abstract class NestDecoder(
    open val nestedObject: Any = Unit,
    open var elementsCount: Int = 0,
) : AbstractDecoder() {

    var elementIndex: Int = 0

    abstract val decodeValueList: List<*>

    final override fun decodeValue(): Any = decodeValueList.elementAt(elementIndex - 1)!!

    final override val serializersModule: SerializersModule = EmptySerializersModule

    final override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
        decodeInt().also { elementsCount = it }

    final override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        var innerCompositeObject: Any?

        if (elementIndex == 0) {
            innerCompositeObject = nestedObject
        } else {
            innerCompositeObject = decodeValueList.elementAt(elementIndex - 1)
        }

        return when (innerCompositeObject) {
            is Map<*, *> -> {
                NestedMapDecoder(innerCompositeObject, descriptor.elementsCount)
            }
            is List<*> -> {
                NestedListDecoder(innerCompositeObject, innerCompositeObject.size)
            }
            else -> {
                throw Exception(
                    "Incorrect format of nested data provided: <$innerCompositeObject>"
                )
            }
        }
    }
}

class NestedListDecoder(
    override val nestedObject: List<*>,
    override var elementsCount: Int = 0,
) : NestDecoder() {
    private val list = nestedObject
    override val decodeValueList = list

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == list.size) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }
}

class NestedMapDecoder(
    override val nestedObject: Map<*, *>,
    override var elementsCount: Int = 0,
) : NestDecoder() {
    private val map = nestedObject
    override val decodeValueList = ArrayList(map.values)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == map.size) return CompositeDecoder.DECODE_DONE
        val fieldName: String = map.keys.elementAt(elementIndex++).toString()
        // TODO: if fieldName not in descriptor
        //  @IgnoreExtraProperties will return CompositeDecoder.UNKNOWN_NAME
        //  @ThrowExtraProperties will throw Exception
        return descriptor.getElementIndex(fieldName)
    }
}

fun <T> decodeFromNestedMap(map: Map<String, Any>, deserializer: DeserializationStrategy<T>): T {
    val decoder: Decoder = NestedMapDecoder(map)
    return decoder.decodeSerializableValue(deserializer)
}

inline fun <reified T> decodeFromNestedMap(map: Map<String, Any>): T =
    decodeFromNestedMap(map, serializer())
