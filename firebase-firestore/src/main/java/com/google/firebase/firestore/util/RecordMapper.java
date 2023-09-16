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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;

/**
 * Serializes java records. Uses automatic record constructors and accessors only. Therefore,
 * exclusion of fields is not supported. Supports DocumentId, PropertyName, and ServerTimestamp
 * annotations on record components. However, those annotations have to
 * include @Target(ElementType.RECORD_COMPONENT), so the ones defined in the
 * firebase-firestore-sdk34 module should be used, rather than the ones defined in this module.
 * Since records are not supported in JDK8, reflection is used for inspecting record metadata. This
 * class will fail to load on java versions that don't support records.
 *
 * @author Eran Leshem
 */
class RecordMapper<T> extends BeanMapper<T> {
  private static final Logger LOGGER = Logger.getLogger(RecordMapper.class.getName());
  private static final RecordInspector RECORD_INSPECTOR = new RecordInspector();

  // Below are maps to find an accessor and constructor parameter index from a given property name.
  // A property name is the name annotated by @PropertyName, if exists; or the component name.
  // See method propertyName for details.
  private final Map<String, Method> accessors = new HashMap<>();
  private final Constructor<T> constructor;
  private final Map<String, Integer> constructorParamIndexes = new HashMap<>();
  // A set of property names that were annotated with @ServerTimestamp.
  private final Set<String> serverTimestamps = new HashSet<>();
  // A set of property names that were annotated with @DocumentId. These properties will be
  // populated with document ID values during deserialization, and be skipped during
  // serialization.
  private final Set<String> documentIdPropertyNames = new HashSet<>();

  RecordMapper(Class<T> clazz) {
    super(clazz);

    constructor = RECORD_INSPECTOR.getCanonicalConstructor(clazz);

    AnnotatedElement[] recordComponents = RECORD_INSPECTOR.getRecordComponents(clazz);
    if (recordComponents.length == 0) {
      throw new RuntimeException("No properties to serialize found on class " + clazz.getName());
    }

    for (int i = 0; i < recordComponents.length; i++) {
      AnnotatedElement recordComponent = recordComponents[i];
      String propertyName = propertyName(recordComponent);
      constructorParamIndexes.put(propertyName, i);
      accessors.put(propertyName, RECORD_INSPECTOR.getAccessor(recordComponent));
      applyComponentAnnotations(recordComponent);
    }
  }

  @Override
  Map<String, Object> serialize(T object, DeserializeContext.ErrorPath path) {
    verifyValidType(object);
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Method> entry : accessors.entrySet()) {
      String property = entry.getKey();
      // Skip @DocumentId annotated properties;
      if (documentIdPropertyNames.contains(property)) {
        continue;
      }

      Object propertyValue;
      Method accessor = entry.getValue();
      try {
        propertyValue = accessor.invoke(object);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }

      Object serializedValue;
      if (serverTimestamps.contains(property) && propertyValue == null) {
        // Replace null ServerTimestamp-annotated fields with the sentinel.
        serializedValue = FieldValue.serverTimestamp();
      } else {
        serializedValue = CustomClassMapper.serialize(propertyValue, path.child(property));
      }
      result.put(property, serializedValue);
    }
    return result;
  }

  @Override
  T deserialize(
      Map<String, Object> values,
      Map<TypeVariable<Class<T>>, Type> types,
      DeserializeContext context) {
    Object[] constructorParams = new Object[constructor.getParameterTypes().length];
    Set<String> deserializedProperties = new HashSet<>(values.size());
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String propertyName = entry.getKey();
      if (accessors.containsKey(propertyName)) {
        Method accessor = accessors.get(propertyName);
        Type resolvedType = resolveType(accessor.getGenericReturnType(), types);
        DeserializeContext.ErrorPath childPath = context.errorPath.child(propertyName);
        Object value =
            CustomClassMapper.deserializeToType(
                entry.getValue(), resolvedType, context.newInstanceWithErrorPath(childPath));
        constructorParams[constructorParamIndexes.get(propertyName).intValue()] = value;
        deserializedProperties.add(propertyName);
      } else {
        String message =
            "No accessor for " + propertyName + " found on class " + getClazz().getName();
        if (isThrowOnUnknownProperties()) {
          throw new RuntimeException(message);
        }

        if (isWarnOnUnknownProperties()) {
          LOGGER.warning(message);
        }
      }
    }

    populateDocumentIdProperties(types, context, constructorParams, deserializedProperties);

    try {
      return constructor.newInstance(constructorParams);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private void applyComponentAnnotations(AnnotatedElement component) {
    if (isAnnotationPresent(component, "ServerTimestamp")) {
      Class<?> componentType = RECORD_INSPECTOR.getType(component);
      if (componentType != Date.class && componentType != Timestamp.class) {
        throw new IllegalArgumentException(
            "Component "
                + RECORD_INSPECTOR.getName(component)
                + " is annotated with @ServerTimestamp but is "
                + componentType
                + " instead of Date or Timestamp.");
      }
      serverTimestamps.add(propertyName(component));
    }

    if (isAnnotationPresent(component, "DocumentId")) {
      Class<?> type = RECORD_INSPECTOR.getType(component);
      ensureValidDocumentIdType("Component", "is", type);
      documentIdPropertyNames.add(propertyName(component));
    }
  }

  private static String propertyName(AnnotatedElement component) {
    Annotation annotation = getAnnotation(component, "PropertyName");
    if (annotation != null) {
      try {
        return (String) annotation.getClass().getMethod("value").invoke(annotation);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        throw new IllegalArgumentException("Failed to get PropertyName annotation value", e);
      }
    }

    return RECORD_INSPECTOR.getName(component);
  }

  private static boolean isAnnotationPresent(AnnotatedElement element, String annotationName) {
    return getAnnotation(element, annotationName) != null;
  }

  private static Annotation getAnnotation(
      AnnotatedElement element, String annotationName) {
    Annotation[] annotations = element.getAnnotations();
    for (Annotation annotation : annotations) {
      if (annotation.annotationType().getSimpleName().equals(annotationName)) {
        return annotation;
      }
    }

    return null;
  }

  // Populate @DocumentId annotated components. If there is a conflict (@DocumentId annotation is
  // applied to a property that is already deserialized from the firestore document)
  // a runtime exception will be thrown.
  private void populateDocumentIdProperties(
      Map<TypeVariable<Class<T>>, Type> types,
      DeserializeContext context,
      Object[] params,
      Set<String> deserialzedProperties) {
    for (String docIdPropertyName : documentIdPropertyNames) {
      checkForDocIdConflict(docIdPropertyName, deserialzedProperties, context);

      if (accessors.containsKey(docIdPropertyName)) {
        Object id;
        Type resolvedType =
            resolveType(accessors.get(docIdPropertyName).getGenericReturnType(), types);
        if (resolvedType == String.class) {
          id = context.documentRef.getId();
        } else {
          id = context.documentRef;
        }
        params[constructorParamIndexes.get(docIdPropertyName).intValue()] = id;
      }
    }
  }

  private static final class RecordInspector {
    static final Class<?>[] CLASSES_ARRAY_TYPE = new Class<?>[0];
    private final Method _getRecordComponents;
    private final Method _getName;
    private final Method _getType;
    private final Method _getAccessor;

    @SuppressWarnings("JavaReflectionMemberAccess")
    private RecordInspector() {
      try {
        _getRecordComponents = Class.class.getMethod("getRecordComponents");
        Class<?> recordComponentClass = Class.forName("java.lang.reflect.RecordComponent");
        _getName = recordComponentClass.getMethod("getName");
        _getType = recordComponentClass.getMethod("getType");
        _getAccessor = recordComponentClass.getMethod("getAccessor");
      } catch (ClassNotFoundException | NoSuchMethodException e) {
        throw new IllegalStateException(
            "Failed to access class or methods needed to support record serialization", e);
      }
    }

    private <T> Constructor<T> getCanonicalConstructor(Class<T> cls) {
      try {
        Class<?>[] paramTypes = getParamTypes(cls);
        Constructor<T> constructor = cls.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);
        return constructor;
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(e);
      }
    }

    private <T> Class<?>[] getParamTypes(Class<T> cls) {
      AnnotatedElement[] recordComponents = getRecordComponents(cls);
      List<Class<?>> types = new ArrayList<>(recordComponents.length);
      for (AnnotatedElement element: recordComponents) {
        types.add(getType(element));
      }
      return types.toArray(CLASSES_ARRAY_TYPE);
    }

    private AnnotatedElement[] getRecordComponents(Class<?> recordType) {
      try {
        return (AnnotatedElement[]) _getRecordComponents.invoke(recordType);
      } catch (InvocationTargetException | IllegalAccessException e) {
        throw new IllegalArgumentException(
            "Failed to load components of record " + recordType.getName(), e);
      }
    }

    private Class<?> getType(AnnotatedElement recordComponent) {
      try {
        return (Class<?>) _getType.invoke(recordComponent);
      } catch (InvocationTargetException | IllegalAccessException e) {
        throw new IllegalArgumentException("Failed to get record component type", e);
      }
    }

    private String getName(AnnotatedElement recordComponent) {
      try {
        return (String) _getName.invoke(recordComponent);
      } catch (InvocationTargetException | IllegalAccessException e) {
        throw new IllegalArgumentException("Failed to get record component name", e);
      }
    }

    private Method getAccessor(AnnotatedElement recordComponent) {
      try {
        Method accessor = (Method) _getAccessor.invoke(recordComponent);
        accessor.setAccessible(true);
        return accessor;
      } catch (InvocationTargetException | IllegalAccessException e) {
        throw new IllegalArgumentException("Failed to get record component accessor", e);
      }
    }
  }
}
