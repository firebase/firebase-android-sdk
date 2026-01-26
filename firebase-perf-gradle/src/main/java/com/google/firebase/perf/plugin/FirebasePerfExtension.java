/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.plugin;

import java.util.Optional;

/** Extension class to allow configuration to the Plugin by the user in the build.gradle. */
public class FirebasePerfExtension {

  /** "null" means the flag value has not been provided. */
  private Boolean instrumentationEnabled = null;

  // LINT.IfChange
  /**
   * Setter for enabling/disabling instrumentation.
   *
   * <p>Usage: Defining {@code FirebasePerformance.instrumentationEnabled = false} in your app's
   * "build.gradle" file will trigger this method with {@code false} as a parameter.
   *
   * <p>Note: To be able to allow configuration, the setter method has to be provided and it must
   * adhere to the JavaBeans naming convention.
   *
   * <p>For example, if we want to define the property naming {@code ”enablePerf”}, the setter
   * method should be {@code “setEnablePerf()”}.
   *
   * <p>Since here we want to define the property naming {@code “instrumentationEnabled”}, the
   * setter method we defined here is {@code “setInstrumentationEnabled()”}.
   */
  public void setInstrumentationEnabled(boolean enabled) {
    instrumentationEnabled = enabled;
  }

  // LINT.ThenChange(firebase/firebase-android-sdk/firebase-perf-gradle/\
  //   src/test/java/com/google/firebase/perf/plugin/GradleBuildRunner.java:extension_property)

  Optional<Boolean> isInstrumentationEnabled() {
    return Optional.ofNullable(instrumentationEnabled);
  }

  @Override
  public String toString() {
    return "FirebasePerfExtension(instrumentationEnabled: " + instrumentationEnabled + ")";
  }
}
