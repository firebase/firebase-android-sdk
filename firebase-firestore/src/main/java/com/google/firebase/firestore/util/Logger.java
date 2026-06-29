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

import android.util.Log;
import com.google.firebase.firestore.BuildConfig;

/** Helper class to log messages for Firestore */
public class Logger {
  /** Log levels supported by this Logger class */
  public enum Level {
    DEBUG,
    WARN,
    NONE,
  }

  private static Level logLevel = Level.WARN;

  public static void setLogLevel(Level level) {
    Logger.logLevel = level;
  }

  /** Returns true iff the logger is logging at DEBUG level or above. */
  public static boolean isDebugEnabled() {
    return Logger.logLevel.ordinal() >= Level.DEBUG.ordinal();
  }

  private static void doLog(Level level, String tag, String toLog, Object... values) {
    if (level.ordinal() >= Logger.logLevel.ordinal()) {
      String prefix = String.format("(%s) [%s]: ", BuildConfig.VERSION_NAME, tag);

      // Prevent massive objects from being evaluated
      // by String.format(), which causes OOMs during .toString() generation.
      if (values.length > 0) {
        for (int i = 0; i < values.length; i++) {
          Object val = values[i];
          if (val == null) continue;

          if (val instanceof com.google.protobuf.MessageLite) {
            int protoSize = ((com.google.protobuf.MessageLite) val).getSerializedSize();

            // If the message is larger than 128KB, don't stringify it
            if (protoSize > 128 * 1024) {
              String messageType = val.getClass().getSimpleName();

              String readableSize;
              if (protoSize >= 1024 * 1024) {
                readableSize = String.format("%.2f MB", protoSize / (1024.0 * 1024.0));
              } else {
                readableSize = String.format("%.1f KB", protoSize / 1024.0);
              }

              values[i] =
                  String.format("<%s truncated for logging (Size: %s)>", messageType, readableSize);
            }
          }
          // Guard against standard massive Strings
          else if (val instanceof String) {
            String strVal = (String) val;
            if (strVal.length() > 4096) {
              values[i] = strVal.substring(0, 4096) + "... <String truncated>";
            }
          }
        }
      }

      String value = values.length > 0 ? String.format(toLog, values) : toLog;
      value = prefix + value;
      switch (level) {
        case DEBUG:
          Log.i("Firestore", value);
          return;
        case WARN:
          Log.w("Firestore", value);
          return;
        case NONE:
          throw new IllegalStateException("Trying to log something on level NONE");
      }
    }
  }

  public static void warn(String tag, String toLog, Object... values) {
    doLog(Level.WARN, tag, toLog, values);
  }

  public static void debug(String tag, String toLog, Object... values) {
    doLog(Level.DEBUG, tag, toLog, values);
  }
}
