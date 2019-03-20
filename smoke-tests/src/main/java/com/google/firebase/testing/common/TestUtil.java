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

import com.google.android.gms.tasks.Task;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Test utilities invoked from the application APK. */
public final class TestUtil {

  private static Method waitForSuccessImpl;

  private TestUtil() {}

  @SuppressWarnings("unchecked")
  public static <T> T waitForSuccess(Task<T> task) throws Exception {
    if (waitForSuccessImpl == null) {
      try {
        Class<?> impl = Class.forName("com.google.firebase.testing.common.TestApkUtil");
        waitForSuccessImpl = impl.getMethod("waitForSuccess", Task.class);
      } catch (Exception ex) {
        throw new IllegalStateException("Error initializing test framework", ex);
      }
    }

    try {
      return (T) waitForSuccessImpl.invoke(null, task);
    } catch (InvocationTargetException ex) {
      Throwable t = ex.getCause();
      if (t instanceof Exception) {
        throw (Exception) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      }

      throw new IllegalStateException("Task threw unexpected Throwable", t);
    } catch (Exception ex) {
      throw new IllegalStateException("Error invoking test APK", ex);
    }
  }
}
