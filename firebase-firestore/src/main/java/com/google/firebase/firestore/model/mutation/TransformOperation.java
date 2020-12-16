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
import com.google.firestore.v1.Value;

/** Used to represent a field transform on a mutation. */
public interface TransformOperation {
  /**
   * Computes the local transform result against the provided previousValue, optionally using the
   * provided localWriteTime.
   */
  Value applyToLocalView(@Nullable Value previousValue, Timestamp localWriteTime);

  /**
   * Computes a final transform result after the transform has been acknowledged by the server,
   * potentially using the server-provided transformResult.
   */
  Value applyToRemoteDocument(@Nullable Value previousValue, Value transformResult);

  /**
   * If applicable, returns the base value to persist for this transform. If a base value is
   * provided, the transform operation is always applied to this base value, even if document has
   * already been updated.
   *
   * <p>Base values provide consistent behavior for non-idempotent transforms and allow us to return
   * the same latency-compensated value even if the backend has already applied the transform
   * operation. The base value is null for idempotent transforms, as they can be re-played even if
   * the backend has already applied them.
   *
   * @return a base value to store along with the mutation, or null for idempotent transforms.
   */
  @Nullable
  Value computeBaseValue(@Nullable Value previousValue);
}
