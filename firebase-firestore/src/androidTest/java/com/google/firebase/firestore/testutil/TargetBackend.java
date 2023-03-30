// Copyright 2023 Google LLC
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

package com.google.firebase.firestore.testutil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.firestore.util.Logger;

/**
 * The Firestore backends available for use in integration testing.
 *
 * <p>To get the backend configured for use, call {@link #getConfiguredValue()}. To quickly override
 * the local backend for use during local testing simply hardcode the return value of {@link
 * #getConfiguredValue()} to the desired backend.
 *
 * <p>To configure the default backend, set the instrumentation argument "TARGET_BACKEND" either via
 * the Gradle command line or the Android Studio run configuration. See {@link
 * #loadFromInstrumentationArguments}
 */
public enum TargetBackend {
  EMULATOR("10.0.2.2:8080", false),
  QA("staging-firestore.sandbox.googleapis.com", true),
  NIGHTLY("test-firestore.sandbox.googleapis.com", true),
  PROD("firestore.googleapis.com", true);

  private static final String INSTRUMENTATION_ARGUMENTS_KEY = "TARGET_BACKEND";
  private static final TargetBackend CONFIGURED_TARGET_BACKEND = loadConfiguredValue();

  public final String host;
  public final boolean ssl;

  TargetBackend(@NonNull String host, boolean ssl) {
    this.host = host;
    this.ssl = ssl;
  }

  @NonNull
  public static TargetBackend getConfiguredValue() {
    return CONFIGURED_TARGET_BACKEND;
  }

  @NonNull
  private static TargetBackend loadConfiguredValue() {
    TargetBackend targetBackend = loadFromInstrumentationArguments();
    if (targetBackend == null) {
      Logger.debug("TargetBackend", "loadConfiguredValue() returning default: " + EMULATOR);
      return EMULATOR;
    }

    Logger.debug("TargetBackend", "loadConfiguredValue() returning: " + targetBackend);
    return targetBackend;
  }

  @Nullable
  private static TargetBackend loadFromInstrumentationArguments() {
    String targetBackendName = loadNameFromInstrumentationArguments();
    if (targetBackendName == null) {
      return null;
    }

    switch (targetBackendName) {
      case "emulator":
        return TargetBackend.EMULATOR;
      case "qa":
        return TargetBackend.QA;
      case "nightly":
        return TargetBackend.NIGHTLY;
      case "prod":
        return TargetBackend.PROD;
      default:
        throw new IllegalArgumentException("Unknown target backend name: " + targetBackendName);
    }
  }

  @Nullable
  private static String loadNameFromInstrumentationArguments() {
    Object valueObj = InstrumentationRegistry.getArguments().get(INSTRUMENTATION_ARGUMENTS_KEY);
    if (valueObj == null) {
      Logger.debug(
          "TargetBackend",
          "loadNameFromInstrumentationArguments() did not find value for key \""
              + INSTRUMENTATION_ARGUMENTS_KEY
              + "\" in InstrumentationRegistry.getArguments(); returning null");
      return null;
    }

    String value = valueObj.toString();
    Logger.debug(
        "TargetBackend",
        "loadNameFromInstrumentationArguments() found value for key \""
            + INSTRUMENTATION_ARGUMENTS_KEY
            + "\" in InstrumentationRegistry.getArguments(): "
            + value);

    return value;
  }
}
