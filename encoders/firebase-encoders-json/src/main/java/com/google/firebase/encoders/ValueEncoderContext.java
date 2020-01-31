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
import androidx.annotation.Nullable;
import java.io.IOException;

/**
 * Enables encoding objects as target primitive types.
 *
 * <p>A target primitive type is a primitive type in the target encoding format. That includes
 * numbers, strings, and booleans.
 */
public interface ValueEncoderContext {

  /** Adds {@code value} as a primitive encoded value. */
  @NonNull
  ValueEncoderContext add(@Nullable String value) throws IOException;

  /** Adds {@code value} as a primitive encoded value. */
  @NonNull
  ValueEncoderContext add(double value) throws IOException;

  /** Adds {@code value} as a primitive encoded value. */
  @NonNull
  ValueEncoderContext add(int value) throws IOException;

  /** Adds {@code value} as a primitive encoded value. */
  @NonNull
  ValueEncoderContext add(long value) throws IOException;

  /** Adds {@code value} as a primitive encoded value. */
  @NonNull
  ValueEncoderContext add(boolean value) throws IOException;

  /** Adds {@code value} as a encoded array of bytes. */
  @NonNull
  ValueEncoderContext add(@NonNull byte[] bytes) throws IOException;
}
