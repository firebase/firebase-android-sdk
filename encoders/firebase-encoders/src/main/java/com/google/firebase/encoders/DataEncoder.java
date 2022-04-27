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
import java.io.IOException;
import java.io.Writer;

/**
 * High level entry point for consumers of the encoders API.
 *
 * <p>A class implementing this interface should have enough context to transparently encode an
 * object hierarchy and return it in the right format. Clients of this interface are not concerned
 * with how objects are encoded.
 */
public interface DataEncoder {

  /** Encodes {@code obj} into {@code writer}. */
  void encode(@NonNull Object obj, @NonNull Writer writer) throws IOException;

  /** Returns the string-encoded representation of {@code obj}. */
  @NonNull
  String encode(@NonNull Object obj);
}
