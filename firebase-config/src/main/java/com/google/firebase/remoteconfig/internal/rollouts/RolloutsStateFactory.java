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

package com.google.firebase.remoteconfig.internal.rollouts;

import androidx.annotation.NonNull;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import java.util.HashSet;
import java.util.Set;

public class RolloutsStateFactory {

  RolloutsStateFactory() {}

  // TODO: This is a stub.
  @NonNull
  RolloutsState getActiveRolloutsState(@NonNull ConfigContainer configContainer)
      throws FirebaseRemoteConfigClientException {
    // TODO: Convert configContainer.getRolloutMetadata to RolloutsState with active parameter
    // values.

    Set<RolloutAssignment> rolloutAssignments = new HashSet<>();
    return RolloutsState.create(rolloutAssignments);
  }

  @NonNull
  public static RolloutsStateFactory create() {
    return new RolloutsStateFactory();
  }
}
