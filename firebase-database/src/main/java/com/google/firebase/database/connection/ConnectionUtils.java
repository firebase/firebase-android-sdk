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

package com.google.firebase.database.connection;

import java.util.ArrayList;
import java.util.List;

public class ConnectionUtils {
  public static List<String> stringToPath(String string) {
    List<String> path = new ArrayList<String>();
    // OMG, why does Java not have filter ?!? !121111!~
    String[] segments = string.split("/", -1);
    for (int i = 0; i < segments.length; i++) {
      if (!segments[i].isEmpty()) {
        path.add(segments[i]);
      }
    }
    return path;
  }

  public static String pathToString(List<String> segments) {
    if (segments.isEmpty()) {
      return "/";
    } else {
      StringBuilder path = new StringBuilder();
      boolean first = true;
      for (String segment : segments) {
        if (!first) {
          path.append("/");
        }
        first = false;
        path.append(segment);
      }
      return path.toString();
    }
  }

  public static Long longFromObject(Object o) {
    if (o instanceof Integer) {
      return Long.valueOf((Integer) o);
    } else if (o instanceof Long) {
      return (Long) o;
    } else {
      return null;
    }
  }

  // TODO: Merge these with Utils from firebase-database
  public static void hardAssert(boolean condition) {
    hardAssert(condition, "");
  }

  public static void hardAssert(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new AssertionError("hardAssert failed: " + String.format(message, args));
    }
  }
}
