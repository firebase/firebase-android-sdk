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

package com.google.firebase.decoders;

import androidx.annotation.NonNull;

/**
 * {@link ValueDecoderContext} enables {@link ValueDecoder} to decode objects. {@link
 * ValueDecoderContext} should keep its decoding logic into a {@link ValueDecoderContext}. Decoding
 * method inside {@link ValueDecoderContext} should only be called once to indicate which value was
 * used to represent the object and kept a {@link FieldRef} to retrieve decoded value from {@link
 * CreationContext}.
 *
 * <p>Note: Object decoded by {@link ValueDecoder} was represented as a single or primitive value.
 */
public interface ValueDecoderContext {
  /**
   * Indicate Object was represented by {@code String}, returned {@link FieldRef} will be used to
   * retrieve decoded value from {@link CreationContext}
   */
  @NonNull
  FieldRef.Boxed<String> decodeString();

  /**
   * Indicate Object was represented by {@code boolean}, returned {@link FieldRef} will be used to
   * retrieve decoded value from {@link CreationContext}
   */
  @NonNull
  FieldRef.Primitive<Boolean> decodeBoolean();

  /**
   * Indicate Object was represented by {@code int}, returned {@link FieldRef} will be used to
   * retrieve decoded value from {@link CreationContext}
   */
  @NonNull
  FieldRef.Primitive<Integer> decodeInteger();

  /**
   * Indicate Object was represented by {@code long}, returned {@link FieldRef} will be used to
   * retrieve decoded value from {@link CreationContext}
   */
  @NonNull
  FieldRef.Primitive<Long> decodeLong();

  /**
   * Indicate Object was represented by {@code double}, returned {@link FieldRef} will be used to
   * retrieve decoded value from {@link CreationContext}
   */
  @NonNull
  FieldRef.Primitive<Double> decodeDouble();
}
