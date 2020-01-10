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

package com.google.firebase.abt;

/**
 * An exception thrown when there's an issue with a call to the {@link
 * com.google.firebase.abt.FirebaseABTesting} API.
 *
 * @author Miraziz Yusupov
 */
public class AbtException extends Exception {

  /** Creates an ABT exception with the given message. */
  public AbtException(String message) {
    super(message);
  }

  /** Creates an ABT exception with the given message and cause. */
  public AbtException(String message, Exception cause) {
    super(message, cause);
  }
}
