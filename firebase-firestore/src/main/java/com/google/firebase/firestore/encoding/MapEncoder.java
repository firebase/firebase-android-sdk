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

package com.google.firebase.firestore.encoding;

import java.util.Map;

/**
 * Provides an interface of (de)encoding an custom object into(from) a nested map of primitive
 * types. This interface is registered as a component into FirestoreRegistrar. Other library, like
 * firestore Kotlin extension library can concretely implement this interface, and provide this
 * component at runtime.
 *
 * <p>For the deserialization process, this {@code MapEncoder} is going to be used by the following
 * Java classes: {@code DocumentSnapshot}, {@code QueryDocumentSnapshot}, and {@code QuerySnapshot}.
 * For the serialization process, this {@code MapEncoder} is going to be used by the following Java
 * classes: {@code DocumentReference}, {@code Transaction} and {@code WriteBatch}.
 *
 * <p><b>Implementation Note</b>: The concrete implementation of this interface in the firestore
 * Kotlin extension library requires the type of the object being (de)encoded to be
 * {@code @Serializable}, the max depth of the encoded nested map should be less than 500,
 * (de)encode a list of mixed data types, or a list of list are not supported as these lists are not
 * considered as Kotlin serializable (serializers cannot be obtained at compile time).
 */
public interface MapEncoder {

  /**
   * Encodes a custom object to a nested map of firestore primitive data types.
   *
   * @param value A custom object need to be encoded.
   * @return A nested map of firestore primitive data types.
   */
  Map<String, Object> encode(Object value);

  /**
   * Returns whether or not the class can be (de)encoded by the {@code MapEncoder}. Returns false if
   * this class cannot be (de)encoded by this {@code MapEncoder}.
   *
   * @param valueType The Java class to be encoded from or decoded to.
   * @return True iff the class can be (de)encoded by the {@code MapEncoder}.
   */
  <T> boolean supports(Class<T> valueType);
}
