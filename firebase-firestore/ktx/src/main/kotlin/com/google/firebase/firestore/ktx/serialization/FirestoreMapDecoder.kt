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

import com.google.firebase.firestore.DocumentReference
import java.lang.IllegalArgumentException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

abstract class FirestoreAbstractDecoder(
    private val nestedObject: Any,
    private val docRef: DocumentReference
) : AbstractDecoder() {

    protected var elementIndex: Int = 0
    protected var isCurrentDecodeElementNotNull: Boolean = true
    protected lateinit var decodedElementDataType: SerialKind

    /**
     * Returns a list of values that need to be decoded as fields of the custom object.
     */
    abstract val decodeValueList: List<Any?>

    /**
     * Decodes an enum field by returning its index in the enum object's descriptor.
     */
    final override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val decodedEnumFieldName = decodeValueList.elementAt(elementIndex - 1)
        // TODO: Add a EnumNamingProperties parameter, and convert the decodedEnumFieldName based on it
        // i.e. case insensitive, snake_case match camelCase, etc
        val enumFieldNames = enumDescriptor.elementNames.toList()
        return enumFieldNames.indexOf(decodedEnumFieldName)
    }

    /**
     * Returns true if the current element being decoded is not null. [decodeNull] method will be used instead of [decodeValue] in case this method returns false.
     */
    final override fun decodeNotNullMark(): Boolean {
        val result = isCurrentDecodeElementNotNull
        isCurrentDecodeElementNotNull = true
        return result
    }

    final override fun decodeValue(): Any {
        println("~".repeat(60))
        println("calling decode value")
        val element = decodeValueList.elementAt(elementIndex - 1) ?: throw IllegalArgumentException(
            "Can not assign a null value to a non-null field."
        )
        return when (decodedElementDataType) {
            is PrimitiveKind.INT -> return (element as Long).toInt() // firestore saves Int as Long
            is PrimitiveKind.FLOAT -> return (element as Double).toFloat() // firestore saves Float as Double
            else -> element
        }
    }

    final override val serializersModule: SerializersModule = EmptySerializersModule

    final override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val innerCompositeObject = getCompositeObject(elementIndex)
        when (descriptor.kind) {
            is StructureKind.CLASS -> {
                val innerMap = innerCompositeObject as Map<String, Any>
                return FirestoreMapDecoder(innerMap, docRef)
            }
            is StructureKind.LIST -> {
                val innerList = innerCompositeObject as List<Any>
                return FirestoreListDecoder(innerList, docRef)
            }
            else -> {
                throw Exception(
                    "Incorrect format of nested data provided: <$innerCompositeObject>"
                )
            }
        }
    }

    private fun getCompositeObject(elementIndex: Int): Any? {
        return when (elementIndex) {
            0 -> nestedObject // the custom object is the first Structure to decode
            else -> decodeValueList[elementIndex - 1]
        }
    }
}

class FirestoreMapDecoder(
    nestedObject: Map<String, Any?>,
    docRef: DocumentReference
) :
    FirestoreAbstractDecoder(
        nestedObject,
        docRef
    ) {

    private val nestedMap = nestedObject
    private val decodeNameList: List<String>
    override val decodeValueList: List<Any?>

    /**
     * Returns a list of keys and values that need to be decoded as fields of the custom object.
     */
    init {
        val (decodeNameList, decodeValueList) = nestedObject.toList().unzip()
        this.decodeNameList = decodeNameList
        this.decodeValueList = decodeValueList
    }

    /**
     * Returns the index of the element to be decoded. Index represents a position of the current element in the serial descriptor element that can be found with [SerialDescriptor.getElementIndex].
     * Additional to the element index, this method also returns [CompositeDecoder.DECODE_DONE] to indicate the decoding process is finished, and returns [CompositeDecoder.UNKNOWN_NAME] to indicate that the element to be decode is not in [descriptor].
     */
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == nestedMap.size) return CompositeDecoder.DECODE_DONE
        val decodeElementName = decodeNameList[elementIndex]
        isCurrentDecodeElementNotNull = decodeValueList[elementIndex] != null
        elementIndex++
        decodedElementDataType =
            descriptor.getElementDescriptor(descriptor.getElementIndex(decodeElementName)).kind
        return descriptor.getElementIndex(decodeElementName) // TODO: this can return [CompositeDecoder.UNKNOWN_NAME], if ignoreOnExtraProperties, loop to next element that is not [CompositeDecoder.UNKNOWN_NAME]
    }
}

class FirestoreListDecoder(nestedObject: List<Any>, docRef: DocumentReference) :
    FirestoreAbstractDecoder(
        nestedObject, docRef
    ) {
    private val nestedList = nestedObject
    override val decodeValueList = nestedObject

    /**
     * Returns the index of the element to be decoded. Index represents a position of the current element in the serial descriptor element that can be found with [SerialDescriptor.getElementIndex].
     * Additional to the element index, this method also returns [CompositeDecoder.DECODE_DONE] to indicate the decoding process is finished.
     */
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == nestedList.size) return CompositeDecoder.DECODE_DONE
        isCurrentDecodeElementNotNull = decodeValueList[elementIndex] != null
        decodedElementDataType = descriptor.getElementDescriptor(elementIndex).kind
        return elementIndex++
    }
}

fun <T> decodeFromMap(
    map: Map<String, Any?>,
    deserializer: DeserializationStrategy<T>,
    docRef: DocumentReference
): T {
    val decoder: Decoder = FirestoreMapDecoder(map, docRef)
    return decoder.decodeSerializableValue(deserializer)
}

inline fun <reified T> decodeFromMap(map: Map<String, Any?>, docRef: DocumentReference): T =
    decodeFromMap(map, serializer(), docRef)

// TODO: To be continue with https://github.com/firebase/firebase-android-sdk/blob/ywmei/serialization-dev/firestore-kl-serialization/app/src/main/java/com/example/firestore_kotlin_serialization/NestedMapDecoder.kt
