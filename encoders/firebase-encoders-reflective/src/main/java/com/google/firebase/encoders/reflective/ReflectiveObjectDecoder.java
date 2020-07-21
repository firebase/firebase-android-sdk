// Copyright 2020 Google LLC
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

package com.google.firebase.encoders.reflective;

import androidx.annotation.NonNull;

import com.google.firebase.decoders.CreationContext;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.ObjectDecoderContext;
import com.google.firebase.decoders.TypeCreator;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.FieldDescriptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class ReflectiveObjectDecoder<T> implements ObjectDecoder<T> {

  private Map<String, ReflectiveObjectDecoderConfig> configs;
  private Map<String, FieldRef<?>> refs;
  private Map<FieldRef<?>, ReflectiveSetter> setters;
  private ReflectiveObjectDecoderDelegation delegation = null;

  @NonNull
  public static ReflectiveObjectDecoder INSTANCE = new ReflectiveObjectDecoder();

  @NonNull
  public static ReflectiveObjectDecoder with(@NonNull ReflectiveObjectDecoderDelegation delegation) {
    ReflectiveObjectDecoder reflectiveObjectDecoder = new ReflectiveObjectDecoder();
    reflectiveObjectDecoder.delegation = delegation;
    return reflectiveObjectDecoder;
  }

  private ReflectiveObjectDecoder() {
    configs = new HashMap<>();
    refs = new HashMap<>();
    setters = new HashMap<>();
  }

  @NonNull
  @Override
  public TypeCreator<T> decode(@NonNull ObjectDecoderContext<T> ctx) {
    decodeFields(ctx);
    return getTypeCreator(ctx.getTypeToken());
  }

  private void decodeFields(@NonNull ObjectDecoderContext<T> ctx) {
    Class<? super T> currentClass = ctx.getTypeToken().getRawType();

    Map<String, Method> fieldNameToSetter = getSetterMap(currentClass);

    currentClass = ctx.getTypeToken().getRawType();
    while (currentClass != Object.class) {
      Field[] fields = currentClass.getDeclaredFields();
      for (Field field: fields) {
        if (!shouldIncludeField(field)) {
          continue;
        }

        ReflectiveObjectDecoderConfig config = getConfig(field);

        if (delegation != null) delegation.willDecodeField(config);

        FieldRef<?> ref = decodeField(ctx, config);
        refs.put(field.getName(), ref);

        ReflectiveSetter setter;
        Method fieldSetter = fieldNameToSetter.get(field.getName());
        if (fieldSetter != null) setter = ReflectiveSetter.of(fieldSetter);
        else setter = ReflectiveSetter.of(field);
        setters.put(ref, setter);
      }
      currentClass = currentClass.getSuperclass();
    }
  }

  private Map<String, Method> getSetterMap(Class<?> clazz) {
    Class<?> currentClass = clazz;
    Map<String, Method> setters = new HashMap<>();
    while (currentClass != Object.class) {
      Method[] methods = currentClass.getDeclaredMethods();
      for (Method method: methods) {
        if (!shouldIncludeSetter(method)) {
          continue;
        }
        ReflectiveObjectDecoderConfig config = ReflectiveObjectDecoderConfig.of(method);
        configs.put(config.getFieldName(), config);
        setters.put(config.getFieldName(), method);

        if (delegation != null) delegation.willDecodeField(config);
      }
      currentClass = currentClass.getSuperclass();
    }
    return setters;
  }

  private ReflectiveObjectDecoderConfig getConfig(Field field) {
    String fieldName = field.getName();
    ReflectiveObjectDecoderConfig config = configs.get(fieldName);
    if (config == null) {
      config = ReflectiveObjectDecoderConfig.of(field);
      configs.put(config.getFieldName(), config);
    }
    return config;
  }

  private FieldRef<?> decodeField(ObjectDecoderContext<T> ctx, ReflectiveObjectDecoderConfig config) {
    String fieldName = config.getFieldName();
    Class<?> fieldType = config.getFieldType();
    boolean isInline = config.isInLine();

    if (fieldType.equals(int.class)) {
      return ctx.decodeInteger(FieldDescriptor.of(fieldName));
    } else if (fieldType.equals(long.class)) {
      return ctx.decodeLong(FieldDescriptor.of(fieldName));
    } else if (fieldType.equals(short.class)) {
      return ctx.decodeShort(FieldDescriptor.of(fieldName));
    } else if (fieldType.equals(double.class)) {
      return ctx.decodeDouble(FieldDescriptor.of(fieldName));
    } else if (fieldType.equals(float.class)) {
      return ctx.decodeFloat(FieldDescriptor.of(fieldName));
    } else if (fieldType.equals(boolean.class)) {
      return ctx.decodeBoolean(FieldDescriptor.of(fieldName));
    } else {
      if (isInline)
        return ctx.decodeInline((TypeToken.ClassToken<T>) TypeToken.of(fieldType), this);
      return ctx.decode(FieldDescriptor.of(fieldName), TypeToken.of(fieldType));
    }
  }

  @SuppressWarnings("unchecked")
  private TypeCreator<T> getTypeCreator(TypeToken.ClassToken<T> classToken) {
    return (creationCtx -> {
      T instance = ReflectiveInitializer.newInstance(classToken);
      setFields(creationCtx, instance);
      return instance;
    });
  }

  private void setFields(CreationContext creationCtx, Object instance) {
    for (Map.Entry<String, FieldRef<?>> entry: refs.entrySet()) {
      String fieldName = entry.getKey();
      ReflectiveObjectDecoderConfig config = configs.get(fieldName);
      FieldRef<?> ref = entry.getValue();

      Class<?> fieldType = ref.getTypeToken().getRawType();
      ReflectiveSetter setter = setters.get(ref);
      if (ref instanceof FieldRef.Boxed) {
        Object val = creationCtx.get((FieldRef.Boxed<?>)ref);
        config.setValue(val);
        if (delegation != null) delegation.didDecodeField(config);
        setter.set(instance, config.getValue());
      } else if (fieldType.equals(int.class)) {
        int val = creationCtx.getInteger((FieldRef.Primitive<Integer>) ref);
        setter.set(instance, val);
      } else if (fieldType.equals(long.class)) {
        long val = creationCtx.getLong((FieldRef.Primitive<Long>) ref);
        setter.set(instance, val);
      } else if (fieldType.equals(short.class)) {
        short val = creationCtx.getShort((FieldRef.Primitive<Short>) ref);
        setter.set(instance, val);
      } else if (fieldType.equals(double.class)) {
        double val = creationCtx.getDouble((FieldRef.Primitive<Double>) ref);
        setter.set(instance, val);
      } else if (fieldType.equals(float.class)) {
        float val = creationCtx.getFloat((FieldRef.Primitive<Float>) ref);
        setter.set(instance, val);
      } else if (fieldType.equals(char.class)) {
        char val = creationCtx.getChar((FieldRef.Primitive<Character>) ref);
        setter.set(instance, val);
      } else if (fieldType.equals(boolean.class)) {
        boolean val = creationCtx.getBoolean((FieldRef.Primitive<Boolean>) ref);
        setter.set(instance, val);
      } else {
        throw new EncodingException(fieldType + " not supported.");
      }
    }

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
//    if (method.isAnnotationPresent(Exclude.class)) {
//      return false;
//    }
    return true;
  }

  private static boolean shouldIncludeField(Field field) {
    // Exclude methods from Object.class
    if (field.getDeclaringClass().equals(Object.class)) {
      return false;
    }
    // Non-public fields
//    if (!Modifier.isPublic(field.getModifiers())) {
//      return false;
//    }
    // Static fields
    if (Modifier.isStatic(field.getModifiers())) {
      return false;
    }
    // Transient fields
    if (Modifier.isTransient(field.getModifiers())) {
      return false;
    }
    return true;
  }
}
