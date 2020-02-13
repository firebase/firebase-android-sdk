// Copyright 2019 Google LLC
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
import androidx.annotation.Nullable;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.annotations.Encodable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

final class ReflectiveObjectEncoderProvider implements ObjectEncoderProvider {
  static final ObjectEncoderProvider INSTANCE = new ReflectiveObjectEncoderProvider();

  private static class ReflectiveObjectEncoderImpl implements ObjectEncoder<Object> {

    private final Map<String, EncodingDescriptor> fields;

    private ReflectiveObjectEncoderImpl(Map<String, EncodingDescriptor> fields) {
      this.fields = fields;
    }

    @Override
    public void encode(@Nullable Object obj, @NonNull ObjectEncoderContext ctx) throws IOException {
      for (Map.Entry<String, EncodingDescriptor> entry : fields.entrySet()) {
        String fieldName = entry.getKey();
        EncodingDescriptor descriptor = entry.getValue();
        try {
          if (descriptor.inline) {
            ctx.inline(descriptor.method.invoke(obj));
          } else {
            ctx.add(fieldName, descriptor.method.invoke(obj));
          }
        } catch (IllegalAccessException e) {
          throw new EncodingException(
              String.format("Could not encode field '%s' of %s.", fieldName, obj.getClass()), e);
        } catch (InvocationTargetException e) {
          throw new EncodingException(
              String.format("Could not encode field '%s' of %s.", fieldName, obj.getClass()), e);
        }
      }
    }
  }

  @NonNull
  @Override
  public <T> ObjectEncoder<T> get(@NonNull Class<T> type) {
    Map<String, EncodingDescriptor> fields = new HashMap<>();
    for (Method method : type.getMethods()) {
      if (method.isAnnotationPresent(Encodable.Ignore.class)) {
        continue;
      }

      Encodable.Field fieldAnnotation = method.getAnnotation(Encodable.Field.class);
      String getter = toGetterName(method, fieldAnnotation);
      if (getter != null) {
        method.setAccessible(true);
        fields.put(
            getter,
            new EncodingDescriptor(fieldAnnotation != null && fieldAnnotation.inline(), method));
      }
    }
    @SuppressWarnings("unchecked")
    ObjectEncoder<T> encoder = (ObjectEncoder<T>) new ReflectiveObjectEncoderImpl(fields);
    return encoder;
  }

  @Nullable
  private static String toGetterName(Method m, @Nullable Encodable.Field fieldAnnotation) {
    Class<?> returnType = m.getReturnType();
    if (Modifier.isStatic(m.getModifiers())
        || !Modifier.isPublic(m.getModifiers())
        || m.getParameterTypes().length > 0
        || returnType.equals(void.class)
        || m.getDeclaringClass().equals(Object.class)) {
      return null;
    }

    if (fieldAnnotation != null) {
      String annotationName = fieldAnnotation.name();
      if (!annotationName.isEmpty()) {
        return annotationName;
      }
    }

    String methodName = m.getName();

    if (returnType.equals(boolean.class)
        && methodName.startsWith("is")
        && methodName.length() > 2) {
      return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
    }

    if (methodName.startsWith("get") && methodName.length() > 3) {
      return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    }
    return null;
  }

  private static class EncodingDescriptor {
    final boolean inline;
    final Method method;

    EncodingDescriptor(boolean inline, Method method) {
      this.inline = inline;
      this.method = method;
    }
  }
}
