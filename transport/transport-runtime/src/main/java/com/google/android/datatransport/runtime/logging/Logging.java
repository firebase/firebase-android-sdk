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
  private Logging() {}

  private static String getTag(String tag) {
    // isLoggable has a max limit of 23 chars for versions lower than 7.0
    return android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.N ? "TRuntime." + tag : "";
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
