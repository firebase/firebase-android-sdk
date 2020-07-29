// Copyright 2020 Google LLC
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

@AutoValue
public abstract class SdkHeartBeatResult implements Comparable<SdkHeartBeatResult> {
  public abstract String getSdkName();

  public abstract long getMillis();

  public abstract boolean getShouldSendSdkHeartBeat();

  public static SdkHeartBeatResult create(
      String sdkName, long millis, boolean shouldSendSdkHeartBeat) {
    return new AutoValue_SdkHeartBeatResult(sdkName, millis, shouldSendSdkHeartBeat);
  }

  @Override
  public int compareTo(SdkHeartBeatResult sdkHeartBeatResult) {
    return (this.getMillis() < sdkHeartBeatResult.getMillis()) ? -1 : 1;
  }
}
