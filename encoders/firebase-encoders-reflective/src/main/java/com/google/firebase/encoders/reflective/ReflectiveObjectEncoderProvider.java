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
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.annotations.ExtraProperty;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

final class ReflectiveObjectEncoderProvider implements ObjectEncoderProvider {
  static final ObjectEncoderProvider INSTANCE = new ReflectiveObjectEncoderProvider();

  private static class ReflectiveObjectEncoderImpl implements ObjectEncoder<Object> {

    private final Map<FieldDescriptor, EncodingDescriptor> fields;

    private ReflectiveObjectEncoderImpl(Map<FieldDescriptor, EncodingDescriptor> fields) {
      this.fields = fields;
    }

    @Override
    public void encode(@NonNull Object obj, @NonNull ObjectEncoderContext ctx) throws IOException {
      for (Map.Entry<FieldDescriptor, EncodingDescriptor> entry : fields.entrySet()) {
        FieldDescriptor fieldDescriptor = entry.getKey();
        EncodingDescriptor encodingDescriptor = entry.getValue();
        try {
          if (encodingDescriptor.inline) {
            ctx.inline(encodingDescriptor.method.invoke(obj));
          } else {
            ctx.add(fieldDescriptor, encodingDescriptor.method.invoke(obj));
          }
        } catch (IllegalAccessException e) {
          throw new EncodingException(
              String.format("Could not encode field '%s' of %s.", fieldDescriptor, obj.getClass()),
              e);
        } catch (InvocationTargetException e) {
          throw new EncodingException(
              String.format("Could not encode field '%s' of %s.", fieldDescriptor, obj.getClass()),
              e);
        }
      }
    }
  }

  @NonNull
  @Override
  public <T> ObjectEncoder<T> get(@NonNull Class<T> type) {
    Map<FieldDescriptor, EncodingDescriptor> fields = new HashMap<>();
    for (Method method : type.getMethods()) {
      if (!shouldIncludeGetter(method)) {
        continue;
      }

      InternalAnnotationContext internalAnnotationContext = new InternalAnnotationContext(method);
      if (internalAnnotationContext.isIgnored()) {
        continue;
      }

      FieldDescriptor descriptor =
          toFieldDescriptor(method, internalAnnotationContext.getDecodingKey());
      method.setAccessible(true);

      fields.put(descriptor, new EncodingDescriptor(internalAnnotationContext.isInline(), method));
    }
    @SuppressWarnings("unchecked")
    ObjectEncoder<T> encoder = (ObjectEncoder<T>) new ReflectiveObjectEncoderImpl(fields);
    return encoder;
  }

  private static FieldDescriptor toFieldDescriptor(Method method, String decodingKey) {
    FieldDescriptor.Builder builder = FieldDescriptor.builder(decodingKey);
    for (Annotation annotation : method.getAnnotations()) {
      if (!annotation.annotationType().isAnnotationPresent(ExtraProperty.class)) {
        continue;
      }
      builder.withProperty(annotation);
    }
    return builder.build();
  }

  private static boolean shouldIncludeGetter(Method m) {
    if (!m.getName().startsWith("get") && !m.getName().startsWith("is")) {
      return false;
    }
    Class<?> returnType = m.getReturnType();
    if (Modifier.isStatic(m.getModifiers())
        || !Modifier.isPublic(m.getModifiers())
        || m.getParameterTypes().length > 0
        || returnType.equals(void.class)
        || m.getDeclaringClass().equals(Object.class)) {
      return false;
    }
    return true;
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
