// Copyright 2023 Google LLC
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

package com.google.firebase.crashlytics.internal.metadata;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Handles RolloutsState for metadata */
public class RolloutAssignmentList {
  // Sync on list itself rather than the elements, this is because for Rollout
  // we only care about the snapshot of the state for all assignments
  private final List<RolloutAssignment> rolloutsState = new ArrayList<>();
  private final int maxEntries;

  static final String ROLLOUTS_STATE = "rolloutsState";

  // init
  public RolloutAssignmentList(int maxEntries) {
    this.maxEntries = maxEntries;
  }

  public synchronized List<RolloutAssignment> getRolloutAssignmentList() {
    return Collections.unmodifiableList(new ArrayList<RolloutAssignment>(rolloutsState));
  }

  @CanIgnoreReturnValue
  public synchronized boolean updateRolloutAssignmentList(List<RolloutAssignment> newMapList) {
    rolloutsState.clear();
    int nOverLimit = 0;

    if (newMapList.size() > maxEntries) {
      Logger.getLogger()
          .w(
              "Ignored "
                  + nOverLimit
                  + " entries when adding rollout assignments. "
                  + "Maximum allowable: "
                  + maxEntries);
      List<RolloutAssignment> maxAllowedNewMapList = newMapList.subList(0, maxEntries);
      return rolloutsState.addAll(maxAllowedNewMapList);
    }
    return rolloutsState.addAll(newMapList);
  }

  // TODO: Nest assignments as a field in RolloutsState
  public List<CrashlyticsReport.Session.Event.RolloutAssignment> getReportRolloutsState() {
    List<RolloutAssignment> rolloutAssignments = getRolloutAssignmentList();
    List<CrashlyticsReport.Session.Event.RolloutAssignment> rolloutsState =
        new ArrayList<CrashlyticsReport.Session.Event.RolloutAssignment>();

    for (int i = 0; i < rolloutAssignments.size(); i++) {
      rolloutsState.add(rolloutAssignments.get(i).toReportProto());
    }
    return rolloutsState;
  }
}
