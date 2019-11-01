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
 * An {@code Encoder} takes objects and writes them in {@link EncoderContext}.
 *
 * <p>This interface should be used as base for interfaces with concrete {@code EncoderContexts}.
 */
interface Encoder<T, EncoderContext> {

  /** Encode {@code obj} using {@code EncoderContext}. */
  void encode(@Nullable T obj, @NonNull EncoderContext context)
      throws EncodingException, IOException;
}
