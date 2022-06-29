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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.annotations.KThrowOnExtraProperties
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



abstract class FirestoreAbstractDecoder(
    open val nestedObject: Any = Unit,
    open var documentId: FirestoreDocument? = null
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
                        // TODO: Loop through all the properties' annotation list to replace
                        // @ServerTimestamp
                        if (annotationsOnProperty.any { it is KDocumentId }) {
                            val propertieType = descriptor.getElementDescriptor(propertyIndex).kind
                            if (
                                propertieType is PrimitiveKind.STRING
                            ) { // TODO: Need to handle DocumentReference Type as well
                                innerMap[propertyName] = documentId!!.id
                            } else {
                                throw IllegalArgumentException(
                                    "Field is annotated with @DocumentId but is class $propertieType instead of String."
                                )
                            }
                        }
                    }
                }
                return FirestoreMapDecoder(innerMap, documentId)
            }
            is StructureKind.LIST -> {
                val innerList = innerCompositeObject as? List<Any> ?: listOf()
                return FirestoreListDecoder(innerList)
            }
            else -> {
                throw Exception("Incorrect format of nested data provided: <$innerCompositeObject>")
            }
        }
    }
}

class FirestoreListDecoder(
    override val nestedObject: List<*>,
) : FirestoreAbstractDecoder() {
    private val list = nestedObject
    override val decodeValueList = list

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == list.size) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }
}

class FirestoreMapDecoder(
    override val nestedObject: Map<*, *>,
    override var documentId: FirestoreDocument?
) : FirestoreAbstractDecoder() {
    private val map = nestedObject
    override val decodeValueList = ArrayList(map.values)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == map.size) return CompositeDecoder.DECODE_DONE
        val throwOnExtraProperties: Boolean =
            descriptor.annotations.any { it is KThrowOnExtraProperties }
        while (true) {
            if (elementIndex == map.size) return CompositeDecoder.DECODE_DONE
            val decodeElementName = map.keys.elementAt(elementIndex).toString()
            val decodeElementValue = decodeValueList.elementAt(elementIndex)
            val decodeElementIndex = descriptor.getElementIndex(decodeElementName)
            currentValueNotNull = decodeElementValue != null
            elementIndex++
            if (decodeElementIndex != CompositeDecoder.UNKNOWN_NAME) {
                return decodeElementIndex
            }
            if (decodeElementIndex == CompositeDecoder.UNKNOWN_NAME && throwOnExtraProperties) {
                throw IllegalArgumentException(
                    "Can not match $decodeElementName to any properties inside of Object: ${descriptor.serialName}"
                )
            }
        }
    }
}

data class FirestoreDocument(val id: String, val documentReference: DocumentReference)

fun <T> decodeFromNestedMap(map: Map<String, Any?>, deserializer: DeserializationStrategy<T>, firestoreDocument: FirestoreDocument?): T {
    val decoder: Decoder = FirestoreMapDecoder(map, firestoreDocument)
    return decoder.decodeSerializableValue(deserializer)
}

inline fun <reified T> decodeFromNestedMap(map: Map<String, Any?>, firestoreDocument: FirestoreDocument?): T =
    decodeFromNestedMap(map, serializer(), firestoreDocument)

inline fun <reified T> DocumentSnapshot.get(): T? {
    val firestoreDocument = FirestoreDocument(this.id, this.reference)
    val objectMap = this.data // Map<String!, Any!>?
    return objectMap?.let { decodeFromNestedMap<T>(it, firestoreDocument) }
}
