// Copyright 2018 Google LLC
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

import android.support.annotation.IntDef;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.internal.Preconditions;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents a {@link Component} dependency. */
@KeepForSdk
public final class Dependency {
  /** Enumerates dependency types. */
  @IntDef({Type.OPTIONAL, Type.REQUIRED})
  @Retention(RetentionPolicy.SOURCE)
  private @interface Type {
    int OPTIONAL = 0;
    int REQUIRED = 1;
  }

  @IntDef({Injection.DIRECT, Injection.PROVIDER})
  @Retention(RetentionPolicy.SOURCE)
  private @interface Injection {
    int DIRECT = 0;
    int PROVIDER = 1;
  }

  private final Class<?> anInterface;
  private final @Type int type;
  private final @Injection int injection;

  private Dependency(Class<?> anInterface, @Type int type, @Injection int injection) {
    this.anInterface = Preconditions.checkNotNull(anInterface, "Null dependency anInterface.");
    this.type = type;
    this.injection = injection;
  }

  @KeepForSdk
  public static Dependency optional(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.DIRECT);
  }

  @KeepForSdk
  public static Dependency required(Class<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.DIRECT);
  }

  @KeepForSdk
  public static Dependency optionalProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.OPTIONAL, Injection.PROVIDER);
  }

  @KeepForSdk
  public static Dependency requiredProvider(Class<?> anInterface) {
    return new Dependency(anInterface, Type.REQUIRED, Injection.PROVIDER);
  }

  public Class<?> getInterface() {
    return anInterface;
  }

  public boolean isRequired() {
    return type == Type.REQUIRED;
  }

  public boolean isDirectInjection() {
    return injection == Injection.DIRECT;
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
            .append(", required=")
            .append(type == Type.REQUIRED)
            .append(", direct=")
            .append(injection == Injection.DIRECT)
            .append("}");
    return sb.toString();
  }
}
