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

/** A transform within a TransformMutation. */
public interface TransformOperation {
  /**
   * Computes the local transform result against the provided previousValue, optionally using the
   * provided localWriteTime.
   */
  FieldValue applyToLocalView(FieldValue previousValue, Timestamp localWriteTime);

  /**
   * Computes a final transform result after the transform has been acknowledged by the server,
   * potentially using the server-provided transformResult.
   */
  FieldValue applyToRemoteDocument(FieldValue previousValue, FieldValue transformResult);

  /** Returns whether this field transform is idempotent. */
  boolean isIdempotent();
}
