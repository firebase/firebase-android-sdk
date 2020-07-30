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
 * An {@link Decoder} takes objects and writes them in {@code DecoderContext}.
 *
 * <p>This interface should be used as base for interfaces with concrete {@code Contexts}.
 */
public interface Decoder<TValue, TContext> {
  @NonNull
  TypeCreator<TValue> decode(@NonNull TContext ctx);
}
