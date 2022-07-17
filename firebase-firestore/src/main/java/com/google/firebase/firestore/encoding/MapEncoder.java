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
 * Provides an interface of (de)encoding a custom object into(from) a nested map of Firestore
 * supported primitive types.
 *
 * <p><b>Implementation Note</b>: The concrete implementation of this interface in the Firestore
 * Kotlin extension library has the following restrictions: Only @{@code Serializable} objects are
 * supported; object depth, defined as objects within objects, cannot be larger than 500;
 * (de)encoding lists of mixed data types, or lists of lists is not supported as they are not
 * considered as Kotlin serializable (serializers cannot be obtained at compile time).
 */
public interface MapEncoder {

  /**
   * Encodes a object to a nested map of Firestore supported types.
   *
   * @param value An object need to be encoded.
   * @return A nested map of Firestore supported types.
   */
  Map<String, Object> encode(Object value);

  /**
   * Returns whether or not the class can be (de)encoded by the {@code MapEncoder}.
   *
   * @param valueType The class to be encoded from or decoded to.
   * @return True iff the class can be (de)encoded.
   */
  <T> boolean supports(Class<T> valueType);
}
