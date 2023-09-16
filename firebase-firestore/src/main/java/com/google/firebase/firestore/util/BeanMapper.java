/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore.util;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ThrowOnExtraProperties;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/** Base bean mapper class, providing common functionality for class and record serialization. */
abstract class BeanMapper<T> {
  private final Class<T> clazz;
  // Whether to throw exception if there are properties we don't know how to set to
  // custom object fields/setters or record components during deserialization.
  private final boolean throwOnUnknownProperties;
  // Whether to log a message if there are properties we don't know how to set to
  // custom object fields/setters or record components during deserialization.
  private final boolean warnOnUnknownProperties;

  BeanMapper(Class<T> clazz) {
    this.clazz = clazz;
    throwOnUnknownProperties = clazz.isAnnotationPresent(ThrowOnExtraProperties.class);
    warnOnUnknownProperties = !clazz.isAnnotationPresent(IgnoreExtraProperties.class);
  }

  Class<T> getClazz() {
    return clazz;
  }

  boolean isThrowOnUnknownProperties() {
    return throwOnUnknownProperties;
  }

  boolean isWarnOnUnknownProperties() {
    return warnOnUnknownProperties;
  }

  /**
   * Serialize an object to a map.
   *
   * @param object the object to serialize
   * @param path the path to a specific field/component in an object, for use in error messages
   * @return the map
   */
  abstract Map<String, Object> serialize(T object, DeserializeContext.ErrorPath path);

  /**
   * Deserialize a map to an object.
   *
   * @param values the map to deserialize
   * @param types generic type mappings
   * @param context context information about the deserialization operation
   * @return the deserialized object
   */
  abstract T deserialize(
      Map<String, Object> values,
      Map<TypeVariable<Class<T>>, Type> types,
      DeserializeContext context);

  void ensureValidDocumentIdType(String fieldDescription, String operation, Type type) {
    if (type != String.class && type != DocumentReference.class) {
      throw new IllegalArgumentException(
          fieldDescription
              + " is annotated with @DocumentId but "
              + operation
              + " "
              + type
              + " instead of String or DocumentReference.");
    }
  }

  void verifyValidType(T object) {
    if (!clazz.isAssignableFrom(object.getClass())) {
      throw new IllegalArgumentException(
          "Can't serialize object of class "
              + object.getClass()
              + " with BeanMapper for class "
              + clazz);
    }
  }

  Type resolveType(Type type, Map<TypeVariable<Class<T>>, Type> types) {
    if (type instanceof TypeVariable) {
      Type resolvedType = types.get(type);
      if (resolvedType == null) {
        throw new IllegalStateException("Could not resolve type " + type);
      }

      return resolvedType;
    }

    return type;
  }

  void checkForDocIdConflict(
      String docIdPropertyName,
      Collection<String> deserializedProperties,
      DeserializeContext context) {
    if (deserializedProperties.contains(docIdPropertyName)) {
      String message =
          "'"
              + docIdPropertyName
              + "' was found from document "
              + context.documentRef.getPath()
              + ", cannot apply @DocumentId on this property for class "
              + clazz.getName();
      throw new RuntimeException(message);
    }
  }

  T deserialize(Map<String, Object> values, DeserializeContext context) {
    return deserialize(values, Collections.emptyMap(), context);
  }
}
