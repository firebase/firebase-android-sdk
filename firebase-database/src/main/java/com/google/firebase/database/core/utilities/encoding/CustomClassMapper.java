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

package com.google.firebase.database.core.utilities.encoding;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import android.util.Log;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ThrowOnExtraProperties;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Helper class to convert to/from custom POJO classes and plain Java types. */
public class CustomClassMapper {
  private static final String LOG_TAG = "ClassMapper";

  private static final ConcurrentMap<Class<?>, BeanMapper<?>> mappers = new ConcurrentHashMap<>();

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

  @SuppressWarnings("unchecked")
  public static Map<String, Object> convertToPlainJavaTypes(Map<String, Object> update) {
    Object converted = serialize(update);
    hardAssert(converted instanceof Map);
    return (Map<String, Object>) converted;
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
    return deserializeToClass(object, clazz);
  }

  /**
   * Converts a standard library Java representation of JSON data to an object of the class provided
   * through the GenericTypeIndicator
   *
   * @param object The representation of the JSON data
   * @param typeIndicator The indicator providing class of the object to convert to
   * @return The POJO object.
   */
  public static <T> T convertToCustomClass(Object object, GenericTypeIndicator<T> typeIndicator) {
    Class<?> clazz = typeIndicator.getClass();
    Type genericTypeIndicatorType = clazz.getGenericSuperclass();
    if (genericTypeIndicatorType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) genericTypeIndicatorType;
      if (!parameterizedType.getRawType().equals(GenericTypeIndicator.class)) {
        throw new DatabaseException(
            "Not a direct subclass of GenericTypeIndicator: " + genericTypeIndicatorType);
      }
      // We are guaranteed to have exactly one type parameter
      Type type = parameterizedType.getActualTypeArguments()[0];
      return deserializeToType(object, type);
    } else {
      throw new DatabaseException(
          "Not a direct subclass of GenericTypeIndicator: " + genericTypeIndicatorType);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Object serialize(T o) {
    if (o == null) {
      return null;
    } else if (o instanceof Number) {
      if (o instanceof Float || o instanceof Double) {
        double doubleValue = ((Number) o).doubleValue();
        if (doubleValue <= Long.MAX_VALUE
            && doubleValue >= Long.MIN_VALUE
            && Math.floor(doubleValue) == doubleValue) {
          return ((Number) o).longValue();
        }
        return doubleValue;
      } else if (o instanceof Long || o instanceof Integer) {
        return o;
      } else {
        throw new DatabaseException(
            String.format(
                "Numbers of type %s are not supported, please use an int, long, float or double",
                o.getClass().getSimpleName()));
      }
    } else if (o instanceof String) {
      return o;
    } else if (o instanceof Boolean) {
      return o;
    } else if (o instanceof Character) {
      throw new DatabaseException("Characters are not supported, please use Strings");
    } else if (o instanceof Map) {
      Map<String, Object> result = new HashMap<>();
      for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) o).entrySet()) {
        Object key = entry.getKey();
        if (key instanceof String) {
          String keyString = (String) key;
          result.put(keyString, serialize(entry.getValue()));
        } else {
          throw new DatabaseException("Maps with non-string keys are not supported");
        }
      }
      return result;
    } else if (o instanceof Collection) {
      if (o instanceof List) {
        List<Object> list = (List<Object>) o;
        List<Object> result = new ArrayList<>(list.size());
        for (Object object : list) {
          result.add(serialize(object));
        }
        return result;
      } else {
        throw new DatabaseException(
            "Serializing Collections is not supported, " + "please use Lists instead");
      }
    } else if (o.getClass().isArray()) {
      throw new DatabaseException(
          "Serializing Arrays is not supported, please use Lists " + "instead");
    } else if (o instanceof Enum) {
      return ((Enum<?>) o).name();
    } else {
      Class<T> clazz = (Class<T>) o.getClass();
      BeanMapper<T> mapper = loadOrCreateBeanMapperForClass(clazz);
      return mapper.serialize(o);
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> T deserializeToType(Object o, Type type) {
    if (o == null) {
      return null;
    } else if (type instanceof ParameterizedType) {
      return deserializeToParameterizedType(o, (ParameterizedType) type);
    } else if (type instanceof Class) {
      return deserializeToClass(o, (Class<T>) type);
    } else if (type instanceof WildcardType) {
      Type[] lowerBounds = ((WildcardType) type).getLowerBounds();
      if (lowerBounds.length > 0) {
        throw new DatabaseException("Generic lower-bounded wildcard types are not supported");
      }

      // Upper bounded wildcards are of the form <? extends Foo>. Multiple upper bounds are allowed
      // but if any of the bounds are of class type, that bound must come first in this array. Note
      // that this array always has at least one element, since the unbounded wildcard <?> always
      // has at least an upper bound of Object.
      Type[] upperBounds = ((WildcardType) type).getUpperBounds();
      hardAssert(upperBounds.length > 0, "Wildcard type " + type + " is not upper bounded.");
      return deserializeToType(o, upperBounds[0]);
    } else if (type instanceof TypeVariable) {
      // As above, TypeVariables always have at least one upper bound of Object.
      Type[] upperBounds = ((TypeVariable<?>) type).getBounds();
      hardAssert(upperBounds.length > 0, "Wildcard type " + type + " is not upper bounded.");
      return deserializeToType(o, upperBounds[0]);

    } else if (type instanceof GenericArrayType) {
      throw new DatabaseException(
          "Generic Arrays are not supported, please use Lists " + "instead");
    } else {
      throw new IllegalStateException("Unknown type encountered: " + type);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T deserializeToClass(Object o, Class<T> clazz) {
    if (o == null) {
      return null;
    } else if (clazz.isPrimitive()
        || Number.class.isAssignableFrom(clazz)
        || Boolean.class.isAssignableFrom(clazz)
        || Character.class.isAssignableFrom(clazz)) {
      return (T) deserializeToPrimitive(o, clazz);
    } else if (String.class.isAssignableFrom(clazz)) {
      return (T) convertString(o);
    } else if (clazz.isArray()) {
      throw new DatabaseException(
          "Converting to Arrays is not supported, please use Lists" + "instead");
    } else if (clazz.getTypeParameters().length > 0) {
      throw new DatabaseException(
          "Class "
              + clazz.getName()
              + " has generic type "
              + "parameters, please use GenericTypeIndicator instead");
    } else if (clazz.equals(Object.class)) {
      return (T) o;
    } else if (clazz.isEnum()) {
      return deserializeToEnum(o, clazz);
    } else {
      return convertBean(o, clazz);
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> T deserializeToParameterizedType(Object o, ParameterizedType type) {
    // getRawType should always return a Class<?>
    Class<?> rawType = (Class<?>) type.getRawType();
    if (List.class.isAssignableFrom(rawType)) {
      Type genericType = type.getActualTypeArguments()[0];
      if (o instanceof List) {
        List<Object> list = (List<Object>) o;
        List<Object> result = new ArrayList<>(list.size());
        for (Object object : list) {
          result.add(deserializeToType(object, genericType));
        }
        return (T) result;
      } else {
        throw new DatabaseException(
            "Expected a List while deserializing, but got a " + o.getClass());
      }
    } else if (Map.class.isAssignableFrom(rawType)) {
      Type keyType = type.getActualTypeArguments()[0];
      Type valueType = type.getActualTypeArguments()[1];
      if (!keyType.equals(String.class)) {
        throw new DatabaseException(
            "Only Maps with string keys are supported, "
                + "but found Map with key type "
                + keyType);
      }
      Map<String, Object> map = expectMap(o);
      HashMap<String, Object> result = new HashMap<>();
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        result.put(entry.getKey(), deserializeToType(entry.getValue(), valueType));
      }
      return (T) result;
    } else if (Collection.class.isAssignableFrom(rawType)) {
      throw new DatabaseException("Collections are not supported, please use Lists instead");
    } else {
      Map<String, Object> map = expectMap(o);
      BeanMapper<T> mapper = (BeanMapper<T>) loadOrCreateBeanMapperForClass(rawType);
      HashMap<TypeVariable<Class<T>>, Type> typeMapping = new HashMap<>();
      TypeVariable<Class<T>>[] typeVariables = mapper.clazz.getTypeParameters();
      Type[] types = type.getActualTypeArguments();
      if (types.length != typeVariables.length) {
        throw new IllegalStateException(
            "Mismatched lengths for type variables and " + "actual types");
      }
      for (int i = 0; i < typeVariables.length; i++) {
        typeMapping.put(typeVariables[i], types[i]);
      }
      return mapper.deserialize(map, typeMapping);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T deserializeToPrimitive(Object o, Class<T> clazz) {
    if (Integer.class.isAssignableFrom(clazz) || int.class.isAssignableFrom(clazz)) {
      return (T) convertInteger(o);
    } else if (Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)) {
      return (T) convertBoolean(o);
    } else if (Double.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz)) {
      return (T) convertDouble(o);
    } else if (Long.class.isAssignableFrom(clazz) || long.class.isAssignableFrom(clazz)) {
      return (T) convertLong(o);
    } else if (Float.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz)) {
      return (T) (Float) convertDouble(o).floatValue();
    } else {
      throw new DatabaseException(
          String.format("Deserializing values to %s is not supported", clazz.getSimpleName()));
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T deserializeToEnum(Object object, Class<T> clazz) {
    if (object instanceof String) {
      String value = (String) object;
      // We cast to Class without generics here since we can't prove the bound
      // T extends Enum<T> statically
      try {
        return (T) Enum.valueOf((Class) clazz, value);
      } catch (IllegalArgumentException e) {
        throw new DatabaseException(
            "Could not find enum value of " + clazz.getName() + " for value \"" + value + "\"");
      }
    } else {
      throw new DatabaseException(
          "Expected a String while deserializing to enum "
              + clazz
              + " but got a "
              + object.getClass());
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> BeanMapper<T> loadOrCreateBeanMapperForClass(Class<T> clazz) {
    BeanMapper<T> mapper = (BeanMapper<T>) mappers.get(clazz);
    if (mapper == null) {
      mapper = new BeanMapper<T>(clazz);
      // Inserting without checking is fine because mappers are "pure" and it's okay
      // if we create and use multiple by different threads temporarily
      mappers.put(clazz, mapper);
    }
    return mapper;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> expectMap(Object object) {
    if (object instanceof Map) {
      // TODO: runtime validation of keys?
      return (Map<String, Object>) object;
    } else {
      throw new DatabaseException(
          "Expected a Map while deserializing, but got a " + object.getClass());
    }
  }

  private static Integer convertInteger(Object o) {
    if (o instanceof Integer) {
      return (Integer) o;
    } else if (o instanceof Long || o instanceof Double) {
      double value = ((Number) o).doubleValue();
      if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
        return ((Number) o).intValue();
      } else {
        throw new DatabaseException(
            "Numeric value out of 32-bit integer range: "
                + value
                + ". Did you mean to use a long or double instead of an int?");
      }
    } else {
      throw new DatabaseException(
          "Failed to convert a value of type " + o.getClass().getName() + " to int");
    }
  }

  private static Long convertLong(Object o) {
    if (o instanceof Integer) {
      return ((Integer) o).longValue();
    } else if (o instanceof Long) {
      return (Long) o;
    } else if (o instanceof Double) {
      Double value = (Double) o;
      if (value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
        return value.longValue();
      } else {
        throw new DatabaseException(
            "Numeric value out of 64-bit long range: "
                + value
                + ". Did you mean to use a double instead of a long?");
      }
    } else {
      throw new DatabaseException(
          "Failed to convert a value of type " + o.getClass().getName() + " to long");
    }
  }

  private static Double convertDouble(Object o) {
    if (o instanceof Integer) {
      return ((Integer) o).doubleValue();
    } else if (o instanceof Long) {
      Long value = (Long) o;
      Double doubleValue = ((Long) o).doubleValue();
      if (doubleValue.longValue() == value) {
        return doubleValue;
      } else {
        throw new DatabaseException(
            "Loss of precision while converting number to "
                + "double: "
                + o
                + ". Did you mean to use a 64-bit long instead?");
      }
    } else if (o instanceof Double) {
      return (Double) o;
    } else {
      throw new DatabaseException(
          "Failed to convert a value of type " + o.getClass().getName() + " to double");
    }
  }

  private static Boolean convertBoolean(Object o) {
    if (o instanceof Boolean) {
      return (Boolean) o;
    } else {
      throw new DatabaseException(
          "Failed to convert value of type " + o.getClass().getName() + " to boolean");
    }
  }

  private static String convertString(Object o) {
    if (o instanceof String) {
      return (String) o;
    } else {
      throw new DatabaseException(
          "Failed to convert value of type " + o.getClass().getName() + " to String");
    }
  }

  private static <T> T convertBean(Object o, Class<T> clazz) {
    BeanMapper<T> mapper = loadOrCreateBeanMapperForClass(clazz);
    if (o instanceof Map) {
      return mapper.deserialize(expectMap(o));
    } else {
      throw new DatabaseException(
          "Can't convert object of type " + o.getClass().getName() + " to type " + clazz.getName());
    }
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

    public BeanMapper(Class<T> clazz) {
      this.clazz = clazz;
      this.throwOnUnknownProperties = clazz.isAnnotationPresent(ThrowOnExtraProperties.class);
      this.warnOnUnknownProperties = !clazz.isAnnotationPresent(IgnoreExtraProperties.class);
      this.properties = new HashMap<>();

      this.setters = new HashMap<>();
      this.getters = new HashMap<>();
      this.fields = new HashMap<>();

      Constructor<T> constructor = null;
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
            throw new DatabaseException("Found conflicting getters for name: " + method.getName());
          }
          getters.put(propertyName, method);
        }
      }

      // Add any public fields to properties
      for (Field field : clazz.getFields()) {
        if (shouldIncludeField(field)) {
          String propertyName = propertyName(field);

          addProperty(propertyName);
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
                throw new DatabaseException(
                    "Found setter with invalid " + "case-sensitive name: " + method.getName());
              } else {
                Method existingSetter = setters.get(propertyName);
                if (existingSetter == null) {
                  method.setAccessible(true);
                  setters.put(propertyName, method);
                } else if (!isSetterOverride(method, existingSetter)) {
                  // We require that setters with conflicting property names are
                  // overrides from a base class
                  throw new DatabaseException(
                      "Found a conflicting setters "
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

        for (Field field : currentClass.getDeclaredFields()) {
          String propertyName = propertyName(field);

          // Case sensitivity is checked at deserialization time
          // Fields are only added if they don't exist on a subclass
          if (properties.containsKey(propertyName.toLowerCase(Locale.US))
              && !fields.containsKey(propertyName)) {
            field.setAccessible(true);
            fields.put(propertyName, field);
          }
        }

        // Traverse class hierarchy until we reach java.lang.Object which contains a bunch
        // of fields/getters we don't want to serialize
        currentClass = currentClass.getSuperclass();
      } while (currentClass != null && !currentClass.equals(Object.class));

      if (properties.isEmpty()) {
        throw new DatabaseException("No properties to serialize found on class " + clazz.getName());
      }
    }

    private void addProperty(String property) {
      String oldValue = this.properties.put(property.toLowerCase(Locale.US), property);
      if (oldValue != null && !property.equals(oldValue)) {
        throw new DatabaseException(
            "Found two getters or fields with conflicting case "
                + "sensitivity for property: "
                + property.toLowerCase(Locale.US));
      }
    }

    public T deserialize(Map<String, Object> values) {
      return deserialize(values, Collections.<TypeVariable<Class<T>>, Type>emptyMap());
    }

    public T deserialize(Map<String, Object> values, Map<TypeVariable<Class<T>>, Type> types) {
      if (this.constructor == null) {
        throw new DatabaseException(
            "Class "
                + this.clazz.getName()
                + " does not define a no-argument constructor. If you are using ProGuard, make "
                + "sure these constructors are not stripped.");
      }
      T instance;
      try {
        instance = this.constructor.newInstance();
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        String propertyName = entry.getKey();
        if (this.setters.containsKey(propertyName)) {
          Method setter = this.setters.get(propertyName);
          Type[] params = setter.getGenericParameterTypes();
          if (params.length != 1) {
            throw new IllegalStateException("Setter does not have exactly one parameter");
          }
          Type resolvedType = resolveType(params[0], types);
          Object value = CustomClassMapper.deserializeToType(entry.getValue(), resolvedType);
          try {
            setter.invoke(instance, value);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        } else if (this.fields.containsKey(propertyName)) {
          Field field = this.fields.get(propertyName);
          Type resolvedType = resolveType(field.getGenericType(), types);
          Object value = CustomClassMapper.deserializeToType(entry.getValue(), resolvedType);
          try {
            field.set(instance, value);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        } else {
          String message =
              "No setter/field for "
                  + propertyName
                  + " found "
                  + "on class "
                  + this.clazz.getName();
          if (this.properties.containsKey(propertyName.toLowerCase(Locale.US))) {
            message += " (fields/setters are case sensitive!)";
          }
          if (this.throwOnUnknownProperties) {
            throw new DatabaseException(message);
          } else if (this.warnOnUnknownProperties) {
            // TODO: replace Android logging with "our" logging
            Log.w(LOG_TAG, message);
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

    public Map<String, Object> serialize(T object) {
      if (!clazz.isAssignableFrom(object.getClass())) {
        throw new IllegalArgumentException(
            "Can't serialize object of class "
                + object.getClass()
                + " with BeanMapper for class "
                + clazz);
      }
      Map<String, Object> result = new HashMap<>();
      for (String property : this.properties.values()) {
        Object propertyValue;
        if (this.getters.containsKey(property)) {
          Method getter = this.getters.get(property);
          try {
            propertyValue = getter.invoke(object);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        } else {
          // Must be a field
          Field field = this.fields.get(property);
          if (field == null) {
            throw new IllegalStateException("Bean property without field or getter:" + property);
          }
          try {
            propertyValue = field.get(object);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        }
        Object serializedValue = CustomClassMapper.serialize(propertyValue);
        result.put(property, serializedValue);
      }
      return result;
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
}
