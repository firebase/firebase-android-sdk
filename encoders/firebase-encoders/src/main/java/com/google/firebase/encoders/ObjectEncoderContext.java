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
 * Enables encoding objects as maps of name -> value.
 *
 * <p>The format of the entries is as follows: names <strong>are</strong> strings, values can either
 * be primitive types, arrays/collections, or nested objects. Classes implementing this interface
 * may provide users with the means of registring {@link Encoder} for types beforehand.
 */
public interface ObjectEncoderContext {

  /**
   * Add an entry with {@code name} mapped to the encoded version of {@code obj}.
   *
   * <p>{@code obj} can be a regular type with a matching {@code Encoder} registered. In this case,
   * the value of the entry will be the encoded version of {@code obj}.
   *
   * <p>{@code obj} can be an array. If the elements of the array are primitive types, they will be
   * directly encoded. Otherwise, the matching {@code Encoder} registered for the type will be used.
   * In this case, the value of the entry will be an encoded array obtained by sequencially applying
   * the encoder to each element of the array. Nested arrays are supported.
   *
   * <p>{@code obj} can be a {@link java.util.Collection}. The matching {@code Encoder} registered
   * for the type contained within the collection will be used. In this case, the value of the entry
   * will be an encoded array obtained by sequencially applying the encoder to each element of the
   * array. Nested collections are supported.
   *
   * <p>If {@code obj} does not match any of the criteria above, or if there's no matching {@code
   * Encoder} for the type, an {@code EncodingException} will be thrown. Also, any exceptions thrown
   * by the encoders will be propagated.
   */
  @NonNull
  ObjectEncoderContext add(@NonNull String name, @Nullable Object obj)
      throws IOException, EncodingException;

  /** Add an entry with {@code name} mapped to the encoded primitive type of {@code value}. */
  @NonNull
  ObjectEncoderContext add(@NonNull String name, double value)
      throws IOException, EncodingException;

  /** Add an entry with {@code name} mapped to the encoded primitive type of {@code value}. */
  @NonNull
  ObjectEncoderContext add(@NonNull String name, int value) throws IOException, EncodingException;

  /** Add an entry with {@code name} mapped to the encoded primitive type of {@code value}. */
  @NonNull
  ObjectEncoderContext add(@NonNull String name, long value) throws IOException, EncodingException;

  /** Add an entry with {@code name} mapped to the encoded primitive type of {@code value}. */
  @NonNull
  ObjectEncoderContext add(@NonNull String name, boolean value)
      throws IOException, EncodingException;
}
