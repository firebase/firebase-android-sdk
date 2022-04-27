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

package com.google.firebase.encoders;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes a field of a given type.
 *
 * <p>Contains the following information:
 *
 * <ul>
 *   <li>Name of the field.
 *   <li>A set of annotations attached to the field.
 * </ul>
 *
 * <p>For example the following field could have the {@link FieldDescriptor} equivalent to:
 *
 * <pre>{@code
 * {@literal @}MyAnnotation(key="value")
 * String getFoo();
 *
 * FieldDescriptor(name="foo", properties=[{@literal @}MyAnnotation(key="value")])
 * }</pre>
 */
public final class FieldDescriptor {

  private final String name;
  private final Map<Class<?>, Object> properties;

  private FieldDescriptor(String name, Map<Class<?>, Object> properties) {
    this.name = name;
    this.properties = properties;
  }

  /** Name of the field. */
  @NonNull
  public String getName() {
    return name;
  }

  /**
   * Provides access to extra properties of the field.
   *
   * @return {@code T} annotation if present, null otherwise.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public <T extends Annotation> T getProperty(@NonNull Class<T> type) {
    return (T) properties.get(type);
  }

  @NonNull
  public static FieldDescriptor of(@NonNull String name) {
    return new FieldDescriptor(name, Collections.emptyMap());
  }

  @NonNull
  public static Builder builder(@NonNull String name) {
    return new Builder(name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FieldDescriptor)) {
      return false;
    }

    FieldDescriptor that = (FieldDescriptor) o;

    return name.equals(that.name) && properties.equals(that.properties);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + properties.hashCode();
    return result;
  }

  @NonNull
  @Override
  public String toString() {
    return "FieldDescriptor{name=" + name + ", properties=" + properties.values() + "}";
  }

  public static final class Builder {

    private final String name;
    private Map<Class<?>, Object> properties = null;

    Builder(String name) {
      this.name = name;
    }

    @NonNull
    public <T extends Annotation> Builder withProperty(@NonNull T value) {
      if (properties == null) {
        properties = new HashMap<>();
      }
      properties.put(value.annotationType(), value);
      return this;
    }

    @NonNull
    public FieldDescriptor build() {
      return new FieldDescriptor(
          name,
          properties == null
              ? Collections.emptyMap()
              : Collections.unmodifiableMap(new HashMap<>(properties)));
    }
  }
}
