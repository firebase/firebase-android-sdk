// Copyright 2020 Google LLC
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

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;
import javax.annotation.Nullable;

public class Strings {
  /**
   * Returns the given {@code template} string with each occurrence of {@code "%s"} replaced with
   * the corresponding argument value from {@code args}; or, if the placeholder and argument counts
   * do not match, returns a best-effort form of that string. Will not throw an exception under
   * normal conditions.
   *
   * <p><b>Note:</b> For most string-formatting needs, use {@link String#format String.format},
   * {@link java.io.PrintWriter#format PrintWriter.format}, and related methods. These support the
   * full range of <a
   * href="https://docs.oracle.com/javase/9/docs/api/java/util/Formatter.html#syntax">format
   * specifiers</a>, and alert you to usage errors by throwing {@link
   * java.util.IllegalFormatException}.
   *
   * <p>In certain cases, such as outputting debugging information or constructing a message to be
   * used for another unchecked exception, an exception during string formatting would serve little
   * purpose except to supplant the real information you were trying to provide. These are the cases
   * this method is made for; it instead generates a best-effort string with all supplied argument
   * values present. This method is also useful in environments such as GWT where {@code
   * String.format} is not available. As an example, method implementations of the {@link
   * Preconditions} class use this formatter, for both of the reasons just discussed.
   *
   * <p><b>Warning:</b> Only the exact two-character placeholder sequence {@code "%s"} is
   * recognized.
   *
   * @param template a string containing zero or more {@code "%s"} placeholder sequences. {@code
   *     null} is treated as the four-character string {@code "null"}.
   * @param args the arguments to be substituted into the message template. The first argument
   *     specified is substituted for the first occurrence of {@code "%s"} in the template, and so
   *     forth. A {@code null} argument is converted to the four-character string {@code "null"};
   *     non-null values are converted to strings using {@link Object#toString()}.
   * @since 25.1
   */
  public static String lenientFormat(@Nullable String template, @Nullable Object... args) {
    template = String.valueOf(template); // null -> "null"

    if (args == null) {
      args = new Object[] {"(Object[])null"};
    } else {
      for (int i = 0; i < args.length; i++) {
        args[i] = lenientToString(args[i]);
      }
    }

    // start substituting the arguments into the '%s' placeholders
    StringBuilder builder = new StringBuilder(template.length() + 16 * args.length);
    int templateStart = 0;
    int i = 0;
    while (i < args.length) {
      int placeholderStart = template.indexOf("%s", templateStart);
      if (placeholderStart == -1) {
        break;
      }
      builder.append(template, templateStart, placeholderStart);
      builder.append(args[i++]);
      templateStart = placeholderStart + 2;
    }
    builder.append(template, templateStart, template.length());

    // if we run out of placeholders, append the extra args in square braces
    if (i < args.length) {
      builder.append(" [");
      builder.append(args[i++]);
      while (i < args.length) {
        builder.append(", ");
        builder.append(args[i++]);
      }
      builder.append(']');
    }

    return builder.toString();
  }

  private static String lenientToString(@Nullable Object o) {
    try {
      return String.valueOf(o);
    } catch (Exception e) {
      // Default toString() behavior - see Object.toString()
      String objectToString =
          o.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(o));
      // Logger is created inline with fixed name to avoid forcing Proguard to create another class.
      Logger.getLogger("com.google.firebase.firestore.util.Strings")
          .log(WARNING, "Exception during lenientFormat for " + objectToString, e);
      return "<" + objectToString + " threw " + e.getClass().getName() + ">";
    }
  }
}
