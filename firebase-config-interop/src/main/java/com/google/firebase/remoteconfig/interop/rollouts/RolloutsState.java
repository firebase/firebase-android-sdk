/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.remoteconfig.interop.rollouts;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import java.util.Set;

/** Model representing the state of all rollouts assigned to an app instance at a point in time. */
@AutoValue
public abstract class RolloutsState {
  /** Rollouts assigned at this point in time. May include zero or more assignments. */
  @NonNull
  public abstract Set<RolloutAssignment> getRolloutAssignments();

  @NonNull
  public static RolloutsState create(@NonNull Set<RolloutAssignment> rolloutAssignments) {
    return new AutoValue_RolloutsState(rolloutAssignments);
  }
}
