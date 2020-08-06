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
import java.lang.annotation.Annotation;

/**
 * Implemented by concrete {@link DataDecoder} builders.
 *
 * <p>Used by clients to configure decoders without coupling to a particular decoder format.
 */
public interface DecoderConfig<T extends DecoderConfig<T>> {
  @NonNull
  <U> T register(@NonNull Class<U> clazz, @NonNull ObjectDecoder<? extends U> objectDecoder);

  @NonNull
  <U> T register(@NonNull Class<U> clazz, @NonNull ValueDecoder<? extends U> valueDecoder);

  @NonNull
  <U extends Annotation> T register(
      @NonNull Class<U> clazz, @NonNull AnnotatedFieldHandler<U> handler);
}
