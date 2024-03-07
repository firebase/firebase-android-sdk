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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

public class ConfigRealtimeHandler {

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> listeners;

  @GuardedBy("this")
  private final ConfigRealtimeHttpClient configRealtimeHttpClient;

  private final ConfigFetchHandler configFetchHandler;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final ConfigCacheClient activatedCacheClient;
  private final Context context;
  private final String namespace;
  private final ConfigMetadataClient metadataClient;
  private final ScheduledExecutorService scheduledExecutorService;

  public ConfigRealtimeHandler(
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      ConfigFetchHandler configFetchHandler,
      ConfigCacheClient activatedCacheClient,
      Context context,
      String namespace,
      ConfigMetadataClient metadataClient,
      ScheduledExecutorService scheduledExecutorService) {

    this.listeners = new LinkedHashSet<>();
    this.configRealtimeHttpClient =
        new ConfigRealtimeHttpClient(
            firebaseApp,
            firebaseInstallations,
            configFetchHandler,
            activatedCacheClient,
            context,
            namespace,
            listeners,
            metadataClient,
            scheduledExecutorService);

    this.firebaseApp = firebaseApp;
    this.configFetchHandler = configFetchHandler;
    this.firebaseInstallations = firebaseInstallations;
    this.activatedCacheClient = activatedCacheClient;
    this.context = context;
    this.namespace = namespace;
    this.metadataClient = metadataClient;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  // Kicks off Http stream listening and autofetch
  private synchronized void beginRealtime() {
    if (!listeners.isEmpty()) {
      configRealtimeHttpClient.startHttpConnection();
    }
  }

  @NonNull
  public synchronized ConfigUpdateListenerRegistration addRealtimeConfigUpdateListener(
      @NonNull ConfigUpdateListener configUpdateListener) {
    listeners.add(configUpdateListener);
    beginRealtime();
    return new ConfigUpdateListenerRegistrationInternal(configUpdateListener);
  }

  public synchronized void setBackgroundState(boolean isInBackground) {
    configRealtimeHttpClient.setRealtimeBackgroundState(isInBackground);
    if (!isInBackground) {
      beginRealtime();
    }
  }

  private synchronized void removeRealtimeConfigUpdateListener(ConfigUpdateListener listener) {
    listeners.remove(listener);
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
}
