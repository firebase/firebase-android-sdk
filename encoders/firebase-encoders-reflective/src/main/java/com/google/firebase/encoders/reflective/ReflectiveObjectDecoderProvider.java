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
import com.google.firebase.encoders.annotations.Encodable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

class ReflectiveObjectDecoderProvider implements ObjectDecoderProvider {

  static ReflectiveObjectDecoderProvider INSTANCE = new ReflectiveObjectDecoderProvider();

  private ReflectiveObjectDecoderProvider() {}

  @NonNull
  @Override
  public <T> ObjectDecoder<T> get(@NonNull Class<T> clazz) {
    return new ReflectiveObjectDecoderImpl<>();
  }

  private static class ReflectiveObjectDecoderImpl<T> implements ObjectDecoder<T> {

    private final Map<String, ReflectiveDecoderFieldContext> fieldContexts = new HashMap<>();

    private ReflectiveObjectDecoderImpl() {}

    @NonNull
    @Override
    public TypeCreator<T> decode(@NonNull ObjectDecoderContext<T> ctx) {
      Class<T> clazz = ctx.getTypeToken().getRawType();
      readMethods(clazz);
      readFields(clazz);
      decodeFields(ctx);
      return getTypeCreator(ctx.getTypeToken());
    }

    private void readMethods(Class<T> clazz) {
      Class<? super T> currentClass = clazz;
      while (currentClass != Object.class && currentClass != null) {
        Method[] methods = currentClass.getDeclaredMethods();
        for (Method method : methods) {
          if (!shouldIncludeSetter(method)) {
            continue;
          }
          String fieldName = fieldName(method);
          if (!fieldContexts.containsKey(fieldName)) {
            fieldContexts.put(fieldName, ReflectiveDecoderFieldContextImpl.of(method));
          }
        }
        currentClass = currentClass.getSuperclass();
      }
    }

    private void readFields(Class<T> clazz) {
      Class<? super T> currentClass = clazz;
      while (currentClass != Object.class && currentClass != null) {
        for (Field field : currentClass.getDeclaredFields()) {
          if (!shouldIncludeField(field)) {
            continue;
          }
          String fieldName = field.getName();
          if (!fieldContexts.containsKey(fieldName)) {
            fieldContexts.put(fieldName, ReflectiveDecoderFieldContextImpl.of(field));
          }
        }
        currentClass = currentClass.getSuperclass();
      }
    }

    private void decodeFields(ObjectDecoderContext<T> ctx) {
      for (ReflectiveDecoderFieldContext<?> fieldContext : fieldContexts.values()) {
        decodeField(ctx, fieldContext);
      }
    }

    @SuppressWarnings("unchecked")
    private <TField> void decodeField(
        ObjectDecoderContext<T> ctx, ReflectiveDecoderFieldContext<TField> fieldContext) {
      Class<TField> fieldType = fieldContext.getFieldRawType();
      FieldDescriptor fieldDescriptor = fieldContext.getFieldDescriptor();
      FieldRef<TField> ref;
      if (fieldType.equals(int.class)) {
        ref = (FieldRef<TField>) ctx.decodeInteger(fieldDescriptor);
      } else if (fieldType.equals(long.class)) {
        ref = (FieldRef<TField>) ctx.decodeLong(fieldDescriptor);
      } else if (fieldType.equals(short.class)) {
        ref = (FieldRef<TField>) ctx.decodeShort(fieldDescriptor);
      } else if (fieldType.equals(double.class)) {
        ref = (FieldRef<TField>) ctx.decodeDouble(fieldDescriptor);
      } else if (fieldType.equals(float.class)) {
        ref = (FieldRef<TField>) ctx.decodeFloat(fieldDescriptor);
      } else if (fieldType.equals(boolean.class)) {
        ref = (FieldRef<TField>) ctx.decodeBoolean(fieldDescriptor);
      } else {
        TypeToken<TField> fieldTypeToken =
            (TypeToken<TField>) getFieldTypeToken(fieldContext.getFieldGenericType(), ctx);
        if (fieldContext.isInline()) {
          if (fieldTypeToken instanceof TypeToken.ClassToken) {
            TypeToken.ClassToken<Object> classToken = (TypeToken.ClassToken<Object>) fieldTypeToken;
            ref = (FieldRef<TField>) ctx.decodeInline(classToken, ReflectiveObjectDecoder.DEFAULT);
          } else {
            throw new IllegalArgumentException(
                "Array types cannot be decoded inline, type:" + fieldTypeToken + " found.");
          }
        } else {
          ref = ctx.decode(fieldDescriptor, fieldTypeToken);
        }
      }
      fieldContext.putFieldRef(ref);
    }

    private TypeToken<?> getFieldTypeToken(Type type, ObjectDecoderContext<?> ctx) {
      if (type instanceof TypeVariable) {
        TypeVariable[] typeVariables = ctx.getTypeToken().getRawType().getTypeParameters();
        for (int i = 0; i < typeVariables.length; i++) {
          if (typeVariables[i].equals(type)) {
            return ctx.getTypeArgument(i);
          }
        }
        throw new EncodingException(
            "Class "
                + ctx.getTypeToken()
                + "does not have type variable "
                + ((TypeVariable) type).getName());
      }
      return TypeToken.of(type);
    }

    private TypeCreator<T> getTypeCreator(TypeToken.ClassToken<T> classToken) {
      return (creationCtx -> {
        T instance = ReflectiveInitializer.newInstance(classToken);
        setFields(creationCtx, instance);
        return instance;
      });
    }

    private void setFields(CreationContext creationCtx, Object instance) {
      for (ReflectiveDecoderFieldContext<?> fieldContext : fieldContexts.values()) {
        setField(creationCtx, instance, fieldContext);
      }
    }

    @SuppressWarnings("unchecked")
    private <TField> void setField(
        CreationContext creationCtx,
        Object instance,
        ReflectiveDecoderFieldContext<TField> fieldContext) {
      FieldRef<TField> ref = fieldContext.getFieldRef();
      Class<TField> fieldType = fieldContext.getFieldRawType();
      ReflectiveSetter<TField> fieldSetter = fieldContext.getReflectiveSetter();
      if (ref instanceof FieldRef.Boxed) {
        TField val = creationCtx.get((FieldRef.Boxed<TField>) ref);
        fieldSetter.set(instance, val);
      } else if (fieldType.equals(int.class)) {
        int val = creationCtx.getInteger((FieldRef.Primitive<Integer>) ref);
        fieldSetter.set(instance, (TField) (Integer) val);
      } else if (fieldType.equals(long.class)) {
        long val = creationCtx.getLong((FieldRef.Primitive<Long>) ref);
        fieldSetter.set(instance, (TField) (Long) val);
      } else if (fieldType.equals(short.class)) {
        short val = creationCtx.getShort((FieldRef.Primitive<Short>) ref);
        fieldSetter.set(instance, (TField) (Short) val);
      } else if (fieldType.equals(double.class)) {
        double val = creationCtx.getDouble((FieldRef.Primitive<Double>) ref);
        fieldSetter.set(instance, (TField) (Double) val);
      } else if (fieldType.equals(float.class)) {
        float val = creationCtx.getFloat((FieldRef.Primitive<Float>) ref);
        fieldSetter.set(instance, (TField) (Float) val);
      } else if (fieldType.equals(char.class)) {
        char val = creationCtx.getChar((FieldRef.Primitive<Character>) ref);
        fieldSetter.set(instance, (TField) (Character) val);
      } else if (fieldType.equals(boolean.class)) {
        boolean val = creationCtx.getBoolean((FieldRef.Primitive<Boolean>) ref);
        fieldSetter.set(instance, (TField) (Boolean) val);
      } else {
        throw new EncodingException(fieldType + " not supported.");
      }
    }
  }

  private static boolean shouldIncludeSetter(Method method) {
    if (!method.getName().startsWith("set")) {
      return false;
    }
    if (method.getDeclaringClass().equals(Object.class)) {
      return false;
    }
    if (Modifier.isStatic(method.getModifiers())) {
      return false;
    }
    if (!method.getReturnType().equals(Void.TYPE)) {
      return false;
    }
    if (method.getParameterTypes().length != 1) {
      return false;
    }
    return !method.isAnnotationPresent(Encodable.Ignore.class);
  }

  private static boolean shouldIncludeField(Field field) {
    if (field.getDeclaringClass().equals(Object.class)) {
      return false;
    }
    if (!Modifier.isPublic(field.getModifiers())) {
      return false;
    }
    if (Modifier.isStatic(field.getModifiers())) {
      return false;
    }
    if (Modifier.isTransient(field.getModifiers())) {
      return false;
    }
    return !field.isAnnotationPresent(Encodable.Ignore.class);
  }

  private static String fieldName(Method method) {
    String methodName = method.getName();
    final String prefix = "set";
    if (!methodName.startsWith(prefix)) {
      throw new IllegalArgumentException("Unknown Bean prefix for method: " + methodName);
    }
    return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
  }
}
