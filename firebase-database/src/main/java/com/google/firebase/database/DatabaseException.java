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

package com.google.firebase.database;

import androidx.annotation.RestrictTo;

/**
 * This error is thrown when the Firebase Database library is unable to operate on the input it has
 * been given.
 */
public class DatabaseException extends RuntimeException {

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param message A human readable description of the error
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public DatabaseException(String message) {
    super(message);
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param message A human readable description of the error
   * @param cause The underlying cause for this error
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public DatabaseException(String message, Throwable cause) {
    super(message, cause);
  }
}
