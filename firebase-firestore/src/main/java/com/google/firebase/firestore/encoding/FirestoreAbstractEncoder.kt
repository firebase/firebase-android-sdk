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

package com.google.firebase.firestore.encoding

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.util.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An abstract interface of [Encoder] that provides a method to encode Firestore supported non-primitive types ([DocumentReference], [Timestamp], [Date] and [GeoPoint]).
 */
interface FirestoreAbstractEncoder : Encoder {
    fun <T : Any> encodeFirestoreNativeDataType(value: T)
}

/**
 * An abstract interface of [Decoder] that provides a method to decode Firestore supported non-primitive types ([DocumentReference], [Timestamp], [Date] and [GeoPoint]).
 */
interface FirestoreAbstractDecoder : Decoder {
    fun decodeFirestoreNativeDataType(): Any
}