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

import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.annotations.Alias;
import com.google.firebase.encoders.annotations.Encodable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class InternalAnnotationContext {
  private boolean inline;
  private String decodingKey;
  private boolean ignored;

  InternalAnnotationContext(AccessibleObject accObj) {
    if (accObj instanceof Field) {
      decodingKey = ((Field) accObj).getName();
    } else if (accObj instanceof Method) {
      decodingKey = fieldName((Method) accObj);
    } else {
      throw new EncodingException("Constructor shouldn't be used to get its decoding key");
    }
    this.inline = false;
    this.ignored = false;

    // internal annotations
    if (accObj.isAnnotationPresent(Encodable.Ignore.class)) {
      this.ignored = true;
      return;
    }
    if (accObj.isAnnotationPresent(Encodable.Field.class)) {
      Encodable.Field annotation = accObj.getAnnotation(Encodable.Field.class);
      if (annotation != null && annotation.name().length() > 0) {
        this.decodingKey = annotation.name();
      }
      if (annotation != null) {
        this.inline = annotation.inline();
      }
    }

    // TODO: support Annotation @ExtraProperty
    // alias annotations
    for (Annotation annotation : accObj.getAnnotations()) {
      Alias alias = annotation.annotationType().getAnnotation(Alias.class);
      if (alias != null) {
        Class<? extends Annotation> actualAnnotationType = alias.value();
        if (actualAnnotationType.equals(Encodable.Ignore.class)) {
          this.ignored = true;
        } else if (actualAnnotationType.equals(Encodable.Field.class)) {
          for (Method method : annotation.annotationType().getDeclaredMethods()) {
            Alias.Property property = method.getAnnotation(Alias.Property.class);
            if (property == null) {
              continue;
            }
            String propertyName = property.value();
            Object obj = null;
            try {
              method.setAccessible(true);
              obj = method.invoke(annotation);
            } catch (IllegalAccessException e) {
              throw new EncodingException(
                  "Method: "
                      + method.getName()
                      + " of Annotation:"
                      + annotation
                      + "encountered illegal access.\n"
                      + e);
            } catch (InvocationTargetException e) {
              throw new EncodingException(
                  "Method: "
                      + method.getName()
                      + " of Annotation:"
                      + annotation
                      + "encountered InvocationTarget.\n"
                      + e);
            }
            if (propertyName.equals("name")) {
              if (!method.getReturnType().equals(String.class)) {
                throw new EncodingException(
                    "Method annotated by @Alias.Property must return String value.");
              }
              String val = (String) obj;
              if (val != null && val.length() > 0) {
                this.decodingKey = val;
              }
            } else if (propertyName.equals("inline")) {
              if (!method.getReturnType().equals(boolean.class)) {
                throw new EncodingException(
                    "Method annotated by @Alias.inline must return String value.");
              }
              Boolean val = (Boolean) obj;
              if (val != null) {
                this.inline = val;
              }
            } else {
              throw new EncodingException(
                  "Annotation @Alias.Property should only has value of \"name\" and \"inline.\"");
            }
          }
        }
      }
    }
  }

  public boolean isInline() {
    return inline;
  }

  public String getDecodingKey() {
    return decodingKey;
  }

  public boolean isIgnored() {
    return ignored;
  }

  private String fieldName(Method method) {
    String methodName = method.getName();
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
