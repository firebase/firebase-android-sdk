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

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsStateSubscriber;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class RolloutsStateSubscriptionsHandler {
  private ConfigCacheClient activatedConfigsCache;
  private RolloutsStateFactory rolloutsStateFactory;
  private Executor executor;

  // Thread-safe implementation for subscribers, using a ConcurrentHashMap as the underlying
  // implementation with set-like accessors.
  private Set<RolloutsStateSubscriber> subscribers =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public RolloutsStateSubscriptionsHandler(
      @NonNull ConfigCacheClient activatedConfigsCache,
      @NonNull RolloutsStateFactory rolloutsStateFactory,
      @NonNull Executor executor) {
    this.activatedConfigsCache = activatedConfigsCache;
    this.rolloutsStateFactory = rolloutsStateFactory;
    this.executor = executor;
  }

  public void registerRolloutsStateSubscriber(@NonNull RolloutsStateSubscriber subscriber) {
    subscribers.add(subscriber);

    Task<ConfigContainer> activatedConfigsCacheTask = activatedConfigsCache.get();

    activatedConfigsCacheTask.addOnSuccessListener(
        executor,
        unused -> {
          try {
            ConfigContainer activatedConfigsCache = activatedConfigsCacheTask.getResult();

            // If we try to read the cache before the Remote Config component has started up,
            // the cache will return null. We don't need to publish any rollouts since none are
            // assigned yet.
            if (activatedConfigsCache != null) {
              RolloutsState rolloutsState =
                  rolloutsStateFactory.getActiveRolloutsState(activatedConfigsCache);
              executor.execute(() -> subscriber.onRolloutsStateChanged(rolloutsState));
            }
          } catch (FirebaseRemoteConfigException e) {
            Log.w(
                TAG,
                "Exception publishing RolloutsState to subscriber. Continuing to listen for changes.",
                e);
          }
        });
  }

  public void publishActiveRolloutsState(@NonNull ConfigContainer configContainer) {
    try {
      RolloutsState activeRolloutsState =
          rolloutsStateFactory.getActiveRolloutsState(configContainer);

      for (RolloutsStateSubscriber subscriber : subscribers) {
        executor.execute(() -> subscriber.onRolloutsStateChanged(activeRolloutsState));
      }
    } catch (FirebaseRemoteConfigException e) {
      Log.w(
          TAG,
          "Exception publishing RolloutsState to subscribers. Continuing to listen for changes.",
          e);
    }
  }
}
