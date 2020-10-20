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

package com.google.firebase.components;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents a {@link Component} dependency. */
public final class Dependency {
  /** Enumerates dependency types. */
  @IntDef({Type.OPTIONAL, Type.REQUIRED, Type.SET})
  @Retention(RetentionPolicy.SOURCE)
  private @interface Type {
    int OPTIONAL = 0;
    int REQUIRED = 1;
    int SET = 2;
  }

  @IntDef({Injection.DIRECT, Injection.PROVIDER, Injection.DEFERRED})
  @Retention(RetentionPolicy.SOURCE)
  private @interface Injection {
    int DIRECT = 0;
    int PROVIDER = 1;
    int DEFERRED = 2;
  }

  private final Class<?> anInterface;
  private final @Type int type;
  private final @Injection int injection;

  private Dependency(Class<?> anInterface, @Type int type, @Injection int injection) {
    this.anInterface = Preconditions.checkNotNull(anInterface, "Null dependency anInterface.");
    this.type = type;
    this.injection = injection;
  }

  public static Dependency optional(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.DIRECT);
  }

  public static Dependency deferred(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.DEFERRED);
  }

  public static Dependency required(Class<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.DIRECT);
  }

  public static Dependency setOf(Class<?> anInterface) {
    return new Dependency(anInterface, Type.SET, Injection.DIRECT);
  }

  public static Dependency optionalProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.PROVIDER);
  }

  public static Dependency requiredProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.PROVIDER);
  }

  public static Dependency setOfProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.SET, Injection.PROVIDER);
  }

  public Class<?> getInterface() {
    return anInterface;
  }

  public boolean isRequired() {
    return type == Type.REQUIRED;
  }

  public boolean isSet() {
    return type == Type.SET;
  }

  public boolean isDirectInjection() {
    return injection == Injection.DIRECT;
  }

  public boolean isDeferred() {
    return injection == Injection.DEFERRED;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Dependency) {
      Dependency other = (Dependency) o;
      return anInterface == other.anInterface && type == other.type && injection == other.injection;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1000003;
    h ^= anInterface.hashCode();
    h *= 1000003;
    h ^= type;
    h *= 1000003;
    h ^= injection;
    return h;
  }

  @Override
  public String toString() {
    StringBuilder sb =
        new StringBuilder("Dependency{anInterface=")
            .append(anInterface)
            .append(", type=")
            .append(type == Type.REQUIRED ? "required" : type == Type.OPTIONAL ? "optional" : "set")
            .append(", injection=")
            .append(describeInjection(injection))
            .append("}");
    return sb.toString();
  }

  private static String describeInjection(@Injection int injection) {
    switch (injection) {
      case Injection.DIRECT:
        return "direct";
      case Injection.PROVIDER:
        return "provider";
      case Injection.DEFERRED:
        return "deferred";
      default:
        throw new AssertionError("Unsupported injection: " + injection);
    }
  }
}
