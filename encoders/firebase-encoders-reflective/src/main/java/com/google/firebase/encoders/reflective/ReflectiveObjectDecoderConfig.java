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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectiveObjectDecoderConfig {

  private boolean inLine;
  private String fieldName;
  private Class<?> fieldType;
  private AccessType accessType;
  private Object value;
  private enum AccessType {METHOD, FIELD}

  @NonNull
  public static ReflectiveObjectDecoderConfig of(@NonNull Field field) {
    return new ReflectiveObjectDecoderConfig(false, field.getName(), field.getType(), AccessType.FIELD);
  }

  @NonNull
  public static ReflectiveObjectDecoderConfig of(@NonNull Method method) {
    return new ReflectiveObjectDecoderConfig(false, propertyName(method), method.getReturnType(), AccessType.METHOD);
  }

  private ReflectiveObjectDecoderConfig(boolean inLine, @NonNull String fieldName, @NonNull Class<?> fieldType, @NonNull AccessType accessType) {
    this.inLine = inLine;
    this.fieldName = fieldName;
    this.fieldType = fieldType;
    this.accessType = accessType;
  }

  public boolean isInLine() {
    return inLine;
  }

  public void setInLine(boolean inLine) {
    this.inLine = inLine;
  }

  @NonNull
  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(@NonNull String fieldName) {
    this.fieldName = fieldName;
  }

  @NonNull
  public Class<?> getFieldType() {
    return fieldType;
  }

  @NonNull
  public AccessType getAccessType() {
    return accessType;
  }

  @NonNull
  public Object getValue() {
    return value;
  }

  public void setValue(@NonNull Object value) {
    this.value = value;
  }

  private static String propertyName(Method method) {
    String methodName = method.getName();
    final String prefix = "set";

    if (!methodName.startsWith(prefix)) {
      throw new IllegalArgumentException("Unknown Bean prefix for method: " + methodName);

    }

    String strippedName = methodName.substring(prefix.length());

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
