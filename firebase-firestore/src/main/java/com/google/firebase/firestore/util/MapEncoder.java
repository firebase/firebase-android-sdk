package com.google.firebase.firestore.util;

import java.util.Map;

/**
 * Provides an interface of encoding an custom object into a nested map of primitive types. This
 * interface is registered as a component into FirestoreRegistrar. Other library, like firestore
 * Kotlin extension library can concrete implement this interface, and provide this component at
 * runtime.
 */
public interface MapEncoder {

  // encode an custom object to a nested map of firestore primitive data types
  Map<String, Object> encode(Object value);

  // use reflection to determine if this object is suitable to be encoded with the Kotlin
  // serialization plugin
  boolean isAbleToBeEncoded(Object value);
}
