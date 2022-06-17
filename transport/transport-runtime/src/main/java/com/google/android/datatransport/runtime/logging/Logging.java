// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime.logging;

import android.os.Build;
import android.util.Log;

public final class Logging {
  private static final String LOG_PREFIX = "TRuntime.";
  private static final int MAX_LOG_TAG_SIZE_IN_SDK_N = 23;

  private Logging() {}

  private static String getTag(String tag) {
    if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) return concatTag(LOG_PREFIX, tag);

    return LOG_PREFIX + tag;
  }

  private static String concatTag(String prefix, String tag) {
    String concatText = prefix + tag;

    if (concatText.length() > MAX_LOG_TAG_SIZE_IN_SDK_N)
      concatText = concatText.substring(0, MAX_LOG_TAG_SIZE_IN_SDK_N);

    return concatText;
  }

  public static void d(String tag, String message) {
    tag = getTag(tag);
    if (Log.isLoggable(tag, Log.DEBUG)) {
      Log.d(tag, message);
    }
  }

  public static void d(String tag, String message, Object arg1) {
    tag = getTag(tag);
    if (Log.isLoggable(tag, Log.DEBUG)) {
      Log.d(tag, String.format(message, arg1));
    }
  }

  public static void d(String tag, String message, Object arg1, Object arg2) {
    tag = getTag(tag);
    if (Log.isLoggable(tag, Log.DEBUG)) {
      Log.d(tag, String.format(message, arg1, arg2));
    }
  }

  public static void d(String tag, String message, Object... args) {
    tag = getTag(tag);
    if (Log.isLoggable(tag, Log.DEBUG)) {
      Log.d(tag, String.format(message, args));
    }
  }

  public static void i(String tag, String message, Object arg1) {
    tag = getTag(tag);
    if (Log.isLoggable(tag, Log.INFO)) {
      Log.i(tag, String.format(message, arg1));
    }
  }

  public static void e(String tag, String message, Throwable e) {
    tag = getTag(tag);
    if (Log.isLoggable(tag, Log.ERROR)) {
      Log.e(tag, message, e);
    }
  }

  public static void w(String tag, String message, Object arg1) {
    tag = getTag(tag);
    if (Log.isLoggable(tag, Log.WARN)) {
      Log.w(tag, String.format(message, arg1));
    }
  }
}
