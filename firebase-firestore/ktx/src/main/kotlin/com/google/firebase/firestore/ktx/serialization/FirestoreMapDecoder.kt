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
import com.google.firebase.firestore.ThrowOnExtraProperties
import com.google.firebase.firestore.encoding.FirestoreAbstractDecoder
import com.google.firebase.firestore.encoding.FirestoreSerializersModule
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.serialization.FirestoreKtxAbstractDecoder.Constants.START_INDEX
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * A skeleton implementation of a decoder that decodes a nested map (or list) of Firestore supported
 * types as a @[Serializable] object (or its field).
 *
 * Most of the decode* methods have default implementations that delegate to decodeValue(value:
 * Any). The [decodeElementIndex] method need to be override in each of the subclasses to support
 * decoding of a nested map or list.
 *
 * @param nestedObject The nested object (map or list) that that needs to be decoded.
 * @param docRef The [DocumentReference] where this nested object is obtained from.
 */
private abstract class FirestoreKtxAbstractDecoder(
    private val descriptor: SerialDescriptor,
    private val nestedObject: Any,
    private val docRef: DocumentReference
) : FirestoreAbstractDecoder, AbstractDecoder() {

    object Constants {
        const val START_INDEX = 0
    }
    protected var elementIndex: Int = START_INDEX

    /** The data class records the information for the element that needs to be decoded. */
    protected inner class Element(val decodeValue: Any?, serialIndex: Int) {
        val elementDescriptor: SerialDescriptor = descriptor.getElementDescriptor(serialIndex)
        val elementDataType: SerialKind = elementDescriptor.kind
    }
    protected lateinit var currentDecodeElement: Element

    /** A list of values that need to be decoded as fields of the [Serializable] object. */
    abstract val decodeValueList: List<Any?>

    /** Decodes an enum field by returning its index from the enum object's descriptor. */
    final override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val decodedEnumFieldName = currentDecodeElement.decodeValue
        // TODO: Add a EnumNamingProperties parameter, and convert decodedEnumFieldName based on it
        // i.e. case insensitive, snake_case match camelCase, etc
        val enumFieldNames = enumDescriptor.elementNames.toList()
        return enumFieldNames.indexOf(decodedEnumFieldName)
    }

    /**
     * Returns true if the current element being decoded is not null.
     *
     * In case this method returns false, [decodeNull] method will be used instead of [decodeValue]
     * to decode this null value element.
     */
    final override fun decodeNotNullMark(): Boolean {
        return currentDecodeElement.decodeValue != null
    }

    /**
     * Returns a not null primitive value that is going to be assigned as a field of the decoded
     * [Serializable] object.
     *
     * Note: Firestore saves [Int] values as [Long], and [Float] values as [Double] from the server
     * side; therefore, during decoding process, depending on the field's [SerialKind], cast might
     * be required to convert [Long] back to [Int], and to convert [Double] back to [Float].
     */
    final override fun decodeValue(): Any {
        val value = decodedElementNotNullOrThrow()
        return when (currentDecodeElement.elementDataType) {
            is PrimitiveKind.INT -> return (value as Long).toInt() // Firestore saves Int as Long
            is PrimitiveKind.FLOAT ->
                return (value as Double).toFloat() // Firestore saves Float as Double
            else -> value
        }
    }

    /**
     * Returns a not null value from [decodeValueList]. This is the value that is going to be
     * decoded as the field value of the [Serializable] object.
     *
     * Note: An [IllegalArgumentException] will be thrown if the [elementIndex] is pointing to a
     * null value from [decodeValueList].
     */
    private fun decodedElementNotNullOrThrow(): Any =
        currentDecodeElement.decodeValue
            ?: throw IllegalArgumentException(
                "Got a null value while trying to decode a not null field."
            )

    final override val serializersModule: SerializersModule =
        FirestoreSerializersModule.getFirestoreSerializersModule()

    override fun decodeFirestoreNativeDataType(): Any = decodedElementNotNullOrThrow()

    final override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val innerObject = getCompositeObject(elementIndex)
        when (descriptor.kind) {
            is StructureKind.CLASS -> {
                val innerMap =
                    (innerObject as Map<String, Any?>).run {
                        replaceKDocumentIdFieldWithCurrentDocRef(descriptor, docRef)
                    }
                return FirestoreMapDecoder(descriptor, innerMap, docRef)
            }
            is StructureKind.LIST -> {
                val innerList = innerObject as List<Any?>
                return FirestoreListDecoder(descriptor, innerList, docRef)
            }
            else -> {
                throw Exception("Incorrect format of nested data provided: <$innerObject>")
            }
        }
    }

    /**
     * Returns the nested structured object that needs to be decoded. The nested structured object
     * could be either a nested [Serializable] object or a nested List.
     */
    private fun getCompositeObject(elementIndex: Int): Any? {
        return when (elementIndex) {
            START_INDEX ->
                nestedObject // the custom object itself is the first structured obj to decode
            else -> decodeValueList[elementIndex - 1]
        }
    }
}

/** replaces the @[DocumentId] annotated field with [DocumentReference] value. */
private fun Map<String, Any?>.replaceKDocumentIdFieldWithCurrentDocRef(
    descriptor: SerialDescriptor,
    docRef: DocumentReference
): MutableMap<String, Any?> =
    this.toMutableMap().apply {
        for (propertyName in descriptor.elementNames) {
            val propertyIndex: Int = descriptor.getElementIndex(propertyName)
            val annotationsOnProperty = descriptor.getElementAnnotations(propertyIndex)
            if (annotationsOnProperty.any { it is KDocumentId }) {
                val propertyDescriptor = descriptor.getElementDescriptor(propertyIndex)
                val propertyType: SerialKind = propertyDescriptor.kind
                val propertySerialName: String = propertyDescriptor.serialName
                val docRefRegex = Regex("<DocumentReference>|__DocumentReferenceSerializer__")
                val strDocRefRegex = Regex("<String>")
                when {
                    propertyType is PrimitiveKind.STRING -> this[propertyName] = docRef.id
                    propertySerialName.contains(strDocRefRegex) -> this[propertyName] = docRef.id
                    propertySerialName.contains(docRefRegex) -> this[propertyName] = docRef
                    else ->
                        throw IllegalArgumentException(
                            "Field is annotated with @KDocumentId but is class $propertyType (with SerialName $propertySerialName) instead of String or DocumentReference."
                        )
                }
            }
        }
    }
/**
 * The entry point of Firestore Kotlin deserialization process. It decodes a nested map of Firestore
 * supported types to a [Serializable] Kotlin object.
 *
 * For a [Serializable] object, at compile time, a serializer will be generated by the Kotlin
 * serialization compiler plugin (or a custom serializer can be manually passed in). The structure
 * information of the [Serializable] object will be recorded inside of the serializer’s descriptor
 * (i.e. the name/type of each property to be encoded, the annotations on each property).
 *
 * Based on the descriptor’s information, during the decoding process, a nested map will be send to
 * the decoder. Decoder will loop through all the key-value pairs in the nested map, use the key to
 * find the target field from the [SerialDescriptor], then assign the value to the field.
 *
 * @param nestedMap The nested map that that needs to be decoded to a @[Serializable] object.
 * @param docRef The [DocumentReference] where this nested map is obtained from.
 */
private class FirestoreMapDecoder(
    descriptor: SerialDescriptor,
    val nestedMap: Map<String, Any?>,
    docRef: DocumentReference
) : FirestoreKtxAbstractDecoder(descriptor, nestedMap, docRef) {

    private val decodeNameList: List<String>
    override val decodeValueList: List<Any?>

    /** Separates keys and values that need to be decoded. */
    init {
        val (decodeNameList, decodeValueList) = nestedMap.toList().unzip()
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
     * @param descriptor The [SerialDescriptor] of the [Serializable] object.
     */
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val isThrowOnExtraProperties: Boolean =
            descriptor.annotations.any { it is ThrowOnExtraProperties }
        while (true) {
            if (elementIndex == nestedMap.size) return CompositeDecoder.DECODE_DONE
            val elementName = decodeNameList[elementIndex]
            val descriptorIndex = descriptor.getElementIndex(elementName)
            if (descriptorIndex != CompositeDecoder.UNKNOWN_NAME) {
                val elementValue = decodeValueList[elementIndex++]
                currentDecodeElement = Element(elementValue, descriptorIndex)
                return descriptorIndex
            }
            if (descriptorIndex == CompositeDecoder.UNKNOWN_NAME && isThrowOnExtraProperties) {
                throw IllegalArgumentException(
                    "Can not match $elementName to any properties inside of Object: ${descriptor.serialName}"
                )
            }
            elementIndex++
        }
    }
}

/**
 * Decodes a nested list of Firestore supported types as a field of a a list of @[Serializable]
 * objects.
 *
 * @param decodeValueList The nested list that that needs to be decoded.
 * @param docRef The [DocumentReference] where this nested object is obtained from.
 */
private class FirestoreListDecoder(
    descriptor: SerialDescriptor,
    override val decodeValueList: List<Any?>,
    docRef: DocumentReference
) : FirestoreKtxAbstractDecoder(descriptor, decodeValueList, docRef) {

    /**
     * Returns the index of the element to be decoded.
     *
     * @param descriptor The [SerialDescriptor] of the [Serializable] object where this list field
     * belongs to.
     * @return The index of the element in the list that need to be decoded.
     */
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == decodeValueList.size) return CompositeDecoder.DECODE_DONE
        currentDecodeElement = Element(decodeValueList[elementIndex], elementIndex)
        return elementIndex++
    }
}

/**
 * Returns a decoded [Serializable] object which is converted from a nested map.
 *
 * @param map A nested map that need to be decoded.
 * @param deserializer The [DeserializationStrategy] of the target [Serializable] object.
 * @param docRef The [DocumentReference] where this nested map is from.
 * @return The decoded [Serializable] object.
 */
fun <T> decodeFromMap(
    map: Map<String, Any?>,
    deserializer: DeserializationStrategy<T>,
    docRef: DocumentReference
): T {
    val decoder: Decoder = FirestoreMapDecoder(deserializer.descriptor, map, docRef)
    return decoder.decodeSerializableValue(deserializer)
}

inline fun <reified T> decodeFromMap(map: Map<String, Any?>, docRef: DocumentReference): T =
    decodeFromMap(map, serializer(), docRef)
