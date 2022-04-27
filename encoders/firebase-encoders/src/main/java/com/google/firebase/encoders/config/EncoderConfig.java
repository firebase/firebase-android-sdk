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

package com.google.firebase.encoders.config;

import androidx.annotation.NonNull;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ValueEncoder;

/**
 * Implemented by concrete {@link com.google.firebase.encoders.DataEncoder} builders.
 *
 * <p>Used by clients to configure encoders without coupling to a particular encoder format.
 */
public interface EncoderConfig<T extends EncoderConfig<T>> {
  @NonNull
  <U> T registerEncoder(@NonNull Class<U> type, @NonNull ObjectEncoder<? super U> encoder);

  @NonNull
  <U> T registerEncoder(@NonNull Class<U> type, @NonNull ValueEncoder<? super U> encoder);
}
