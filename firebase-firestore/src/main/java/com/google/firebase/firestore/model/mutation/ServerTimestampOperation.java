// Copyright 2018 Google LLC
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

package com.google.firebase.firestore.model.mutation;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.ServerTimestamps;
import com.google.firestore.v1.Value;

/** Transforms a value into a server-generated timestamp. */
public class ServerTimestampOperation implements TransformOperation {
  private ServerTimestampOperation() {}

  private static final ServerTimestampOperation SHARED_INSTANCE = new ServerTimestampOperation();

  public static ServerTimestampOperation getInstance() {
    return SHARED_INSTANCE;
  }

  @Override
  public Value applyToLocalView(@Nullable Value previousValue, Timestamp localWriteTime) {
    return ServerTimestamps.valueOf(localWriteTime, previousValue);
  }

  @Override
  public Value applyToRemoteDocument(@Nullable Value previousValue, Value transformResult) {
    return transformResult;
  }

  @Nullable
  @Override
  public Value computeBaseValue(@Nullable Value currentValue) {
    return null; // Server timestamps are idempotent and don't require a base value.
  }

  // NOTE: Since we've guaranteed a singleton instance, we can rely on Object's default
  // implementation of equals() / hashCode().
}
