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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkArgument;
import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A {@code FieldPath} refers to a field in a document. The path may consist of a single field name
 * (referring to a top level field in the document), or a list of field names (referring to a nested
 * field in the document).
 */
public final class FieldPath {
  /** Matches any characters in a field path string that are reserved. */
  private static final Pattern RESERVED = Pattern.compile("[~*/\\[\\]]");

  private final com.google.firebase.firestore.model.FieldPath internalPath;

  private FieldPath(List<String> segments) {
    this.internalPath = com.google.firebase.firestore.model.FieldPath.fromSegments(segments);
  }

  private FieldPath(com.google.firebase.firestore.model.FieldPath internalPath) {
    this.internalPath = internalPath;
  }

  com.google.firebase.firestore.model.FieldPath getInternalPath() {
    return internalPath;
  }

  /**
   * Creates a {@code FieldPath} from the provided field names. If more than one field name is
   * provided, the path will point to a nested field in a document.
   *
   * @param fieldNames A list of field names.
   * @return A {@code FieldPath} that points to a field location in a document.
   */
  @NonNull
  public static FieldPath of(String... fieldNames) {
    checkArgument(fieldNames.length > 0, "Invalid field path. Provided path must not be empty.");

    for (int i = 0; i < fieldNames.length; ++i) {
      checkArgument(
          fieldNames[i] != null && !fieldNames[i].isEmpty(),
          "Invalid field name at argument " + (i + 1) + ". Field names must not be null or empty.");
    }

    return new FieldPath(Arrays.asList(fieldNames));
  }

  private static final FieldPath DOCUMENT_ID_INSTANCE =
      new FieldPath(com.google.firebase.firestore.model.FieldPath.KEY_PATH);

  /**
   * Returns A special sentinel {@code FieldPath} to refer to the ID of a document. It can be used
   * in queries to sort or filter by the document ID.
   */
  @NonNull
  public static FieldPath documentId() {
    return DOCUMENT_ID_INSTANCE;
  }

  /** Parses a field path string into a {@code FieldPath}, treating dots as separators. */
  static FieldPath fromDotSeparatedPath(@NonNull String path) {
    checkNotNull(path, "Provided field path must not be null.");
    checkArgument(
        !RESERVED.matcher(path).find(), "Use FieldPath.of() for field names containing '~*/[]'.");
    try {
      // By default, split() doesn't return empty leading and trailing segments. This can be enabled
      // by passing "-1" as the  limit.
      return com.google.firebase.firestore.FieldPath.of(path.split("\\.", -1));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid field path ("
              + path
              + "). Paths must not be empty, begin with '.', end with '.', or contain '..'");
    }
  }

  @Override
  public String toString() {
    return internalPath.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FieldPath fieldPath = (FieldPath) o;

    return internalPath.equals(fieldPath.internalPath);
  }

  @Override
  public int hashCode() {
    return internalPath.hashCode();
  }
}
