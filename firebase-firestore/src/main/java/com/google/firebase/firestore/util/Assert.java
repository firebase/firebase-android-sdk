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

/** A helper class to provide static runtime assertion helpers. */
public class Assert {
  /**
   * Triggers a hard assertion. The condition is guaranteed to be checked at runtime. If the
   * condition is false an AssertionError will be thrown. The string messageFormat will be formatted
   * with the provided args using {@link String#format(String, Object...)}.
   *
   * @param condition The condition to check
   * @param messageFormat The message to throw if the condition is false, formatted using {@link
   *     String#format(String, Object...)}.
   * @param args The args to pass to String.format
   */
  public static void hardAssert(boolean condition, String messageFormat, Object... args) {
    if (!condition) {
      throw fail(messageFormat, args);
    }
  }

  /**
   * Throws an AssertionError with the provided message. The string messageFormat will be formatted
   * with the provided args using {@link String#format(String, Object...)}. The method returns an
   * AssertionError so it can be used with a throw statement. However, the method itself throws an
   * AssertionError so fail will not accidentally be silent if the throw is forgotten.
   *
   * @param messageFormat The message to throw if the assertion is failed, formatted using {@link
   *     String#format(String, Object...)}.
   * @param args The args to pass to {@link String#format(String, Object...)}
   */
  public static AssertionError fail(String messageFormat, Object... args) {
    throw new AssertionError(format(messageFormat, args));
  }

  /**
   * Throws an AssertionError with the provided message and cause. The string messageFormat will be
   * formatted with the provided args using {@link String#format(String, Object...)}. The method
   * returns an AssertionError so it can be used with a throw statement. However, the method itself
   * throws an AssertionError so fail will not accidentally be silent if the throw is forgotten.
   *
   * @param messageFormat The message to throw if the assertion is failed, formatted using {@link
   *     String#format(String, Object...)}.
   * @param args The args to pass to {@link String#format(String, Object...)}
   */
  public static AssertionError fail(Throwable cause, String messageFormat, Object... args) {
    throw ApiUtil.newAssertionError(format(messageFormat, args), cause);
  }

  /**
   * Formats a message for an AssertionError. The string messageFormat will be formatted with the
   * provided args using {@link String#format(String, Object...)}.
   *
   * @param messageFormat The message to throw if the assertion is failed, formatted using {@link
   *     String#format(String, Object...)}.
   * @param args The args to pass to {@link String#format(String, Object...)}
   */
  private static String format(String messageFormat, Object... args) {
    return "INTERNAL ASSERTION FAILED: " + String.format(messageFormat, args);
  }
}
