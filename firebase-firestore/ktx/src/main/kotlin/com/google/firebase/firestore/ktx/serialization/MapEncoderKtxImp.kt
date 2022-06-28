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

import com.google.firebase.components.Component
import com.google.firebase.firestore.util.MapEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class MapEncoderKtxImp : MapEncoder {

    /**
     * Encodes a custom serializable object to a nested map of Firebase firestore primitive types
     */
    override fun encode(value: Any): MutableMap<String, Any?> {
        val serializer = serializer(value.javaClass)
        return encodeToMap(serializer, value)
    }

    /**
     * Returns true if the custom object, value, is annotated with @[Serializable] annotation, and
     * contains only firestore Ktx serialization annotations.
     *
     * <p>This method will return false if any of the Java style annotations (like @[DocumentId], @
     * [ServerTimestamp], etc) is applied to this custom object's field.
     */
    override fun isAbleToBeEncoded(value: Any): Boolean {
        if (!isSerializable(value)) return false
        // TODO: Recursively check each of its field to make sure any of the filed contains java
        // style annotations
        return true
    }

    /**
     * Provides the concrete component that implements the [MapEncoder] interface to the component
     * registrar.
     */
    fun component(): Component<MapEncoderKtxImp?> {
        return Component.builder(MapEncoderKtxImp::class.java, MapEncoder::class.java)
            .factory { MapEncoderKtxImp() }
            .build()
    }

    /** Return true if the custom object, value, is annotated with @[Serializable] annotation. */
    private fun isSerializable(value: Any): Boolean {
        val annotations = value.javaClass.annotations
        val result = annotations.indexOfFirst { it.annotationClass == Serializable::class }
        return result != -1
    }
}
