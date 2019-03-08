// Copyright 2018 Google LLC
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

package com.google.firebase.firestore.util;

import static com.google.firebase.firestore.util.ApiUtil.invoke;
import static com.google.firebase.firestore.util.ApiUtil.newInstance;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;
import com.google.firebase.firestore.ThrowOnExtraProperties;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Helper class to convert to/from custom POJO classes and plain Java types. */
public class CustomClassMapper {
  /** Maximum depth before we give up and assume it's a recursive object graph. */
  private static final int MAX_DEPTH = 500;

  private static final ConcurrentMap<Class<?>, BeanMapper<?>> mappers = new ConcurrentHashMap<>();

  private static void hardAssert(boolean assertion) {
    hardAssert(assertion, "Internal inconsistency");
  }

  private static void hardAssert(boolean assertion, String message) {
    if (!assertion) {
      throw new RuntimeException("Hard assert failed: " + message);
    }
  }

  /**
   * Converts a Java representation of JSON data to standard library Java data types: Map, Array,
   * String, Double, Integer and Boolean. POJOs are converted to Java Maps.
   *
   * @param object The representation of the JSON data
   * @return JSON representation containing only standard library Java types
   */
  public static Object convertToPlainJavaTypes(Object object) {
    return serialize(object);
  }

  public static Map<String, Object> convertToPlainJavaTypes(Map<?, Object> update) {
    Object converted = serialize(update);
    hardAssert(converted instanceof Map);
    @SuppressWarnings("unchecked")
    Map<String, Object> convertedMap = (Map<String, Object>) converted;
    return convertedMap;
  }

  /**
   * Converts a standard library Java representation of JSON data to an object of the provided
   * class.
   *
   * @param object The representation of the JSON data
   * @param clazz The class of the object to convert to
   * @return The POJO object.
   */
  public static <T> T convertToCustomClass(Object object, Class<T> clazz) {
    return deserializeToClass(object, clazz, ErrorPath.EMPTY);
  }

  private static <T> Object serialize(T o) {
    return serialize(o, ErrorPath.EMPTY);
  }

  @SuppressWarnings("unchecked")
  private static <T> Object serialize(T o, ErrorPath path) {
    if (path.getLength() > MAX_DEPTH) {
      throw serializeError(
          path,
          "Exceeded maximum depth of "
              + MAX_DEPTH
              + ", which likely indicates there's an object cycle");
    }
    if (o == null) {
      return null;
    } else if (o instanceof Number) {
      if (o instanceof Float) {
        return ((Float) o).doubleValue();
      } else if (o instanceof Short) {
        throw serializeError(path, "Shorts are not supported, please use int or long");
      } else if (o instanceof Byte) {
        throw serializeError(path, "Bytes are not supported, please use int or long");
      } else {
        // Long, Integer, Double
        return o;
      }
    } else if (o instanceof String) {
      return o;
    } else if (o instanceof Boolean) {
      return o;
    } else if (o instanceof Character) {
      throw serializeError(path, "Characters are not supported, please use Strings.");
    } else if (o instanceof Map) {
      Map<String, Object> result = new HashMap<>();
      for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) o).entrySet()) {
        Object key = entry.getKey();
        if (key instanceof String) {
          String keyString = (String) key;
          result.put(keyString, serialize(entry.getValue(), path.child(keyString)));
        } else {
          throw serializeError(path, "Maps with non-string keys are not supported");
        }
      }
      return result;
    } else if (o instanceof Collection) {
      if (o instanceof List) {
        List<Object> list = (List<Object>) o;
        List<Object> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
          result.add(serialize(list.get(i), path.child("[" + i + "]")));
        }
        return result;
      } else {
        throw serializeError(
            path, "Serializing Collections is not supported, please use Lists instead");
      }
    } else if (o.getClass().isArray()) {
      throw serializeError(path, "Serializing Arrays is not supported, please use Lists instead");
    } else if (o instanceof Enum) {
      String enumName = ((Enum<?>) o).name();
      try {
        Field enumField = o.getClass().getField(enumName);
        return BeanMapper.propertyName(enumField);
      } catch (NoSuchFieldException ex) {
        return enumName;
      }
    } else if (o instanceof Date
        || o instanceof Timestamp
        || o instanceof GeoPoint
        || o instanceof Blob
        || o instanceof DocumentReference
        || o instanceof FieldValue) {
      return o;
    } else {
      Class<T> clazz = (Class<T>) o.getClass();
      BeanMapper<T> mapper = loadOrCreateBeanMapperForClass(clazz);
      return mapper.serialize(o, path);
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> T deserializeToType(Object o, Type type, ErrorPath path) {
    if (o == null) {
      return null;
    } else if (type instanceof ParameterizedType) {
      return deserializeToParameterizedType(o, (ParameterizedType) type, path);
    } else if (type instanceof Class) {
      return deserializeToClass(o, (Class<T>) type, path);
    } else if (type instanceof WildcardType) {
      Type[] lowerBounds = ((WildcardType) type).getLowerBounds();
      if (lowerBounds.length > 0) {
        throw deserializeError(path, "Generic lower-bounded wildcard types are not supported");
      }

      // Upper bounded wildcards are of the form <? extends Foo>. Multiple upper bounds are allowed
      // but if any of the bounds are of class type, that bound must come first in this array. Note
      // that this array always has at least one element, since the unbounded wildcard <?> always
      // has at least an upper bound of Object.
      Type[] upperBounds = ((WildcardType) type).getUpperBounds();
      hardAssert(upperBounds.length > 0, "Unexpected type bounds on wildcard " + type);
      return deserializeToType(o, upperBounds[0], path);
    } else if (type instanceof TypeVariable) {
      // As above, TypeVariables always have at least one upper bound of Object.
      Type[] upperBounds = ((TypeVariable<?>) type).getBounds();
      hardAssert(upperBounds.length > 0, "Unexpected type bounds on type variable " + type);
      return deserializeToType(o, upperBounds[0], path);

    } else if (type instanceof GenericArrayType) {
      throw deserializeError(path, "Generic Arrays are not supported, please use Lists instead");
    } else {
      throw deserializeError(path, "Unknown type encountered: " + type);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T deserializeToClass(Object o, Class<T> clazz, ErrorPath path) {
    if (o == null) {
      return null;
    } else if (clazz.isPrimitive()
        || Number.class.isAssignableFrom(clazz)
        || Boolean.class.isAssignableFrom(clazz)
        || Character.class.isAssignableFrom(clazz)) {
      return deserializeToPrimitive(o, clazz, path);
    } else if (String.class.isAssignableFrom(clazz)) {
      return (T) convertString(o, path);
    } else if (Date.class.isAssignableFrom(clazz)) {
      return (T) convertDate(o, path);
    } else if (Timestamp.class.isAssignableFrom(clazz)) {
      return (T) convertTimestamp(o, path);
    } else if (Blob.class.isAssignableFrom(clazz)) {
      return (T) convertBlob(o, path);
    } else if (GeoPoint.class.isAssignableFrom(clazz)) {
      return (T) convertGeoPoint(o, path);
    } else if (DocumentReference.class.isAssignableFrom(clazz)) {
      return (T) convertDocumentReference(o, path);
    } else if (clazz.isArray()) {
      throw deserializeError(
          path, "Converting to Arrays is not supported, please use Lists instead");
    } else if (clazz.getTypeParameters().length > 0) {
      throw deserializeError(
          path,
          "Class "
              + clazz.getName()
              + " has generic type parameters, please use GenericTypeIndicator instead");
    } else if (clazz.equals(Object.class)) {
      return (T) o;
    } else if (clazz.isEnum()) {
      return deserializeToEnum(o, clazz, path);
    } else {
      return convertBean(o, clazz, path);
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> T deserializeToParameterizedType(
      Object o, ParameterizedType type, ErrorPath path) {
    // getRawType should always return a Class<?>
    Class<?> rawType = (Class<?>) type.getRawType();
    if (List.class.isAssignableFrom(rawType)) {
      Type genericType = type.getActualTypeArguments()[0];
      if (o instanceof List) {
        List<Object> list = (List<Object>) o;
        List<Object> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
          result.add(deserializeToType(list.get(i), genericType, path.child("[" + i + "]")));
        }
        return (T) result;
      } else {
        throw deserializeError(path, "Expected a List, but got a " + o.getClass());
      }
    } else if (Map.class.isAssignableFrom(rawType)) {
      Type keyType = type.getActualTypeArguments()[0];
      Type valueType = type.getActualTypeArguments()[1];
      if (!keyType.equals(String.class)) {
        throw deserializeError(
            path,
            "Only Maps with string keys are supported, but found Map with key type " + keyType);
      }
      Map<String, Object> map = expectMap(o, path);
      HashMap<String, Object> result = new HashMap<>();
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        result.put(
            entry.getKey(),
            deserializeToType(entry.getValue(), valueType, path.child(entry.getKey())));
      }
      return (T) result;
    } else if (Collection.class.isAssignableFrom(rawType)) {
      throw deserializeError(path, "Collections are not supported, please use Lists instead");
    } else {
      Map<String, Object> map = expectMap(o, path);
      BeanMapper<T> mapper = (BeanMapper<T>) loadOrCreateBeanMapperForClass(rawType);
      HashMap<TypeVariable<Class<T>>, Type> typeMapping = new HashMap<>();
      TypeVariable<Class<T>>[] typeVariables = mapper.clazz.getTypeParameters();
      Type[] types = type.getActualTypeArguments();
      if (types.length != typeVariables.length) {
        throw new IllegalStateException("Mismatched lengths for type variables and actual types");
      }
      for (int i = 0; i < typeVariables.length; i++) {
        typeMapping.put(typeVariables[i], types[i]);
      }
      return mapper.deserialize(map, typeMapping, path);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T deserializeToPrimitive(Object o, Class<T> clazz, ErrorPath path) {
    if (Integer.class.isAssignableFrom(clazz) || int.class.isAssignableFrom(clazz)) {
      return (T) convertInteger(o, path);
    } else if (Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)) {
      return (T) convertBoolean(o, path);
    } else if (Double.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz)) {
      return (T) convertDouble(o, path);
    } else if (Long.class.isAssignableFrom(clazz) || long.class.isAssignableFrom(clazz)) {
      return (T) convertLong(o, path);
    } else if (Float.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz)) {
      return (T) (Float) convertDouble(o, path).floatValue();
    } else if (Short.class.isAssignableFrom(clazz) || short.class.isAssignableFrom(clazz)) {
      throw deserializeError(path, "Deserializing to shorts is not supported");
    } else if (Byte.class.isAssignableFrom(clazz) || byte.class.isAssignableFrom(clazz)) {
      throw deserializeError(path, "Deserializing to bytes is not supported");
    } else if (Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)) {
      throw deserializeError(path, "Deserializing to chars is not supported");
    } else {
      throw new IllegalArgumentException("Unknown primitive type: " + clazz);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T deserializeToEnum(Object object, Class<T> clazz, ErrorPath path) {
    if (object instanceof String) {
      String value = (String) object;
      // We cast to Class without generics here since we can't prove the bound
      // T extends Enum<T> statically

      // try to use PropertyName if exist
      Field[] enumFields = clazz.getFields();
      for (Field field : enumFields) {
        if (field.isEnumConstant()) {
          String propertyName = BeanMapper.propertyName(field);
          if (value.equals(propertyName)) {
            value = field.getName();
            break;
          }
        }
      }

      try {
        return (T) Enum.valueOf((Class) clazz, value);
      } catch (IllegalArgumentException e) {
        throw deserializeError(
            path,
            "Could not find enum value of " + clazz.getName() + " for value \"" + value + "\"");
      }
    } else {
      throw deserializeError(
          path,
          "Expected a String while deserializing to enum "
              + clazz
              + " but got a "
              + object.getClass());
    }
  }

  private static <T> BeanMapper<T> loadOrCreateBeanMapperForClass(Class<T> clazz) {
    @SuppressWarnings("unchecked")
    BeanMapper<T> mapper = (BeanMapper<T>) mappers.get(clazz);
    if (mapper == null) {
      mapper = new BeanMapper<>(clazz);
      // Inserting without checking is fine because mappers are "pure" and it's okay
      // if we create and use multiple by different threads temporarily
      mappers.put(clazz, mapper);
    }
    return mapper;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> expectMap(Object object, ErrorPath path) {
    if (object instanceof Map) {
      // TODO: runtime validation of keys?
      return (Map<String, Object>) object;
    } else {
      throw deserializeError(
          path, "Expected a Map while deserializing, but got a " + object.getClass());
    }
  }

  private static Integer convertInteger(Object o, ErrorPath path) {
    if (o instanceof Integer) {
      return (Integer) o;
    } else if (o instanceof Long || o instanceof Double) {
      double value = ((Number) o).doubleValue();
      if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
        return ((Number) o).intValue();
      } else {
        throw deserializeError(
            path,
            "Numeric value out of 32-bit integer range: "
                + value
                + ". Did you mean to use a long or double instead of an int?");
      }
    } else {
      throw deserializeError(
          path, "Failed to convert a value of type " + o.getClass().getName() + " to int");
    }
  }

  private static Long convertLong(Object o, ErrorPath path) {
    if (o instanceof Integer) {
      return ((Integer) o).longValue();
    } else if (o instanceof Long) {
      return (Long) o;
    } else if (o instanceof Double) {
      Double value = (Double) o;
      if (value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
        return value.longValue();
      } else {
        throw deserializeError(
            path,
            "Numeric value out of 64-bit long range: "
                + value
                + ". Did you mean to use a double instead of a long?");
      }
    } else {
      throw deserializeError(
          path, "Failed to convert a value of type " + o.getClass().getName() + " to long");
    }
  }

  private static Double convertDouble(Object o, ErrorPath path) {
    if (o instanceof Integer) {
      return ((Integer) o).doubleValue();
    } else if (o instanceof Long) {
      Long value = (Long) o;
      Double doubleValue = ((Long) o).doubleValue();
      if (doubleValue.longValue() == value) {
        return doubleValue;
      } else {
        throw deserializeError(
            path,
            "Loss of precision while converting number to "
                + "double: "
                + o
                + ". Did you mean to use a 64-bit long instead?");
      }
    } else if (o instanceof Double) {
      return (Double) o;
    } else {
      throw deserializeError(
          path, "Failed to convert a value of type " + o.getClass().getName() + " to double");
    }
  }

  private static Boolean convertBoolean(Object o, ErrorPath path) {
    if (o instanceof Boolean) {
      return (Boolean) o;
    } else {
      throw deserializeError(
          path, "Failed to convert value of type " + o.getClass().getName() + " to boolean");
    }
  }

  private static String convertString(Object o, ErrorPath path) {
    if (o instanceof String) {
      return (String) o;
    } else {
      throw deserializeError(
          path, "Failed to convert value of type " + o.getClass().getName() + " to String");
    }
  }

  private static Date convertDate(Object o, ErrorPath path) {
    if (o instanceof Date) {
      return (Date) o;
    } else if (o instanceof Timestamp) {
      return ((Timestamp) o).toDate();
    } else {
      throw deserializeError(
          path, "Failed to convert value of type " + o.getClass().getName() + " to Date");
    }
  }

  private static Timestamp convertTimestamp(Object o, ErrorPath path) {
    if (o instanceof Timestamp) {
      return (Timestamp) o;
    } else if (o instanceof Date) {
      return new Timestamp((Date) o);
    } else {
      throw deserializeError(
          path, "Failed to convert value of type " + o.getClass().getName() + " to Timestamp");
    }
  }

  private static Blob convertBlob(Object o, ErrorPath path) {
    if (o instanceof Blob) {
      return (Blob) o;
    } else {
      throw deserializeError(
          path, "Failed to convert value of type " + o.getClass().getName() + " to Blob");
    }
  }

  private static GeoPoint convertGeoPoint(Object o, ErrorPath path) {
    if (o instanceof GeoPoint) {
      return (GeoPoint) o;
    } else {
      throw deserializeError(
          path, "Failed to convert value of type " + o.getClass().getName() + " to GeoPoint");
    }
  }

  private static DocumentReference convertDocumentReference(Object o, ErrorPath path) {
    if (o instanceof DocumentReference) {
      return (DocumentReference) o;
    } else {
      throw deserializeError(
          path,
          "Failed to convert value of type " + o.getClass().getName() + " to DocumentReference");
    }
  }

  private static <T> T convertBean(Object o, Class<T> clazz, ErrorPath path) {
    BeanMapper<T> mapper = loadOrCreateBeanMapperForClass(clazz);
    if (o instanceof Map) {
      return mapper.deserialize(expectMap(o, path), path);
    } else {
      throw deserializeError(
          path,
          "Can't convert object of type " + o.getClass().getName() + " to type " + clazz.getName());
    }
  }

  private static IllegalArgumentException serializeError(ErrorPath path, String reason) {
    reason = "Could not serialize object. " + reason;
    if (path.getLength() > 0) {
      reason = reason + " (found in field '" + path.toString() + "')";
    }
    return new IllegalArgumentException(reason);
  }

  private static RuntimeException deserializeError(ErrorPath path, String reason) {
    reason = "Could not deserialize object. " + reason;
    if (path.getLength() > 0) {
      reason = reason + " (found in field '" + path.toString() + "')";
    }
    return new RuntimeException(reason);
  }

  private static class BeanMapper<T> {
    private final Class<T> clazz;
    private final Constructor<T> constructor;
    private final boolean throwOnUnknownProperties;
    private final boolean warnOnUnknownProperties;
    // Case insensitive mapping of properties to their case sensitive versions
    private final Map<String, String> properties;

    private final Map<String, Method> getters;
    private final Map<String, Method> setters;
    private final Map<String, Field> fields;

    // A list of any properties that were annotated with @ServerTimestamp.
    private final HashSet<String> serverTimestamps;

    BeanMapper(Class<T> clazz) {
      this.clazz = clazz;
      throwOnUnknownProperties = clazz.isAnnotationPresent(ThrowOnExtraProperties.class);
      warnOnUnknownProperties = !clazz.isAnnotationPresent(IgnoreExtraProperties.class);
      properties = new HashMap<>();

      setters = new HashMap<>();
      getters = new HashMap<>();
      fields = new HashMap<>();

      serverTimestamps = new HashSet<>();

      Constructor<T> constructor;
      try {
        constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
      } catch (NoSuchMethodException e) {
        // We will only fail at deserialization time if no constructor is present
        constructor = null;
      }
      this.constructor = constructor;
      // Add any public getters to properties (including isXyz())
      for (Method method : clazz.getMethods()) {
        if (shouldIncludeGetter(method)) {
          String propertyName = propertyName(method);
          addProperty(propertyName);
          method.setAccessible(true);
          if (getters.containsKey(propertyName)) {
            throw new RuntimeException(
                "Found conflicting getters for name "
                    + method.getName()
                    + " on class "
                    + clazz.getName());
          }
          getters.put(propertyName, method);
          applyGetterAnnotations(method);
        }
      }

      // Add any public fields to properties
      for (Field field : clazz.getFields()) {
        if (shouldIncludeField(field)) {
          String propertyName = propertyName(field);
          addProperty(propertyName);
          applyFieldAnnotations(field);
        }
      }

      // We can use private setters and fields for known (public) properties/getters. Since
      // getMethods/getFields only returns public methods/fields we need to traverse the
      // class hierarchy to find the appropriate setter or field.
      Class<? super T> currentClass = clazz;
      do {
        // Add any setters
        for (Method method : currentClass.getDeclaredMethods()) {
          if (shouldIncludeSetter(method)) {
            String propertyName = propertyName(method);
            String existingPropertyName = properties.get(propertyName.toLowerCase(Locale.US));
            if (existingPropertyName != null) {
              if (!existingPropertyName.equals(propertyName)) {
                throw new RuntimeException(
                    "Found setter on "
                        + currentClass.getName()
                        + " with invalid case-sensitive name: "
                        + method.getName());
              } else {
                Method existingSetter = setters.get(propertyName);
                if (existingSetter == null) {
                  method.setAccessible(true);
                  setters.put(propertyName, method);
                  applySetterAnnotations(method);
                } else if (!isSetterOverride(method, existingSetter)) {
                  // We require that setters with conflicting property names are
                  // overrides from a base class
                  if (currentClass == clazz) {
                    // TODO: Should we support overloads?
                    throw new RuntimeException(
                        "Class "
                            + clazz.getName()
                            + " has multiple setter overloads with name "
                            + method.getName());
                  } else {
                    throw new RuntimeException(
                        "Found conflicting setters "
                            + "with name: "
                            + method.getName()
                            + " (conflicts with "
                            + existingSetter.getName()
                            + " defined on "
                            + existingSetter.getDeclaringClass().getName()
                            + ")");
                  }
                }
              }
            }
          }
        }

        for (Field field : currentClass.getDeclaredFields()) {
          String propertyName = propertyName(field);

          // Case sensitivity is checked at deserialization time
          // Fields are only added if they don't exist on a subclass
          if (properties.containsKey(propertyName.toLowerCase(Locale.US))
              && !fields.containsKey(propertyName)) {
            field.setAccessible(true);
            fields.put(propertyName, field);
            applyFieldAnnotations(field);
          }
        }

        // Traverse class hierarchy until we reach java.lang.Object which contains a bunch
        // of fields/getters we don't want to serialize
        currentClass = currentClass.getSuperclass();
      } while (currentClass != null && !currentClass.equals(Object.class));

      if (properties.isEmpty()) {
        throw new RuntimeException("No properties to serialize found on class " + clazz.getName());
      }
    }

    private void addProperty(String property) {
      String oldValue = properties.put(property.toLowerCase(Locale.US), property);
      if (oldValue != null && !property.equals(oldValue)) {
        throw new RuntimeException(
            "Found two getters or fields with conflicting case "
                + "sensitivity for property: "
                + property.toLowerCase(Locale.US));
      }
    }

    T deserialize(Map<String, Object> values, ErrorPath path) {
      return deserialize(values, Collections.emptyMap(), path);
    }

    T deserialize(
        Map<String, Object> values, Map<TypeVariable<Class<T>>, Type> types, ErrorPath path) {
      if (constructor == null) {
        throw deserializeError(
            path,
            "Class "
                + clazz.getName()
                + " does not define a no-argument constructor. If you are using ProGuard, make "
                + "sure these constructors are not stripped");
      }

      T instance = newInstance(constructor);
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        String propertyName = entry.getKey();
        ErrorPath childPath = path.child(propertyName);
        if (setters.containsKey(propertyName)) {
          Method setter = setters.get(propertyName);
          Type[] params = setter.getGenericParameterTypes();
          if (params.length != 1) {
            throw deserializeError(childPath, "Setter does not have exactly one parameter");
          }
          Type resolvedType = resolveType(params[0], types);
          Object value =
              CustomClassMapper.deserializeToType(entry.getValue(), resolvedType, childPath);
          invoke(setter, instance, value);
        } else if (fields.containsKey(propertyName)) {
          Field field = fields.get(propertyName);
          Type resolvedType = resolveType(field.getGenericType(), types);
          Object value =
              CustomClassMapper.deserializeToType(entry.getValue(), resolvedType, childPath);
          try {
            field.set(instance, value);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        } else {
          String message =
              "No setter/field for " + propertyName + " found on class " + clazz.getName();
          if (properties.containsKey(propertyName.toLowerCase(Locale.US))) {
            message += " (fields/setters are case sensitive!)";
          }
          if (throwOnUnknownProperties) {
            throw new RuntimeException(message);
          } else if (warnOnUnknownProperties) {
            Logger.warn(CustomClassMapper.class.getSimpleName(), "%s", message);
          }
        }
      }
      return instance;
    }

    private Type resolveType(Type type, Map<TypeVariable<Class<T>>, Type> types) {
      if (type instanceof TypeVariable) {
        Type resolvedType = types.get(type);
        if (resolvedType == null) {
          throw new IllegalStateException("Could not resolve type " + type);
        } else {
          return resolvedType;
        }
      } else {
        return type;
      }
    }

    Map<String, Object> serialize(T object, ErrorPath path) {
      if (!clazz.isAssignableFrom(object.getClass())) {
        throw new IllegalArgumentException(
            "Can't serialize object of class "
                + object.getClass()
                + " with BeanMapper for class "
                + clazz);
      }
      Map<String, Object> result = new HashMap<>();
      for (String property : properties.values()) {
        Object propertyValue;
        if (getters.containsKey(property)) {
          Method getter = getters.get(property);
          propertyValue = invoke(getter, object);
        } else {
          // Must be a field
          Field field = fields.get(property);
          if (field == null) {
            throw new IllegalStateException("Bean property without field or getter: " + property);
          }
          try {
            propertyValue = field.get(object);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
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

    private void applyFieldAnnotations(Field field) {
      if (field.isAnnotationPresent(ServerTimestamp.class)) {
        Class<?> fieldType = field.getType();
        if (fieldType != Date.class && fieldType != Timestamp.class) {
          throw new IllegalArgumentException(
              "Field "
                  + field.getName()
                  + " is annotated with @ServerTimestamp but is "
                  + fieldType
                  + " instead of Date or Timestamp.");
        }
        serverTimestamps.add(propertyName(field));
      }
    }

    private void applyGetterAnnotations(Method method) {
      if (method.isAnnotationPresent(ServerTimestamp.class)) {
        Class<?> returnType = method.getReturnType();
        if (returnType != Date.class && returnType != Timestamp.class) {
          throw new IllegalArgumentException(
              "Method "
                  + method.getName()
                  + " is annotated with @ServerTimestamp but returns "
                  + returnType
                  + " instead of Date or Timestamp.");
        }
        serverTimestamps.add(propertyName(method));
      }
    }

    private void applySetterAnnotations(Method method) {
      if (method.isAnnotationPresent(ServerTimestamp.class)) {
        throw new IllegalArgumentException(
            "Method "
                + method.getName()
                + " is annotated with @ServerTimestamp but should not be. @ServerTimestamp can"
                + " only be applied to fields and getters, not setters.");
      }
    }

    private static boolean shouldIncludeGetter(Method method) {
      if (!method.getName().startsWith("get") && !method.getName().startsWith("is")) {
        return false;
      }
      // Exclude methods from Object.class
      if (method.getDeclaringClass().equals(Object.class)) {
        return false;
      }
      // Non-public methods
      if (!Modifier.isPublic(method.getModifiers())) {
        return false;
      }
      // Static methods
      if (Modifier.isStatic(method.getModifiers())) {
        return false;
      }
      // No return type
      if (method.getReturnType().equals(Void.TYPE)) {
        return false;
      }
      // Non-zero parameters
      if (method.getParameterTypes().length != 0) {
        return false;
      }
      // Excluded methods
      if (method.isAnnotationPresent(Exclude.class)) {
        return false;
      }
      return true;
    }

    private static boolean shouldIncludeSetter(Method method) {
      if (!method.getName().startsWith("set")) {
        return false;
      }
      // Exclude methods from Object.class
      if (method.getDeclaringClass().equals(Object.class)) {
        return false;
      }
      // Static methods
      if (Modifier.isStatic(method.getModifiers())) {
        return false;
      }
      // Has a return type
      if (!method.getReturnType().equals(Void.TYPE)) {
        return false;
      }
      // Methods without exactly one parameters
      if (method.getParameterTypes().length != 1) {
        return false;
      }
      // Excluded methods
      if (method.isAnnotationPresent(Exclude.class)) {
        return false;
      }
      return true;
    }

    private static boolean shouldIncludeField(Field field) {
      // Exclude methods from Object.class
      if (field.getDeclaringClass().equals(Object.class)) {
        return false;
      }
      // Non-public fields
      if (!Modifier.isPublic(field.getModifiers())) {
        return false;
      }
      // Static fields
      if (Modifier.isStatic(field.getModifiers())) {
        return false;
      }
      // Transient fields
      if (Modifier.isTransient(field.getModifiers())) {
        return false;
      }
      // Excluded fields
      if (field.isAnnotationPresent(Exclude.class)) {
        return false;
      }
      return true;
    }

    private static boolean isSetterOverride(Method base, Method override) {
      // We expect an overridden setter here
      hardAssert(
          base.getDeclaringClass().isAssignableFrom(override.getDeclaringClass()),
          "Expected override from a base class");
      hardAssert(base.getReturnType().equals(Void.TYPE), "Expected void return type");
      hardAssert(override.getReturnType().equals(Void.TYPE), "Expected void return type");

      Type[] baseParameterTypes = base.getParameterTypes();
      Type[] overrideParameterTypes = override.getParameterTypes();
      hardAssert(baseParameterTypes.length == 1, "Expected exactly one parameter");
      hardAssert(overrideParameterTypes.length == 1, "Expected exactly one parameter");

      return base.getName().equals(override.getName())
          && baseParameterTypes[0].equals(overrideParameterTypes[0]);
    }

    private static String propertyName(Field field) {
      String annotatedName = annotatedName(field);
      return annotatedName != null ? annotatedName : field.getName();
    }

    private static String propertyName(Method method) {
      String annotatedName = annotatedName(method);
      return annotatedName != null ? annotatedName : serializedName(method.getName());
    }

    private static String annotatedName(AccessibleObject obj) {
      if (obj.isAnnotationPresent(PropertyName.class)) {
        PropertyName annotation = obj.getAnnotation(PropertyName.class);
        return annotation.value();
      }

      return null;
    }

    private static String serializedName(String methodName) {
      String[] prefixes = new String[] {"get", "set", "is"};
      String methodPrefix = null;
      for (String prefix : prefixes) {
        if (methodName.startsWith(prefix)) {
          methodPrefix = prefix;
        }
      }
      if (methodPrefix == null) {
        throw new IllegalArgumentException("Unknown Bean prefix for method: " + methodName);
      }
      String strippedName = methodName.substring(methodPrefix.length());

      // Make sure the first word or upper-case prefix is converted to lower-case
      char[] chars = strippedName.toCharArray();
      int pos = 0;
      while (pos < chars.length && Character.isUpperCase(chars[pos])) {
        chars[pos] = Character.toLowerCase(chars[pos]);
        pos++;
      }
      return new String(chars);
    }
  }

  /**
   * Immutable class representing the path to a specific field in an object. Used to provide better
   * error messages.
   */
  static class ErrorPath {
    private final int length;
    private final ErrorPath parent;
    private final String name;

    static final ErrorPath EMPTY = new ErrorPath(null, null, 0);

    ErrorPath(ErrorPath parent, String name, int length) {
      this.parent = parent;
      this.name = name;
      this.length = length;
    }

    int getLength() {
      return length;
    }

    ErrorPath child(String name) {
      return new ErrorPath(this, name, length + 1);
    }

    @Override
    public String toString() {
      if (length == 0) {
        return "";
      } else if (length == 1) {
        return name;
      } else {
        // This is not very efficient, but it's only hit if there's an error.
        return parent.toString() + "." + name;
      }
    }
  }
}
