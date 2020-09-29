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
import androidx.annotation.VisibleForTesting;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Lazy;
import com.google.firebase.inject.Provider;

/** Provides information as whether to send heart beat or not. */
public class DefaultHeartBeatInfo implements HeartBeatInfo {

  private Provider<HeartBeatInfoStorage> storage;

  private DefaultHeartBeatInfo(Context context) {
    this(new Lazy<>(() -> HeartBeatInfoStorage.getInstance(context)));
  }

  @VisibleForTesting
  DefaultHeartBeatInfo(Provider<HeartBeatInfoStorage> testStorage) {
    storage = testStorage;
  }

  @Override
  public @NonNull HeartBeat getHeartBeatCode(@NonNull String heartBeatTag) {
    long presentTime = System.currentTimeMillis();
    boolean shouldSendSdkHB = storage.get().shouldSendSdkHeartBeat(heartBeatTag, presentTime);
    boolean shouldSendGlobalHB = storage.get().shouldSendGlobalHeartBeat(presentTime);
    if (shouldSendSdkHB && shouldSendGlobalHB) {
      return HeartBeat.COMBINED;
    } else if (shouldSendGlobalHB) {
      return HeartBeat.GLOBAL;
    } else if (shouldSendSdkHB) {
      return HeartBeat.SDK;
    }
    return HeartBeat.NONE;
  }

  public static @NonNull Component<HeartBeatInfo> component() {
    return Component.builder(HeartBeatInfo.class)
        .add(Dependency.required(Context.class))
        .factory(c -> new DefaultHeartBeatInfo(c.get(Context.class)))
        .build();
  }
}
