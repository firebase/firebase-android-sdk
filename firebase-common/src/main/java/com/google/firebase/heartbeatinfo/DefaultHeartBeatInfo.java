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
import java.util.Set;

/** Provides information as whether to send heart beat or not. */
public class DefaultHeartBeatInfo implements HeartBeatInfo {

  private HeartBeatInfoStorage storage;

  private Set<HeartBeatConsumer> consumers;

  private DefaultHeartBeatInfo(Context context, Set<HeartBeatConsumer> consumers) {
    storage = HeartBeatInfoStorage.getInstance(context);
    this.consumers = consumers;
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  DefaultHeartBeatInfo(HeartBeatInfoStorage testStorage, Set<HeartBeatConsumer> consumers) {
    storage = testStorage;
    this.consumers = consumers;
  }

  @Override
  public @NonNull HeartBeat getHeartBeatCode(@NonNull String heartBeatTag) {
    long presentTime = System.currentTimeMillis();
    boolean shouldSendSdkHB = storage.shouldSendSdkHeartBeat(heartBeatTag, presentTime);
    boolean shouldSendGlobalHB = storage.shouldSendGlobalHeartBeat(presentTime);
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
    boolean shouldSendGlobalHeartBeat = false;
    for (int i = 0; i < sdkHeartBeatResults.size(); i++) {
      SdkHeartBeatResult sdkHeartBeatResult = sdkHeartBeatResults.get(i);
      HeartBeat heartBeat;
      shouldSendGlobalHeartBeat =
          storage.isValidHeartBeat(lastGlobalHeartBeat, sdkHeartBeatResult.getMillis());
      if (shouldSendGlobalHeartBeat) {
        heartBeat = HeartBeat.COMBINED;
      } else {
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
    if (consumers.size() <= 0) return;
    long presentTime = System.currentTimeMillis();
    boolean shouldSendSdkHB = storage.shouldSendSdkHeartBeat(heartBeatTag, presentTime);
    if (shouldSendSdkHB) {
      storage.storeHeartBeatInformation(heartBeatTag, presentTime);
    }
  }

  public static @NonNull Component<HeartBeatInfo> component() {
    return Component.builder(HeartBeatInfo.class)
        .add(Dependency.required(Context.class))
        .add(Dependency.setOf(HeartBeatConsumer.class))
        .factory(
            c -> new DefaultHeartBeatInfo(c.get(Context.class), c.setOf(HeartBeatConsumer.class)))
        .build();
  }
}
