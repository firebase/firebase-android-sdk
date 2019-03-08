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

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;

/** Transforms a value into a server-generated timestamp. */
public class ServerTimestampOperation implements TransformOperation {
  private ServerTimestampOperation() {}

  private static final ServerTimestampOperation SHARED_INSTANCE = new ServerTimestampOperation();

  public static ServerTimestampOperation getInstance() {
    return SHARED_INSTANCE;
  }

  @Override
  public FieldValue applyToLocalView(FieldValue previousValue, Timestamp localWriteTime) {
    return new ServerTimestampValue(localWriteTime, previousValue);
  }

  @Override
  public FieldValue applyToRemoteDocument(FieldValue previousValue, FieldValue transformResult) {
    return transformResult;
  }

  @Override
  public boolean isIdempotent() {
    return true;
  }

  // NOTE: Since we've guaranteed a singleton instance, we can rely on Object's default
  // implementation of equals() / hashCode().
}
