package com.example.firestore_kotlin_serialization

import com.example.firestore_kotlin_serialization.annotations.DocumentId
import com.example.firestore_kotlin_serialization.annotations.ThrowOnExtraProperties
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

val dummyDocumentId: String = "this is a simulated documentID"

abstract class NestDecoder(
    open val nestedObject: Any = Unit,
) : AbstractDecoder() {

    var elementIndex: Int = 0

    var currentValueNotNull: Boolean = true

    abstract val decodeValueList: List<*>

    final override fun decodeValue(): Any {
        return decodeValueList.elementAt(elementIndex - 1)!!
    }

    override fun decodeNotNullMark(): Boolean {
        val result = currentValueNotNull
        currentValueNotNull = true
        return result
    }

    final override val serializersModule: SerializersModule = EmptySerializersModule

    final override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        var innerCompositeObject: Any?

        if (elementIndex == 0) {
            innerCompositeObject = nestedObject
        } else {
            innerCompositeObject = decodeValueList.elementAt(elementIndex - 1)
        }

        when (descriptor.kind) {
            is StructureKind.CLASS -> {
                val innerMap = (innerCompositeObject as? Map<String, Any> ?: mapOf()).toMutableMap()
                if (elementIndex == 0) {
                    for (propertyName in descriptor.elementNames) {
                        val propertyIndex = descriptor.getElementIndex(propertyName)
                        val annotationsOnProperty = descriptor.getElementAnnotations(propertyIndex)
                        // TODO: Loop through all the properties' annotation list to replace @ServerTimestamp
                        if (annotationsOnProperty.any { it is DocumentId }) {
                            val propertieType = descriptor.getElementDescriptor(propertyIndex).kind
                            if (propertieType is PrimitiveKind.STRING) { // TODO: Need to handle DocumentReference Type as well
                                innerMap[propertyName] = dummyDocumentId
                            } else {
                                throw IllegalArgumentException(
                                    "Field is annotated with @DocumentId but is class ${propertieType} instead of String."
                                )
                            }
                        }
                    }
                }
                return NestedMapDecoder(innerMap)
            }
            is StructureKind.LIST -> {
                val innerList = innerCompositeObject as? List<Any> ?: listOf()
                return NestedListDecoder(innerList)
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
) : NestDecoder() {
    private val map = nestedObject
    override val decodeValueList = ArrayList(map.values)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == map.size) return CompositeDecoder.DECODE_DONE
        val throwOnExtraProperties: Boolean =
            descriptor.annotations.any { it is ThrowOnExtraProperties }
        while (true) {
            if (elementIndex == map.size) return CompositeDecoder.DECODE_DONE
            val decodeElementName = map.keys.elementAt(elementIndex).toString()
            val decodeElementValue = decodeValueList.elementAt(elementIndex)
            val decodeElementIndex = descriptor.getElementIndex(decodeElementName)
            currentValueNotNull =
                decodeElementValue != null
            elementIndex++
            if (decodeElementIndex != CompositeDecoder.UNKNOWN_NAME) {
                return decodeElementIndex
            }
            if (decodeElementIndex == CompositeDecoder.UNKNOWN_NAME && throwOnExtraProperties) {
                throw IllegalArgumentException(
                    "Can not match ${decodeElementName} to any properties inside of Object: ${descriptor.serialName}"
                )
            }
        }
    }
}

fun <T> decodeFromNestedMap(map: Map<String, Any?>, deserializer: DeserializationStrategy<T>): T {
    val decoder: Decoder = NestedMapDecoder(map)
    return decoder.decodeSerializableValue(deserializer)
}

inline fun <reified T> decodeFromNestedMap(map: Map<String, Any?>): T =
    decodeFromNestedMap(map, serializer())

inline fun <reified T> DocumentSnapshot.get(): T? {
    val objectMap = this.data // Map<String!, Any!>?
    return objectMap?.let { decodeFromNestedMap<T>(it) }
}
