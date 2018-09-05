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

package com.google.firebase.firestore.model.value;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.util.Assert;

/** Holds settings that define field value deserialization options. */
public class FieldValueOptions {
  enum ServerTimestampBehavior {
    NONE,
    PREVIOUS,
    ESTIMATE
  }

  private final ServerTimestampBehavior serverTimestampBehavior;
  private final boolean timestampsInSnapshotsEnabled;

  private FieldValueOptions(
      ServerTimestampBehavior serverTimestampBehavior, boolean timestampsInSnapshotsEnabled) {
    this.serverTimestampBehavior = serverTimestampBehavior;
    this.timestampsInSnapshotsEnabled = timestampsInSnapshotsEnabled;
  }

  ServerTimestampBehavior getServerTimestampBehavior() {
    return serverTimestampBehavior;
  }

  boolean areTimestampsInSnapshotsEnabled() {
    return timestampsInSnapshotsEnabled;
  }

  public static FieldValueOptions create(
      DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior,
      boolean timestampsInSnapshotsEnabled) {
    ServerTimestampBehavior internalServerTimestampBehavior;
    switch (serverTimestampBehavior) {
      case ESTIMATE:
        internalServerTimestampBehavior = ServerTimestampBehavior.ESTIMATE;
        break;
      case PREVIOUS:
        internalServerTimestampBehavior = ServerTimestampBehavior.PREVIOUS;
        break;
      case NONE:
        internalServerTimestampBehavior = ServerTimestampBehavior.NONE;
        break;
      default:
        throw Assert.fail(
            "Unexpected case for ServerTimestampBehavior: %s", serverTimestampBehavior.name());
    }

    return new FieldValueOptions(internalServerTimestampBehavior, timestampsInSnapshotsEnabled);
  }
}
