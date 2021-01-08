// Copyright 2020 Google LLC
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

package com.google.firebase.components;

/**
 * Exception thrown when a {@link ComponentRegistrar} is invalid.
 *
 * <p>This can happen for the following reasons:
 *
 * <ul>
 *   <li>Class does not implement {@link ComponentRegistrar}
 *   <li>Class is private or has a private constructor
 *   <li>Class's constructor throws an exception
 */
public class InvalidRegistrarException extends RuntimeException {
  public InvalidRegistrarException(String message) {
    super(message);
  }

  public InvalidRegistrarException(String message, Throwable cause) {
    super(message, cause);
  }
}
