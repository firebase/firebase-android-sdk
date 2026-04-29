/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore.util;

import com.google.firebase.firestore.DocumentReference;

/** Holds information a deserialization operation needs to complete the job. */
class DeserializeContext {
  /**
   * Immutable class representing the path to a specific field in an object. Used to provide better
   * error messages.
   */
  static class ErrorPath {
    static final ErrorPath EMPTY = new ErrorPath(null, null, 0);

    private final int length;
    private final ErrorPath parent;
    private final String name;

    ErrorPath child(String name) {
      return new ErrorPath(this, name, length + 1);
    }

    @Override
    public String toString() {
      if (length == 0) {
        return "";
      } else if (length == 1) {
        return name;
      } else {
        // This is not very efficient, but it's only hit if there's an error.
        return parent.toString() + "." + name;
      }
    }

    ErrorPath(ErrorPath parent, String name, int length) {
      this.parent = parent;
      this.name = name;
      this.length = length;
    }

    int getLength() {
      return length;
    }
  }

  /** Current path to the field being deserialized, used for better error messages. */
  final ErrorPath errorPath;

  /** Value used to set to {@link DocumentId} annotated fields during deserialization, if any. */
  final DocumentReference documentRef;

  DeserializeContext newInstanceWithErrorPath(ErrorPath newPath) {
    return new DeserializeContext(newPath, documentRef);
  }

  DeserializeContext(ErrorPath path, DocumentReference docRef) {
    errorPath = path;
    documentRef = docRef;
  }
}
