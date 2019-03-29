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

package com.google.firebase.testing.common;

import android.app.Activity;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A runner for smoke tests.
 *
 * <p>This class will eventually become a JUnit runner, but it is simply a utility class for now.
 *
 * <p>All smoke tests should manually use this class until JUnit integration is complete. Test
 * methods must be annotated with {@link SmokeTest} and must be in the test's primary activity.
 *
 * <p>This design enables test code to be built, obfuscated, and run like any other application's
 * code. Test methods may be fully defined in one source tree, instead of splitting them across the
 * two APKs used during testing.
 */
public class SmokeTestRunner {

  private static final String TAG = "SmokeTestRunner";

  /**
   * Runs all smoke tests in the provided activity.
   *
   * <p>This method reflectively obtains all methods defined in the activity with the {@link
   * SmokeTest} annotation. These are executed sequentially. Any exception thrown by the test
   * methods is rethrown here without wrapping.
   */
  public static void runSmokeTests(Activity instance) throws Exception {
    Method[] methods = instance.getClass().getMethods();
    Log.d(TAG, "Searching for SmokeTest methods.");
    for (Method method : methods) {
      if (method.getAnnotation(SmokeTest.class) == null) {
        continue;
      }

      Log.d(TAG, "Preparing to run method, " + method.getName() + ".");
      try {
        method.invoke(instance);
        Log.d(TAG, "Test method complete.");
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException("Test method is not public", ex);
      } catch (InvocationTargetException ex) {
        Throwable t = ex.getCause();
        if (t instanceof Exception) {
          throw (Exception) t;
        } else if (t instanceof Error) {
          throw (Error) t;
        }

        throw new IllegalStateException("Test threw unexpected Throwable", t);
      }
    }

    Log.d(TAG, "Finished test runs.");
  }
}
