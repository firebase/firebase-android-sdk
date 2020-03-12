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

/**
 * An {@link Encoder} takes objects and writes them in {@code EncoderContext}.
 *
 * <p>This interface should be used as base for interfaces with concrete {@code Contexts}.
 */
interface Encoder<TValue, TContext> {

  /** Encode {@code obj} using {@code TContext}. */
  void encode(@NonNull TValue obj, @NonNull TContext context) throws IOException;
}
