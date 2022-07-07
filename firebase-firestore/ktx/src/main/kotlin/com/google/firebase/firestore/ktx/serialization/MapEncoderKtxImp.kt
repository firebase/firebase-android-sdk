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

import com.google.firebase.firestore.encoding.MapEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * (De)Encoding an custom object into(from) a nested map of primitive types. This class is intend to
 * be providing a [MapEncoder] component to other libraries at runtime.
 *
 * <p>For the deserialization process, this [MapEncoderKtxImp] is going to be used by the following
 * Java classes: [DocumentSnapshot], [QueryDocumentSnapshot], and [QuerySnapshot]. For the
 * serialization process, this [MapEncoderKtxImp] is going to be used by the following Java classes:
 * [DocumentReference], [Transaction] and [WriteBatch].
 *
 * <p><b>Note</b>: The class of the object being (de)encoded should be [Serializable], the max depth
 * of the encoded nested map should be less than 500, (de)encode a list of mixed data types, or a
 * list of list are not supported as these lists are not considered as Kotlin serializable
 * (serializers cannot be obtained at compile time).
 */
class MapEncoderKtxImp : MapEncoder {
    /**
     * Encodes a custom serializable object to a nested map of Firebase firestore primitive types.
     */
    override fun encode(value: Any): MutableMap<String, Any?> {
        val serializer = serializer(value.javaClass)
        return encodeToMap(serializer, value)
    }

    /**
     * Returns whether or not the class can be (de)encoded by the [MapEncoderKtxImp]. Returns false
     * if this class cannot be (de)encoded by this [MapEncoderKtxImp].
     *
     * @param valueType The Java class to be encoded from or decoded to.
     * @return True iff the class can be (de)encoded by the {@code MapEncoder}.
     */
    // TODO: Recursively check each of its field to make sure none of the filed contains Java
    // style annotations: @PropertyName, or @Exclude
    override fun <T : Any?> supports(valueType: Class<T>): Boolean = isSerializable(valueType)

    /**
     * Returns whether or not the class is annotated with @[Serializable]. Returns false if the data
     * type does not have @[Serializable] annotation.
     *
     * @param valueType The Java class to be encoded from or decoded to.
     * @return True iff the class has @[Serializable] annotation.
     */
    private fun <T : Any?> isSerializable(valueType: Class<T>): Boolean {
        val annotations = valueType.annotations
        return annotations.any { it.annotationClass == Serializable::class }
    }
}
