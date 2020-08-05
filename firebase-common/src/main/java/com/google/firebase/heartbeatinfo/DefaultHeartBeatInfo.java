// Copyright 2019 Google LLC
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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import java.util.ArrayList;
import java.util.List;

/** Provides information as whether to send heart beat or not. */
public class DefaultHeartBeatInfo implements HeartBeatInfo {

  private HeartBeatInfoStorage storage;

  private DefaultHeartBeatInfo(Context context) {
    storage = HeartBeatInfoStorage.getInstance(context);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  DefaultHeartBeatInfo(HeartBeatInfoStorage testStorage) {
    storage = testStorage;
  }

  @Override
  public @NonNull HeartBeat getHeartBeatCode(@NonNull String heartBeatTag) {
    long presentTime = System.currentTimeMillis();
    boolean shouldSendSdkHB = storage.shouldSendSdkHeartBeat(heartBeatTag, presentTime, true);
    boolean shouldSendGlobalHB = storage.shouldSendGlobalHeartBeat(presentTime, true);
    if (shouldSendSdkHB && shouldSendGlobalHB) {
      return HeartBeat.COMBINED;
    } else if (shouldSendGlobalHB) {
      return HeartBeat.GLOBAL;
    } else if (shouldSendSdkHB) {
      return HeartBeat.SDK;
    }
    return HeartBeat.NONE;
  }

  @Override
  public List<HeartBeatResult> getAndClearStoredHeartBeatInfo() {
    List<SdkHeartBeatResult> sdkHeartBeatResults = storage.getStoredHeartBeats(true);
    ArrayList<HeartBeatResult> heartBeatResults = new ArrayList<>();
    long lastGlobalHeartBeat = storage.getLastGlobalHeartBeat();
    long timeElapsed = 0;
    boolean shouldSendGlobalHeartBeat = false;
    for (int i = 0; i < sdkHeartBeatResults.size(); i++) {
      SdkHeartBeatResult sdkHeartBeatResult = sdkHeartBeatResults.get(i);
      HeartBeat heartBeat = HeartBeat.NONE;
      timeElapsed = sdkHeartBeatResult.getMillis() - lastGlobalHeartBeat;
      shouldSendGlobalHeartBeat = (timeElapsed >= (long) 1000 * 60 * 60 * 24);
      if (shouldSendGlobalHeartBeat && sdkHeartBeatResult.getShouldSendSdkHeartBeat()) {
        heartBeat = HeartBeat.COMBINED;
      } else if (shouldSendGlobalHeartBeat && !sdkHeartBeatResult.getShouldSendSdkHeartBeat()) {
        heartBeat = HeartBeat.GLOBAL;
      } else if (sdkHeartBeatResult.getShouldSendSdkHeartBeat()) {
        heartBeat = HeartBeat.SDK;
      }
      if (shouldSendGlobalHeartBeat) {
        lastGlobalHeartBeat = sdkHeartBeatResult.getMillis();
      }
      heartBeatResults.add(
          HeartBeatResult.create(
              sdkHeartBeatResult.getSdkName(), sdkHeartBeatResult.getMillis(), heartBeat));
    }
    if (lastGlobalHeartBeat > 0) {
      storage.updateGlobalHeartBeat(lastGlobalHeartBeat);
    }
    return heartBeatResults;
  }

  @Override
  public void storeHeartBeatInfo(@NonNull String heartBeatTag) {
    long presentTime = System.currentTimeMillis();
    boolean shouldSendSdkHB = storage.shouldSendSdkHeartBeat(heartBeatTag, presentTime, true);
    if (shouldSendSdkHB) {
      storage.storeHeartBeatInformation(heartBeatTag, presentTime, shouldSendSdkHB);
    }
  }

  public static @NonNull Component<HeartBeatInfo> component() {
    return Component.builder(HeartBeatInfo.class)
        .add(Dependency.required(Context.class))
        .factory(c -> new DefaultHeartBeatInfo(c.get(Context.class)))
        .build();
  }
}
