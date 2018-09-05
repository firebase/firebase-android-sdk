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

package com.google.firebase.firestore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A dot separated path for navigating sub-objects with in a document */
public final class FieldPath extends BasePath<FieldPath> {

  public static final FieldPath KEY_PATH = fromSingleSegment(DocumentKey.KEY_FIELD_NAME);
  public static final FieldPath EMPTY_PATH = new FieldPath(Collections.emptyList());

  private FieldPath(List<String> segments) {
    super(segments);
  }

  /** Creates a {@code FieldPath} with a single field. Does not split on dots. */
  public static FieldPath fromSingleSegment(String fieldName) {
    return new FieldPath(Collections.singletonList(fieldName));
  }

  /** Creates a {@code FieldPath} from a list of parsed field path segments. */
  public static FieldPath fromSegments(List<String> segments) {
    return segments.isEmpty() ? FieldPath.EMPTY_PATH : new FieldPath(segments);
  }

  @Override
  FieldPath createPathWithSegments(List<String> segments) {
    return new FieldPath(segments);
  }

  /** Creates a {@code FieldPath} from a server-encoded field path. */
  public static FieldPath fromServerFormat(String path) {
    List<String> res = new ArrayList<>();
    StringBuilder builder = new StringBuilder();
    // TODO: We should make this more strict.
    // Right now, it allows non-identifier path components, even if they aren't escaped.

    int i = 0;

    // If we're inside '`' backticks, then we should ignore '.' dots.
    boolean inBackticks = false;

    while (i < path.length()) {
      char c = path.charAt(i);
      if (c == '\\') {
        if (i + 1 == path.length()) {
          throw new IllegalArgumentException("Trailing escape character is not allowed");
        }
        i++;
        builder.append(path.charAt(i));
      } else if (c == '.') {
        if (!inBackticks) {
          String elem = builder.toString();
          if (elem.isEmpty()) {
            throw new IllegalArgumentException(
                "Invalid field path ("
                    + path
                    + "). Paths must not be empty, begin with '.', end with '.', or contain '..'");
          }
          builder = new StringBuilder();
          res.add(elem);
        } else {
          // escaped, append to current segment
          builder.append(c);
        }
      } else if (c == '`') {
        inBackticks = !inBackticks;
      } else {
        builder.append(c);
      }
      i++;
    }
    String lastElem = builder.toString();
    if (lastElem.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid field path ("
              + path
              + "). Paths must not be empty, begin with '.', end with '.', or contain '..'");
    }
    res.add(lastElem);
    return new FieldPath(res);
  }

  /**
   * Return true if the string could be used as a segment in a field path without escaping. Valid
   * identifies follow the regex [a-zA-Z_][a-zA-Z0-9_]*
   */
  private static boolean isValidIdentifier(String identifier) {
    if (identifier.isEmpty()) {
      return false;
    }

    char first = identifier.charAt(0);
    if (first != '_' && (first < 'a' || first > 'z') && (first < 'A' || first > 'Z')) {
      return false;
    }
    for (int i = 1; i < identifier.length(); i++) {
      char c = identifier.charAt(i);
      if (c != '_' && (c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9')) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String canonicalString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        builder.append(".");
      }
      // Escape backslashes and dots.
      String escaped = segments.get(i);
      escaped = escaped.replace("\\", "\\\\").replace("`", "\\`");

      if (!isValidIdentifier(escaped)) {
        escaped = '`' + escaped + '`';
      }

      builder.append(escaped);
    }
    return builder.toString();
  }

  public boolean isKeyField() {
    return this.equals(KEY_PATH);
  }
}
