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

  private final Qualified<?> anInterface;
  @Type private final int type;
  private final @Injection int injection;

  private Dependency(Class<?> anInterface, @Type int type, @Injection int injection) {
    this(Qualified.unqualified(anInterface), type, injection);
  }

  private Dependency(Qualified<?> anInterface, @Type int type, @Injection int injection) {
    this.anInterface = Preconditions.checkNotNull(anInterface, "Null dependency anInterface.");
    this.type = type;
    this.injection = injection;
  }

  /**
   * Declares an optional dependency.
   *
   * <p>Optional dependencies can be missing at runtime(being {@code null}) and dependents must be
   * ready to handle that.
   *
   * @deprecated Optional dependencies are not safe to use in the context of Play's dynamic feature
   *     delivery. Use {@link #optionalProvider(Class) optional provider} instead.
   */
  @Deprecated
  public static Dependency optional(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.DIRECT);
  }

  /**
   * Declares a deferred dependency.
   *
   * <p>Such dependencies are optional and may not be present by default. But they can become
   * available if a dynamic module that contains them is installed.
   */
  public static Dependency deferred(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.DEFERRED);
  }

  /**
   * Declares a deferred dependency.
   *
   * <p>Such dependencies are optional and may not be present by default. But they can become
   * available if a dynamic module that contains them is installed.
   */
  public static Dependency deferred(Qualified<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.DEFERRED);
  }

  /**
   * Declares a required dependency.
   *
   * <p>Such dependencies must be present in order for the dependent component to function. Any
   * component with a required dependency should also declare a Maven dependency on an SDK that
   * provides it. Failing to do so will result in a {@link MissingDependencyException} to be thrown
   * at runtime.
   */
  public static Dependency required(Class<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.DIRECT);
  }

  /**
   * Declares a required dependency.
   *
   * <p>Such dependencies must be present in order for the dependent component to function. Any
   * component with a required dependency should also declare a Maven dependency on an SDK that
   * provides it. Failing to do so will result in a {@link MissingDependencyException} to be thrown
   * at runtime.
   */
  public static Dependency required(Qualified<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.DIRECT);
  }

  /**
   * Declares a Set multi-binding dependency.
   *
   * <p>Such dependencies provide access to a {@code Set<Foo>} to dependent components. Note that
   * the set is only filled with components that explicitly declare the intent to be a "set"
   * dependency via {@link Component#intoSet(Object, Class)}.
   */
  public static Dependency setOf(Class<?> anInterface) {
    return new Dependency(anInterface, Type.SET, Injection.DIRECT);
  }

  /**
   * Declares a Set multi-binding dependency.
   *
   * <p>Such dependencies provide access to a {@code Set<Foo>} to dependent components. Note that
   * the set is only filled with components that explicitly declare the intent to be a "set"
   * dependency via {@link Component#intoSet(Object, Class)}.
   */
  public static Dependency setOf(Qualified<?> anInterface) {
    return new Dependency(anInterface, Type.SET, Injection.DIRECT);
  }

  /**
   * Declares an optional dependency.
   *
   * <p>Optional dependencies can be missing at runtime(being {@code null}) and dependents must be
   * ready to handle that.
   */
  public static Dependency optionalProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.PROVIDER);
  }

  /**
   * Declares an optional dependency.
   *
   * <p>Optional dependencies can be missing at runtime(being {@code null}) and dependents must be
   * ready to handle that.
   */
  public static Dependency optionalProvider(Qualified<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.PROVIDER);
  }

  /**
   * Declares a required dependency.
   *
   * <p>Such dependencies must be present in order for the dependent component to function. Any
   * component with a required dependency should also declare a Maven dependency on an SDK that
   * provides it. Failing to do so will result in a {@link MissingDependencyException} to be thrown
   * at runtime.
   */
  public static Dependency requiredProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.PROVIDER);
  }

  /**
   * Declares a required dependency.
   *
   * <p>Such dependencies must be present in order for the dependent component to function. Any
   * component with a required dependency should also declare a Maven dependency on an SDK that
   * provides it. Failing to do so will result in a {@link MissingDependencyException} to be thrown
   * at runtime.
   */
  public static Dependency requiredProvider(Qualified<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.PROVIDER);
  }

  /**
   * Declares a Set multi-binding dependency.
   *
   * <p>Such dependencies provide access to a {@code Set<Foo>} to dependent components. Note that
   * the set is only filled with components that explicitly declare the intent to be a "set"
   * dependency via {@link Component#intoSet(Object, Class)}.
   */
  public static Dependency setOfProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.SET, Injection.PROVIDER);
  }

  /**
   * Declares a Set multi-binding dependency.
   *
   * <p>Such dependencies provide access to a {@code Set<Foo>} to dependent components. Note that
   * the set is only filled with components that explicitly declare the intent to be a "set"
   * dependency via {@link Component#intoSet(Object, Class)}.
   */
  public static Dependency setOfProvider(Qualified<?> anInterface) {
    return new Dependency(anInterface, Type.SET, Injection.PROVIDER);
  }

  public Qualified<?> getInterface() {
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
      return anInterface.equals(other.anInterface)
          && type == other.type
          && injection == other.injection;
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
