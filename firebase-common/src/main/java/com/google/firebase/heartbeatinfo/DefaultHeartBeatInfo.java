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
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.internal.HeartBeatInfoStorage;
import org.jetbrains.annotations.NotNull;

public class DefaultHeartBeatInfo implements HeartBeatInfo {

  private static final String GLOBAL = "fire-global";
  private static HeartBeatInfoStorage storage;

  private DefaultHeartBeatInfo(Context context) {
    HeartBeatInfoStorage.initialize(context);
    storage = HeartBeatInfoStorage.getInstance();
  }

  private boolean shouldSendSdkHeartBeat(String sdkName, long millis) {
    if (storage.sharedPreferences.contains(sdkName)) {
      long timeElapsed = storage.sharedPreferences.getLong(sdkName, -1) - millis;
      if (timeElapsed >= (long) 1000 * 60 * 60 * 24) {
        storage.sharedPreferences.edit().putLong(sdkName, millis).apply();
        return true;
      }
      return false;
    } else {
      storage.sharedPreferences.edit().putLong(sdkName, millis).apply();
      return true;
    }
  }

  private boolean shouldSendGlobalHeartBeat(long millis) {
    if (storage.sharedPreferences.contains(GLOBAL)) {
      long timeElapsed = storage.sharedPreferences.getLong(GLOBAL, -1) - millis;
      if (timeElapsed >= (long) 1000 * 60 * 60 * 24) {
        storage.sharedPreferences.edit().putLong(GLOBAL, millis).apply();
        return true;
      }
      return false;
    } else {
      storage.sharedPreferences.edit().putLong(GLOBAL, millis).apply();
      return true;
    }
  }

  @Override
  public int getHeartBeatCode(@NotNull String sdkName) {
    long presentTime = System.currentTimeMillis();
    if (!shouldSendSdkHeartBeat(sdkName, presentTime)) {
      return 0;
    }
    if (shouldSendGlobalHeartBeat(presentTime)) return 2;
    else return 1;
  }

  public static @NotNull Component<HeartBeatInfo> component() {
    return Component.builder(HeartBeatInfo.class)
        .add(Dependency.required(Context.class))
        .factory(c -> new DefaultHeartBeatInfo(c.get(Context.class)))
        .build();
  }
}
