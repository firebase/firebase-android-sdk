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
 * Provides an interface of encoding an custom object into a nested map of primitive types. This
 * interface is registered as a component into FirestoreRegistrar. Other library, like firestore
 * Kotlin extension library can concrete implement this interface, and provide this component at
 * runtime.
 */
public interface MapEncoder {

  // Encodes an custom object to a nested map of firestore primitive data types
  Map<String, Object> encode(Object value);

  // Uses reflection to determine if this object is suitable to be encoded with the Kotlin
  // serialization plugin
  boolean isAbleToBeEncoded(Object value);

  // Id to uniquely identify an implementation of a MapEncoder
  String mapEncoderId();
}
