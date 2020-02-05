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

package com.google.firebase.encoders;

import androidx.annotation.NonNull;

/**
 * An exception thrown when an {@code Encoder} ends up in an invalid state, or if the provided
 * object is in an state that cannot be encoded.
 */
public final class EncodingException extends RuntimeException {

  /** Creates a {@code EncodingException} with the given message. */
  public EncodingException(@NonNull String message) {
    super(message);
  }

  /** Creates a {@code EncodingException} with the given message and cause. */
  public EncodingException(@NonNull String message, @NonNull Exception cause) {
    super(message, cause);
  }
}
