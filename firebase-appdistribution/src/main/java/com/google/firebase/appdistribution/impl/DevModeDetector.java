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

package com.google.firebase.appdistribution.impl;

import androidx.annotation.Nullable;
import java.lang.reflect.Method;
import javax.inject.Inject;

/**
 * A detector to recognize when the SDK is in development mode, which disables any functionality
 * that requires the tester to have signed in.
 */
class DevModeDetector {

  @Inject
  DevModeDetector() {}

  boolean isDevModeEnabled() {
    return Boolean.valueOf(getSystemProperty("debug.firebase.appdistro.devmode"));
  }

  @Nullable
  @SuppressWarnings({"unchecked", "PrivateApi"})
  private static String getSystemProperty(String propertyName) {
    String className = "android.os.SystemProperties";
    try {
      Class<?> sysProps = Class.forName(className);
      Method method = sysProps.getDeclaredMethod("get", String.class);
      Object result = method.invoke(null, propertyName);
      if (result != null && String.class.isAssignableFrom(result.getClass())) {
        return (String) result;
      }
    } catch (Exception e) {
      // do nothing
    }
    return null;
  }
}
