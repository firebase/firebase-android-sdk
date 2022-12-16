// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import android.content.Context;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class ConfigRealtimeHandler {

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> listeners;

  private final ConfigFetchHandler configFetchHandler;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final Context context;
  private final String namespace;
  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;
  private final ConfigRealtimeHttpClient configRealtimeHttpClient;

  public ConfigRealtimeHandler(
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      ConfigFetchHandler configFetchHandler,
      Context context,
      String namespace,
      ExecutorService executorService,
      ScheduledExecutorService scheduledExecutorService) {

    this.listeners = new LinkedHashSet<>();

    this.firebaseApp = firebaseApp;
    this.configFetchHandler = configFetchHandler;
    this.firebaseInstallations = firebaseInstallations;
    this.context = context;
    this.namespace = namespace;
    this.executorService = executorService;
    this.scheduledExecutorService = scheduledExecutorService;
    this.configRealtimeHttpClient =
        new ConfigRealtimeHttpClient(
            firebaseApp,
            firebaseInstallations,
            configFetchHandler,
            context,
            namespace,
            listeners,
            scheduledExecutorService);
  }

  public void pauseRealtime() {
    configRealtimeHttpClient.endRealtimeHttpStream();
  }

  @NonNull
  public synchronized ConfigUpdateListenerRegistration addRealtimeConfigUpdateListener(
      @NonNull ConfigUpdateListener configUpdateListener) {
    listeners.add(configUpdateListener);
    if (configUpdateListener.getClass() != EmptyConfigUpdateListener.class
        || listeners.size() > 1) {
      configRealtimeHttpClient.startRealtimeHttpStream();
    }
    return new ConfigUpdateListenerRegistrationInternal(configUpdateListener);
  }

  private synchronized void removeRealtimeConfigUpdateListener(ConfigUpdateListener listener) {
    listeners.remove(listener);
    if (listeners.isEmpty()) {
      configRealtimeHttpClient.endRealtimeHttpStream();
    }
  }

  public class ConfigUpdateListenerRegistrationInternal
      implements ConfigUpdateListenerRegistration {
    private final ConfigUpdateListener listener;

    public ConfigUpdateListenerRegistrationInternal(ConfigUpdateListener listener) {
      this.listener = listener;
    }

    public void remove() {
      removeRealtimeConfigUpdateListener(listener);
    }
  }

  public static class EmptyConfigUpdateListener implements ConfigUpdateListener {

    @Override
    public void onEvent() {}

    @Override
    public void onError(@NonNull FirebaseRemoteConfigException error) {}
  }
}
