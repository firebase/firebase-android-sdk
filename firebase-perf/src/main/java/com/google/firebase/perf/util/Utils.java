// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.firebase.perf.logging.AndroidLogger;
import okhttp3.HttpUrl;

/** Utility methods */
public class Utils {

  private static Boolean isDebugLoggingEnabled = null;

  /**
   * Strips out the sensitive info like username/password, query parameters if any from the URL
   * string
   *
   * @param urlString URL String
   * @return URL String without sensitive information
   */
  public static String stripSensitiveInfo(@NonNull String urlString) {
    HttpUrl url = HttpUrl.parse(urlString);

    if (url != null) {
      // Not using url.redact() because it unnecessarily appends "/..." to the string.
      return url.newBuilder().username("").password("").query(null).fragment(null).toString();
    }

    return urlString;
  }

  /**
   * Truncates URL to nearest complete path element that is no longer than maxLength.
   *
   * @param urlString input url string.
   * @param maxLength the length to truncate to.
   * @return url string after truncation
   */
  public static String truncateURL(String urlString, int maxLength) {
    if (urlString.length() <= maxLength) {
      return urlString;
    }
    // Truncate exactly at complete path element.
    if (urlString.charAt(maxLength) == '/') {
      return urlString.substring(0, maxLength);
    }

    HttpUrl url = HttpUrl.parse(urlString);
    if (url == null) {
      return urlString.substring(0, maxLength);
    }

    String path = url.encodedPath();
    // Check if path contains '/', otherwise the last '/' could be the "://" after protocol.
    if (path.lastIndexOf('/') >= 0) {
      int lastSlash = urlString.lastIndexOf('/', maxLength - 1);
      if (lastSlash >= 0) {
        // return substring up to but not include the lastSlash.
        return urlString.substring(0, lastSlash);
      }
    }
    return urlString.substring(0, maxLength);
  }

  /**
   * An utility method converts up to first four bytes into an integer in little endian order.
   *
   * @param buffer A byte array.
   * @return integer value.
   */
  // TODO(b/117439584): Convert to using the first 8 bytes and returning a long.
  public static int bufferToInt(@NonNull byte[] buffer) {
    int ret = 0;
    for (int i = 0; i < 4 && i < buffer.length; ++i) {
      ret |= (buffer[i] & 0xFF) << (i * 8);
    }
    return ret;
  }

  /** @return true if logcat is enabled via AndroidManifest meta data flag, false otherwise */
  public static boolean isDebugLoggingEnabled(@NonNull Context appContext) {
    if (isDebugLoggingEnabled != null) {
      return isDebugLoggingEnabled;
    }

    try {
      // This block of code may take about 10ms to execute,avoid running it from main thread.
      ApplicationInfo ai =
          appContext
              .getPackageManager()
              .getApplicationInfo(appContext.getPackageName(), PackageManager.GET_META_DATA);
      Bundle bundle = ai.metaData;
      // No meta data defined so return false because performance is enabled by default
      isDebugLoggingEnabled = bundle.getBoolean("firebase_performance_logcat_enabled", false);
      return isDebugLoggingEnabled;
    } catch (NameNotFoundException | NullPointerException e) {
      AndroidLogger.getInstance().debug("No perf logcat meta data found " + e.getMessage());
    }
    return false;
  }

  /**
   * Returns the {@code int} nearest in value to {@code value}.
   *
   * @param value any {@code long} value
   * @return the same value cast to {@code int} if it is in the range of the {@code int} type,
   *     {@link Integer#MAX_VALUE} if it is too large, or {@link Integer#MIN_VALUE} if it is too
   *     small
   */
  public static int saturatedIntCast(long value) {
    if (value > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    if (value < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return (int) value;
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression, String errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  public static String invoker() {
    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
    StringBuilder builder = new StringBuilder("\n");
    for (int i = 3, length = elements.length; i < length; i++) {
      String clazz = elements[i].getClassName().replace("com.google.firebase", "c.g.f");
      String method = elements[i].getMethodName();
      String file = elements[i].getFileName();
      int line = elements[i].getLineNumber();
      builder.append(String.format("%s#%s(%s:%s)\n", clazz, method, file, line));
    }
    return builder.toString();
  }
}
