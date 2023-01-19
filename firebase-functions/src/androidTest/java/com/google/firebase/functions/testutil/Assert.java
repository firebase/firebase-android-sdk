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

package com.google.firebase.functions.testutil;

/**
 * A set of assertion methods useful for writing tests. Only failed assertions are recorded. These
 * methods can be used directly: <code>Assert.assertEquals(...)</code>, however, they read better if
 * they are referenced through static import:
 *
 * <pre>
 * import static org.junit.Assert.*;
 *    ...
 *    assertEquals(...);
 * </pre>
 *
 * @see AssertionError
 * @since 4.0
 */
public class Assert {
  /** Protect constructor since it is a static only class */
  protected Assert() {}

  static String format(String message, Object expected, Object actual) {
    String formatted = "";
    if (message != null && !message.equals("")) {
      formatted = message + " ";
    }
    String expectedString = String.valueOf(expected);
    String actualString = String.valueOf(actual);
    if (expectedString.equals(actualString)) {
      return formatted
          + "expected: "
          + formatClassAndValue(expected, expectedString)
          + " but was: "
          + formatClassAndValue(actual, actualString);
    } else {
      return formatted + "expected:<" + expectedString + "> but was:<" + actualString + ">";
    }
  }

  private static String formatClassAndValue(Object value, String valueString) {
    String className = value == null ? "null" : value.getClass().getName();
    return className + "<" + valueString + ">";
  }

  /**
   * Asserts that {@code runnable} throws an exception of type {@code expectedThrowable} when
   * executed. If it does, the exception object is returned. If it does not throw an exception, an
   * {@link AssertionError} is thrown. If it throws the wrong type of exception, an {@code
   * AssertionError} is thrown describing the mismatch; the exception that was actually thrown can
   * be obtained by calling {@link AssertionError#getCause}.
   *
   * @param expectedThrowable the expected type of the exception
   * @param runnable a function that is expected to throw an exception when executed
   * @return the exception thrown by {@code runnable}
   * @since 4.13
   */
  public static <T extends Throwable> T assertThrows(
      Class<T> expectedThrowable, ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable actualThrown) {
      if (expectedThrowable.isInstance(actualThrown)) {
        @SuppressWarnings("unchecked")
        T retVal = (T) actualThrown;
        return retVal;
      } else {
        String mismatchMessage =
            format(
                "unexpected exception type thrown;",
                expectedThrowable.getSimpleName(),
                actualThrown.getClass().getSimpleName());

        // The AssertionError(String, Throwable) ctor is only available on JDK7.
        AssertionError assertionError = new AssertionError(mismatchMessage);
        assertionError.initCause(actualThrown);
        throw assertionError;
      }
    }
    String message =
        String.format(
            "expected %s to be thrown, but nothing was thrown", expectedThrowable.getSimpleName());
    throw new AssertionError(message);
  }
}
