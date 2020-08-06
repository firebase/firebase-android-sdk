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

import com.google.firebase.decoders.FieldRef;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.annotations.ExtraProperty;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ReflectiveDecoderFieldContext<T> {
  private FieldDescriptor fieldDescriptor;
  private ReflectiveSetter<T> setter;
  private FieldRef<T> fieldRef;
  private Type genericType;
  private Class<T> rawType;
  private boolean inline;

  ReflectiveDecoderFieldContext(Method method) {
    this.fieldDescriptor = buildFieldDescriptor(method);
    this.genericType = method.getGenericParameterTypes()[0];
    @SuppressWarnings("unchecked")
    Class<T> rawType = (Class<T>) method.getParameterTypes()[0];
    this.rawType = rawType;
    this.inline = inline(method);
    method.setAccessible(true);
    this.setter =
        (obj, val) -> {
          try {
            method.invoke(obj, val);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        };
  }

  ReflectiveDecoderFieldContext(Field field) {
    this.fieldDescriptor = buildFieldDescriptor(field);
    this.genericType = field.getGenericType();
    @SuppressWarnings("unchecked")
    Class<T> rawType = (Class<T>) field.getType();
    this.rawType = rawType;
    this.inline = inline(field);
    field.setAccessible(true);
    this.setter =
        (obj, val) -> {
          try {
            field.set(obj, val);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        };
  }

  public Class<T> getFieldRawType() {
    return rawType;
  }

  public Type getFieldGenericType() {
    return genericType;
  }

  public FieldDescriptor getFieldDescriptor() {
    return fieldDescriptor;
  }

  public ReflectiveSetter<T> getReflectiveSetter() {
    return setter;
  }

  public FieldRef<T> getFieldRef() {
    return fieldRef;
  }

  public FieldRef<T> putFieldRef(FieldRef<T> fieldRef) {
    FieldRef<T> old = this.fieldRef;
    this.fieldRef = fieldRef;
    return old;
  }

  public boolean isInline() {
    return inline;
  }

  private static boolean inline(AccessibleObject accessibleObject) {
    Encodable.Field annotation = accessibleObject.getAnnotation(Encodable.Field.class);
    return annotation != null && annotation.inline();
  }

  private static FieldDescriptor buildFieldDescriptor(AccessibleObject accessibleObject) {
    Class<?> type;
    if (accessibleObject instanceof Field) {
      type = ((Field) accessibleObject).getType();
    } else if (accessibleObject instanceof Method) {
      type = ((Method) accessibleObject).getParameterTypes()[0];
    } else {
      throw new EncodingException("Constructor shouldn't be used to get its decoding key");
    }
    String decodingKey = decodingKey(accessibleObject);
    Annotation[] annotations = accessibleObject.getDeclaredAnnotations();
    FieldDescriptor.Builder builder = FieldDescriptor.builder(decodingKey);
    for (Annotation annotation : annotations) {
      ExtraProperty extraProperty = annotation.annotationType().getAnnotation(ExtraProperty.class);
      if (extraProperty != null) {
        Set<Class<?>> allowedTypes = new HashSet<>(Arrays.asList(extraProperty.allowedTypes()));
        if (allowedTypes.size() == 0 || allowedTypes.contains(type)) {
          builder.withProperty(annotation);
        } else {
          throw new EncodingException(
              "Type(" + type + ")is not allowed by the annotation(" + annotation + ").");
        }
      }
    }
    return builder.build();
  }

  private static String decodingKey(AccessibleObject accessibleObject) {
    String key;
    if (accessibleObject instanceof Field) {
      key = ((Field) accessibleObject).getName();
    } else if (accessibleObject instanceof Method) {
      key = fieldName((Method) accessibleObject);
    } else {
      throw new EncodingException("Constructor shouldn't be used to get its decoding key");
    }
    if (accessibleObject.isAnnotationPresent(Encodable.Field.class)) {
      Encodable.Field annotation = accessibleObject.getAnnotation(Encodable.Field.class);
      if (annotation != null && annotation.name().length() > 0) {
        key = annotation.name();
      }
    }
    return key;
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
