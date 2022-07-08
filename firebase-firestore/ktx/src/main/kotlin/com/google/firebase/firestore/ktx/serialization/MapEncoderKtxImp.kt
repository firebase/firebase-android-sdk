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
 * [MapEncoder] implementation for @[Serializable] kotlin classes
 *
 * <p>For the deserialization process, if the target object is [Serializable], this
 * [MapEncoderKtxImp] can be used by the following Java classes: [DocumentSnapshot],
 * [QueryDocumentSnapshot], and [QuerySnapshot]. For the serialization process, if the source object
 * is [Serializable], this [MapEncoderKtxImp] can be used by the following Java classes:
 * [DocumentReference], [Transaction] and [WriteBatch].
 */
class MapEncoderKtxImp : MapEncoder {
    /** Encodes [Serializable] objects as nested maps of Firestore supported primitive types. */
    override fun encode(value: Any): MutableMap<String, Any?> {
        val serializer = serializer(value.javaClass)
        return encodeToMap(serializer, value)
    }

    /**
     * Returns whether or not the class can be (de)encoded by the [MapEncoderKtxImp].
     *
     * @param valueType The class to be encoded from or decoded to.
     * @return True iff the class can be (de)encoded.
     */
    // TODO: Recursively check each of its fields to make sure none of them contains Java
    // style annotations: @PropertyName, or @Exclude
    override fun <T : Any?> supports(valueType: Class<T>): Boolean = isSerializable(valueType)

    /**
     * Returns whether or not the class is annotated with @[Serializable].
     *
     * @param valueType The class to be encoded from or decoded to.
     * @return True iff the class has @[Serializable] annotation.
     */
    private fun <T : Any?> isSerializable(valueType: Class<T>): Boolean {
        val annotations = valueType.annotations
        return annotations.any { it.annotationClass == Serializable::class }
    }
}
