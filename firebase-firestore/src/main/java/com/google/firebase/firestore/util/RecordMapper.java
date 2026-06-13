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
import androidx.annotation.RequiresApi;
import com.google.firebase.firestore.FieldValue;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Serializes java records. Uses canonical record constructors and accessors only. Therefore,
 * exclusion of fields is not supported. Supports {@code DocumentId}, {@code PropertyName},
 * and {@code ServerTimestamp} annotations on record components.
 * Since java records may be desugared, and record component-related reflection methods may be missing,
 * the canonical record constructor is identified through matching of parameter names and types with fields.
 * Therefore, a mapped record must not have a custom constructor
 * with the same set of parameter names and types as the canonical one
 * (by default, only the canonical constructor's parameter names are preserved at runtime,
 * and the others' get generic runtime names,
 * but that can be changed with the {@code -parameters} compiler option).
 *
 * @author Eran Leshem
 */
@RequiresApi(api = Build.VERSION_CODES.O)
class RecordMapper<T> extends BeanMapper<T> {
  private static final Logger LOGGER = Logger.getLogger(RecordMapper.class.getName());
  private static final Class<?>[] CLASSES_ARRAY_TYPE = new Class<?>[0];

  // Below are maps to find an accessor and constructor parameter index from a given property name.
  // A property name is the name annotated by @PropertyName, if exists; or the component name.
  // See method propertyName for details.
  private final Map<String, Method> accessors = new HashMap<>();
  private final Constructor<T> constructor;
  private final Map<String, Integer> constructorParamIndexes = new HashMap<>();

  RecordMapper(Class<T> clazz) {
    super(clazz);

    constructor = getConstructor(clazz);
    Parameter[] recordComponents = constructor.getParameters();
    if (recordComponents.length == 0) {
      throw new RuntimeException("No properties to serialize found on class " + clazz.getName());
    }

    try {
      for (int i = 0; i < recordComponents.length; i++) {
        Field field = clazz.getDeclaredField(recordComponents[i].getName());
        String propertyName = propertyName(field);
        constructorParamIndexes.put(propertyName, i);
        accessors.put(propertyName, getAccessor(clazz, field));
        applyFieldAnnotations(field);
      }
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> Constructor<T> getConstructor(Class<T> clazz) {
    Map<String, Type> components =
        Arrays.stream(clazz.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .collect(Collectors.toMap(Field::getName, Field::getGenericType));
    Constructor<T> match = null;
    //noinspection unchecked
    for (Constructor<T> ctor : (Constructor<T>[]) clazz.getConstructors()) {
      Parameter[] parameters = ctor.getParameters();
      Map<String, Type> parameterTypes =
          Arrays.stream(parameters)
              .collect(Collectors.toMap(Parameter::getName, Parameter::getParameterizedType));
      if (!parameterTypes.equals(components)) {
        continue;
      }

      if (match != null) {
        throw new RuntimeException(
            String.format(
                "Multiple constructors match set of components for record %s", clazz.getName()));
      }

      match = ctor;
    }

    return match;
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

  private static Method getAccessor(Class<?> clazz, Field recordComponent) {
    try {
      Method accessor = clazz.getDeclaredMethod(recordComponent.getName(), CLASSES_ARRAY_TYPE);
      accessor.setAccessible(true);
      return accessor;
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Failed to get record component accessor", e);
    }
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
}
