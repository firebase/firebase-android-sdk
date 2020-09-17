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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Provides information as whether to send heart beat or not. */
public class DefaultHeartBeatInfo implements HeartBeatInfo {

  private final HeartBeatInfoStorage storage;

  private Set<HeartBeatConsumer> consumers;

  private Executor backgroundExecutor;

  private static final ThreadFactory THREAD_FACTORY =
      r -> new Thread(r, "heartbeat-information-executor");

  private DefaultHeartBeatInfo(Context context, Set<HeartBeatConsumer> consumers) {
    storage = HeartBeatInfoStorage.getInstance(context);
    this.consumers = consumers;
    this.backgroundExecutor =
        new ThreadPoolExecutor(
            0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), THREAD_FACTORY);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  DefaultHeartBeatInfo(
      HeartBeatInfoStorage testStorage,
      Set<HeartBeatConsumer> consumers,
      ExecutorService executor) {
    storage = testStorage;
    this.consumers = consumers;
    this.backgroundExecutor = executor;
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
  public Task<List<HeartBeatResult>> getAndClearStoredHeartBeatInfo() {
    return Tasks.call(
        backgroundExecutor,
        () -> {
          ArrayList<HeartBeatResult> heartBeatResults = new ArrayList<>();
          boolean shouldSendGlobalHeartBeat = false;
          List<SdkHeartBeatResult> sdkHeartBeatResults = storage.getStoredHeartBeats(true);
          long lastGlobalHeartBeat = storage.getLastGlobalHeartBeat();
          HeartBeat heartBeat;
          for (SdkHeartBeatResult sdkHeartBeatResult : sdkHeartBeatResults) {
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
        });
  }

  @Override
  public Task storeHeartBeatInfo(@NonNull String heartBeatTag) {
    if (consumers.size() <= 0) {
      return Tasks.forResult(true);
    }
    return Tasks.call(
        backgroundExecutor,
        () -> {
          long presentTime = System.currentTimeMillis();
          boolean shouldSendSdkHB = storage.shouldSendSdkHeartBeat(heartBeatTag, presentTime);
          if (shouldSendSdkHB) {
            backgroundExecutor.execute(
                () -> {
                  storage.storeHeartBeatInformation(heartBeatTag, presentTime);
                });
          }
          return true;
        });
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
