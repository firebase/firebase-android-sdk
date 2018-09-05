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

package com.google.firebase.firestore.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Papers over API level differences in Android and the JDK in a way that's warning free.
 * Frequently, cleaning up an Android API level warning triggers a warning about using an obsolete
 * mechanism.
 */
public class ApiUtil {
  /** Creates an AssertionError with a cause. */
  @SuppressWarnings("UnnecessaryInitCause") // AssertionError(String, Throwable) is API 19
  public static AssertionError newAssertionError(String message, Throwable cause) {
    AssertionError error = new AssertionError(message);
    error.initCause(cause);
    return error;
  }

  /**
   * Creates a new instance of the type from the given constructor, converting checked exceptions to
   * unchecked.
   */
  static <T> T newInstance(Constructor<T> constructor) {
    // Multi-catch of these types compiles down to using a new ReflectiveOperationException base
    // class from API 19.
    try {
      return constructor.newInstance();
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Invokes the given method on the given instance with the given arguments, converting checked
   * exceptions to unchecked.
   */
  static Object invoke(Method method, Object instance, Object... args) {
    // Multi-catch of these types compiles down to using a new ReflectiveOperationException base
    // class from API 19.
    try {
      return method.invoke(instance, args);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
