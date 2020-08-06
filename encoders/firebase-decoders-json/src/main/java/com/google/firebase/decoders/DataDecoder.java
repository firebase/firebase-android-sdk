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
import java.io.InputStream;

/**
 * High level entry point for consumers of the decoders API.
 *
 * <p>A class implementing {@link DataDecoder} should have enough context to transparently decode an
 * object and return its decoded result. Clients of this interface are not concerned with how
 * objects are decoded.
 */
public interface DataDecoder {
  /**
   * Decode given InputStream into {@code TypeToken<T>}, and return type-safe result of type {@code
   * T}
   */
  @NonNull
  <T> T decode(@NonNull InputStream input, @NonNull TypeToken<T> typeToken) throws IOException;
}
