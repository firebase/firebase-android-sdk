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

/**
 * A skeleton implementation of a decoder that decodes a nested map of firestore primitive types as
 * a custom @Serializable Kotlin object. SDK users don’t need to directly interact with
 * [FirestoreAbstractDecoder].
 *
 * <p>Most of the decode* methods have default implementation that delegates decodeValue(value: Any)
 * as TargetType. The [decodeElementIndex] method need to be override in its of the subclasses to
 * support decoding map or list.
 *
 * @param nestedObject The nested object that that needs to be decoded.
 * @param docRef The [DocumentReference] where this nested object is obtained from.
 */
private abstract class FirestoreAbstractDecoder(
    private val nestedObject: Any,
    private val docRef: DocumentReference
) : AbstractDecoder() {

    protected val START_INDEX = 0
    protected var elementIndex: Int = START_INDEX
    protected var isCurrentDecodeElementNotNull: Boolean = true
    protected lateinit var decodedElementDataType: SerialKind

    /** A list of values that need to be decoded as fields of the custom object. */
    abstract val decodeValueList: List<Any?>

    /** Decodes an enum field by returning its index in the enum object's descriptor. */
    final override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val decodedEnumFieldName = decodeValueList.elementAt(elementIndex - 1)
        // TODO: Add a EnumNamingProperties parameter, and convert decodedEnumFieldName based on it
        // i.e. case insensitive, snake_case match camelCase, etc
        val enumFieldNames = enumDescriptor.elementNames.toList()
        return enumFieldNames.indexOf(decodedEnumFieldName)
    }

    /**
     * Returns true if the current element being decoded is not null. In case this method returns
     * false, [decodeNull] method will be used instead of [decodeValue] to decode this element.
     */
    final override fun decodeNotNullMark(): Boolean {
        val result = isCurrentDecodeElementNotNull
        isCurrentDecodeElementNotNull = true
        return result
    }

    /**
     * Returns a not null primitive value that is going to be assigned as a field of the decoded
     * custom class.
     *
     * <p>Note: Firestore saves Int field as Long, and Float field as Double from the server side;
     * therefore, during decoding process, depending on the field's [SerialKind], cast might be
     * required to convert Long back to Int, and to convert Double back to Float.
     *
     * @return a primitive value that is going to be assigned as a field value of the decoded custom
     * class.
     */
    final override fun decodeValue(): Any {
        val element: Any = validateDecodedElementNotNull()
        return when (decodedElementDataType) {
            is PrimitiveKind.INT -> return (element as Long).toInt() // firestore saves Int as Long
            is PrimitiveKind.FLOAT ->
                return (element as Double).toFloat() // firestore saves Float as Double
            else -> element
        }
    }

    /**
     * Returns a not null value from [decodeValueList], this is the value that is going to be
     * decoded as the field value of the custom object.
     *
     * <p>An [IllegalArgumentException] will be thrown if get a null value from [decodeValueList].
     */
    private fun validateDecodedElementNotNull(): Any =
        decodeValueList.elementAt(elementIndex - 1)
            ?: throw IllegalArgumentException(
                "Got a null value while trying to decode a not null field."
            )

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
                throw Exception("Incorrect format of nested data provided: <$innerCompositeObject>")
            }
        }
    }

    /**
     * Returns the inner structured object that need to be decoded. The inner structured object
     * could be neither a nested Class object or a nested List object.
     */
    private fun getCompositeObject(elementIndex: Int): Any? {
        return when (elementIndex) {
            START_INDEX ->
                nestedObject // the custom object itself is the first structured obj to decode
            else -> decodeValueList[elementIndex - 1]
        }
    }
}

/**
 * The entry point of Firestore Kotlin deserialization process. It decodes a nested map of firestore
 * primitive types to a custom @Serializable Kotlin object. SDK users don’t need to directly
 * interact with [FirestoreMapDecoder], this entry point will be embedded into existing API (no new
 * API need to be introduced).
 *
 * <p>For a custom @Serializable object, at compile time, a serializer will be generated by the
 * Kotlin serialization compiler plugin (or a custom serializer can be manually passed in). The
 * structure information of the custom @Serializable object will be recorded inside of the
 * serializer’s descriptor (i.e. the name/type of each property to be encoded, the annotations on
 * each property).
 *
 * <p>Based on the descriptor’s information, during the decoding process, a nested map will be fed
 * to the decoder. Decoder will loop through all the key-value pairs in the nested map, use the key
 * to find the target field from the [SerialDescriptor] where the value should be assigned to.
 *
 * @param nestedObject The nested map that that needs to be decoded to a @Serializable object.
 * @param docRef The [DocumentReference] where this nested map is obtained from.
 */
private class FirestoreMapDecoder(nestedObject: Map<String, Any?>, docRef: DocumentReference) :
    FirestoreAbstractDecoder(nestedObject, docRef) {

    private val nestedMap = nestedObject
    private val decodeNameList: List<String>
    override val decodeValueList: List<Any?>

    /** Returns a list of keys and values that need to be decoded as fields of the custom object. */
    init {
        val (decodeNameList, decodeValueList) = nestedObject.toList().unzip()
        this.decodeNameList = decodeNameList
        this.decodeValueList = decodeValueList
    }

    /**
     * Returns the index of the element to be decoded. Index represents a position of the current
     * element in the serial descriptor element that can be found with
     * [SerialDescriptor.getElementIndex]. Additional to the element index, this method also returns
     * [CompositeDecoder.DECODE_DONE] to indicate the decoding process is finished, and returns
     * [CompositeDecoder.UNKNOWN_NAME] to indicate that the element to be decode is not in
     * [descriptor].
     *
     * @param descriptor The [SerialDescriptor] of the custom object that contains the meta data for
     * each of its field.
     * @return The index of the element that need to be decoded.
     */
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == nestedMap.size) return CompositeDecoder.DECODE_DONE
        val decodeElementName = decodeNameList[elementIndex]
        isCurrentDecodeElementNotNull = decodeValueList[elementIndex] != null
        elementIndex++
        decodedElementDataType =
            descriptor.getElementDescriptor(descriptor.getElementIndex(decodeElementName)).kind
        return descriptor.getElementIndex(decodeElementName)
        // TODO: To support annotation @IgnoreOnExtraProperties, loop this method
        // to next element which descriptor.getElementIndex() does not return
        // [CompositeDecoder.UNKNOWN_NAME]
    }
}

/**
 * Decodes a nested list of firestore primitive types as a field of a custom @Serializable Kotlin
 * object. SDK users don’t need to directly interact with [FirestoreListDecoder].
 *
 * <p>For a custom @Serializable object, at compile time, a serializer will be generated by the
 * Kotlin serialization compiler plugin (or a custom serializer can be manually passed in). The
 * structure information of the custom @Serializable object will be recorded inside of the
 * serializer’s descriptor (i.e. the name/type of each property to be encoded, the annotations on
 * each property).
 *
 * <p>Based on the descriptor’s information, during the decoding process, if a nested list need to
 * be decoded as a field of the custom class, this decoder will loop through all the values in the
 * list, and pass these values to the deserializer, so that they can be assigned to the custom
 * object. The type of the list elements can be either firestore primitives or @Serializable custom
 * classes.
 *
 * @param nestedObject The nested list that that needs to be decoded to a field of a @Serializable
 * object.
 * @param docRef The [DocumentReference] where this nested object is obtained from.
 */
private class FirestoreListDecoder(nestedObject: List<Any>, docRef: DocumentReference) :
    FirestoreAbstractDecoder(nestedObject, docRef) {
    private val nestedList = nestedObject
    override val decodeValueList = nestedObject

    /**
     * Returns the index of the element to be decoded. Index represents a position of the current
     * element in the serial descriptor element that can be found with
     * [SerialDescriptor.getElementIndex]. Additional to the element index, this method also returns
     * [CompositeDecoder.DECODE_DONE] to indicate the decoding process is finished.
     *
     * @param descriptor The [SerialDescriptor] of the custom object that contains the meta data for
     * each of its field.
     * @return The index of the element that need to be decoded.
     */
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == nestedList.size) return CompositeDecoder.DECODE_DONE
        isCurrentDecodeElementNotNull = decodeValueList[elementIndex] != null
        decodedElementDataType = descriptor.getElementDescriptor(elementIndex).kind
        return elementIndex++
    }
}

/**
 * Returns the decoded @[Serializable] Kotlin object converted from a nested map.
 *
 * @param map A nested map that need to be decoded.
 * @param deserializer The [DeserializationStrategy] of the Custom object.
 * @param docRef The [DocumentReference] where this nested map is from.
 * @return The decoded @[Serializable] Kotlin object.
 */
fun <T> decodeFromMap(
    map: Map<String, Any?>,
    deserializer: DeserializationStrategy<T>,
    docRef: DocumentReference
): T {
    val decoder: Decoder = FirestoreMapDecoder(map, docRef)
    return decoder.decodeSerializableValue(deserializer)
}

/**
 * Returns the decoded @[Serializable] Kotlin object converted from a nested map.
 *
 * @param map A nested map that need to be decoded.
 * @param docRef The [DocumentReference] where this nested map is from.
 * @return The decoded @[Serializable] Kotlin object.
 */
inline fun <reified T> decodeFromMap(map: Map<String, Any?>, docRef: DocumentReference): T =
    decodeFromMap(map, serializer(), docRef)
