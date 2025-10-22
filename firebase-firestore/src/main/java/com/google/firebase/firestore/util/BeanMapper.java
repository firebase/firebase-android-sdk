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

import android.os.Build;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;
import com.google.firebase.firestore.ThrowOnExtraProperties;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Base bean mapper class, providing common functionality for class and record serialization. */
abstract class BeanMapper<T> {
  private final Class<T> clazz;
  // Whether to throw exception if there are properties we don't know how to set to
  // custom object fields/setters or record components during deserialization.
  private final boolean throwOnUnknownProperties;
  // Whether to log a message if there are properties we don't know how to set to
  // custom object fields/setters or record components during deserialization.
  private final boolean warnOnUnknownProperties;
  // A set of property names that were annotated with @ServerTimestamp.
  final Set<String> serverTimestamps;
  // A set of property names that were annotated with @DocumentId. These properties will be
  // populated with document ID values during deserialization, and be skipped during
  // serialization.
  final Set<String> documentIdPropertyNames;

  BeanMapper(Class<T> clazz) {
    this.clazz = clazz;
    throwOnUnknownProperties = clazz.isAnnotationPresent(ThrowOnExtraProperties.class);
    warnOnUnknownProperties = !clazz.isAnnotationPresent(IgnoreExtraProperties.class);
    serverTimestamps = new HashSet<>();
    documentIdPropertyNames = new HashSet<>();
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

  void applyFieldAnnotations(Field field) {
    if (field.isAnnotationPresent(ServerTimestamp.class)) {
      Class<?> fieldType = field.getType();
      if (fieldType != Date.class
          && fieldType != Timestamp.class
          && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && fieldType == Instant.class)) {
        throw new IllegalArgumentException(
            "Field "
                + field.getName()
                + " is annotated with @ServerTimestamp but is "
                + fieldType
                + " instead of Date, Timestamp, or Instant.");
      }
      serverTimestamps.add(propertyName(field));
    }

    if (field.isAnnotationPresent(DocumentId.class)) {
      Class<?> fieldType = field.getType();
      ensureValidDocumentIdType("Field", "is", fieldType);
      documentIdPropertyNames.add(propertyName(field));
    }
  }

  static String propertyName(Field field) {
    String annotatedName = annotatedName(field);
    return annotatedName != null ? annotatedName : field.getName();
  }

  static String annotatedName(AnnotatedElement obj) {
    if (obj.isAnnotationPresent(PropertyName.class)) {
      PropertyName annotation = obj.getAnnotation(PropertyName.class);
      return annotation.value();
    }

    return null;
  }

  static void ensureValidDocumentIdType(String fieldDescription, String operation, Type type) {
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
