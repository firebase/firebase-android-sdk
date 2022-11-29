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
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;

public class ConfigRealtimeHandler {

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> listeners;

  @GuardedBy("this")
  private Future<?> realtimeHttpClientTask;

  private final ConfigFetchHandler configFetchHandler;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final Context context;
  private final String namespace;
  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;

  public ConfigRealtimeHandler(
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      ConfigFetchHandler configFetchHandler,
      Context context,
      String namespace,
      ExecutorService executorService,
      ScheduledExecutorService scheduledExecutorService) {

    this.listeners = new LinkedHashSet<>();
    this.realtimeHttpClientTask = null;

    this.firebaseApp = firebaseApp;
    this.configFetchHandler = configFetchHandler;
    this.firebaseInstallations = firebaseInstallations;
    this.context = context;
    this.namespace = namespace;
    this.executorService = executorService;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  private synchronized boolean canCreateRealtimeHttpClientTask() {
    return !listeners.isEmpty() && realtimeHttpClientTask == null;
  }

  private synchronized Runnable createRealtimeHttpClientTask(
      ConfigRealtimeHttpClient configRealtimeHttpClient) {
    return new Runnable() {
      @Override
      public void run() {
        configRealtimeHttpClient.beginRealtimeHttpStream();
        boolean isRealtimeClientRunning = true;
        while (isRealtimeClientRunning) {
          if (Thread.currentThread().isInterrupted()) {
            isRealtimeClientRunning = false;
          }
        }
      }
    };
  }

  // Kicks off Http stream listening and autofetch
  private synchronized void beginRealtime() {
    if (canCreateRealtimeHttpClientTask()) {
      ConfigRealtimeHttpClient realtimeHttpClient =
          new ConfigRealtimeHttpClient(
              firebaseApp,
              firebaseInstallations,
              configFetchHandler,
              context,
              namespace,
              listeners,
              scheduledExecutorService);
      this.realtimeHttpClientTask =
          this.executorService.submit(
              new RealtimeHttpClientFutureTask(
                  createRealtimeHttpClientTask(realtimeHttpClient), realtimeHttpClient));
    }
  }

  // Pauses Http stream listening
  public synchronized void pauseRealtime() {
    if (realtimeHttpClientTask != null && !realtimeHttpClientTask.isCancelled()) {
      realtimeHttpClientTask.cancel(true);
      realtimeHttpClientTask = null;
    }
  }

  @NonNull
  public synchronized ConfigUpdateListenerRegistration addRealtimeConfigUpdateListener(
      @NonNull ConfigUpdateListener configUpdateListener) {
    listeners.add(configUpdateListener);
    if (configUpdateListener.getClass() != EmptyConfigUpdateListener.class
        || listeners.size() > 1) {
      beginRealtime();
    }
    return new ConfigUpdateListenerRegistrationInternal(configUpdateListener);
  }

  private synchronized void removeRealtimeConfigUpdateListener(ConfigUpdateListener listener) {
    listeners.remove(listener);
    if (listeners.isEmpty()) {
      pauseRealtime();
    }
  }

  private class RealtimeHttpClientFutureTask extends FutureTask<String> {
    private final ConfigRealtimeHttpClient configRealtimeHttpClient;

    public RealtimeHttpClientFutureTask(
        Runnable runnable, ConfigRealtimeHttpClient configRealtimeHttpClient) {
      super(runnable, null);
      this.configRealtimeHttpClient = configRealtimeHttpClient;
    }

    @Override
    protected void done() {
      this.configRealtimeHttpClient.stopRealtime();
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
