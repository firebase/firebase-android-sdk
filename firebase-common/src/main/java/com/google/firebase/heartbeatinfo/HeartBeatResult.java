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

package com.google.firebase.heartbeatinfo;

import com.google.auto.value.AutoValue;
import com.google.firebase.heartbeatinfo.HeartBeatInfo.HeartBeat;

/**
 * Stores the information about when the sdk was used and what kind of heartbeat needs to be sent
 * for the same.
 */
@AutoValue
public abstract class HeartBeatResult {
  public abstract String getSdkName();

  public abstract long getMillis();

  public abstract HeartBeat getHeartBeat();

  public static HeartBeatResult create(
      String sdkName, long millis, HeartBeatInfo.HeartBeat heartBeat) {
    return new AutoValue_HeartBeatResult(sdkName, millis, heartBeat);
  }
}
