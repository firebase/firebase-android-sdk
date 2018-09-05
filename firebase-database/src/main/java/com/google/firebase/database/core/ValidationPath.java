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

package com.google.firebase.database.core;

import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dynamic (mutable) path used to count path lengths.
 *
 * <p>This class is used to efficiently check paths for valid length (in UTF8 bytes) and depth (used
 * in path validation).
 *
 * <p>The definition of a path always begins with '/'.
 */
public class ValidationPath {
  private final List<String> parts = new ArrayList<String>();
  private int byteLength = 0;

  public static final int MAX_PATH_LENGTH_BYTES = 768;
  public static final int MAX_PATH_DEPTH = 32;

  private ValidationPath(Path path) throws DatabaseException {
    for (ChildKey key : path) {
      parts.add(key.asString());
    }

    // Initialize to number of '/' chars needed in path.
    byteLength = Math.max(1, parts.size());
    for (int i = 0; i < parts.size(); i++) {
      byteLength += utf8Bytes(parts.get(i));
    }
    checkValid();
  }

  public static void validateWithObject(Path path, Object value) throws DatabaseException {
    new ValidationPath(path).withObject(value);
  }

  private void withObject(Object value) throws DatabaseException {
    if (value instanceof Map) {
      Map<String, Object> mapValue = (Map<String, Object>) value;
      for (String key : mapValue.keySet()) {
        if (key.startsWith(".")) {
          continue;
        }
        push(key);
        withObject(mapValue.get(key));
        pop();
      }
      return;
    }

    if (value instanceof List) {
      List listValue = (List) value;
      for (int i = 0; i < listValue.size(); ++i) {
        String key = Integer.toString(i);
        push(key);
        withObject(listValue.get(i));
        pop();
      }
    }
  }

  private void push(String child) throws DatabaseException {
    // Count the '/'
    if (parts.size() > 0) {
      byteLength += 1;
    }
    parts.add(child);
    byteLength += utf8Bytes(child);
    checkValid();
  }

  private String pop() {
    String last = parts.remove(parts.size() - 1);
    byteLength -= utf8Bytes(last);
    // Un-count the previous '/'
    if (parts.size() > 0) {
      byteLength -= 1;
    }
    return last;
  }

  private void checkValid() throws DatabaseException {
    if (byteLength > MAX_PATH_LENGTH_BYTES) {
      throw new DatabaseException(
          "Data has a key path longer than "
              + MAX_PATH_LENGTH_BYTES
              + " bytes ("
              + byteLength
              + ").");
    }
    if (parts.size() > MAX_PATH_DEPTH) {
      throw new DatabaseException(
          "Path specified exceeds the maximum depth that can be written ("
              + MAX_PATH_DEPTH
              + ") or object contains a cycle "
              + toErrorString());
    }
  }

  private String toErrorString() {
    if (parts.size() == 0) {
      return "";
    }
    return "in path \'" + joinStringList("/", parts) + "\'";
  }

  private static String joinStringList(String delimeter, List<String> parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        sb.append(delimeter);
      }
      sb.append(parts.get(i));
    }
    return sb.toString();
  }

  /*
   * Compute UTF-8 encoding size in bytes w/o realizing the string in
   * memory (which is what String.getBytes('UTF-8').length would do).
   */
  private static int utf8Bytes(CharSequence sequence) {
    int count = 0;
    for (int i = 0, len = sequence.length(); i < len; i++) {
      char ch = sequence.charAt(i);
      if (ch <= 0x7F) {
        count++;
      } else if (ch <= 0x7FF) {
        count += 2;
      } else if (Character.isHighSurrogate(ch)) {
        count += 4;
        ++i;
      } else {
        count += 3;
      }
    }
    return count;
  }
}
