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
import java.io.IOException;

/**
 * {@link ValueDecoderContext} enables {@link ValueDecoder} to decode objects. {@link
 * ValueDecoderContext} should keep its decoding logic into a {@link ValueDecoderContext}. Decoding
 * method inside {@link ValueDecoderContext} should only be called once to retrieve decoded value.
 *
 * <p>Note: Object decoded by {@link ValueDecoder} was represented as a single or primitive value.
 */
public interface ValueDecoderContext {
  /** Indicate Object was represented by {@code String}, , return decoded value. */
  @NonNull
  String decodeString() throws IOException;

  /** Indicate Object was represented by {@code boolean}, return decoded value. */
  boolean decodeBoolean() throws IOException;

  /** Indicate Object was represented by {@code int}, return decoded value. */
  int decodeInteger() throws IOException;

  /** Indicate Object was represented by {@code long}, return decoded value. */
  long decodeLong() throws IOException;

  /** Indicate Object was represented by {@code double}, return decoded value. */
  double decodeDouble() throws IOException;
}
