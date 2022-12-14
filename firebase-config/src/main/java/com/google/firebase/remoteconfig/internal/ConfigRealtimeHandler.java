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

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigRealtimeHandler {

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> listeners;

  @GuardedBy("this")
  private Future<?> realtimeHttpClientTask;

  @GuardedBy("this")
  private int httpRetriesRemaining;

  @GuardedBy("this")
  private long httpRetrySeconds;

  @GuardedBy("this")
  private boolean isRealtimeDisabled;

  private final ConfigFetchHandler configFetchHandler;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final Context context;
  private final String namespace;
  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;
  private final Random random;

  private final int ORIGINAL_RETRIES = 7;

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
    this.random = new Random();
    this.isRealtimeDisabled = false;
  }

  private synchronized void propagateErrors(FirebaseRemoteConfigException exception) {
    for (ConfigUpdateListener listener : listeners) {
      listener.onError(exception);
    }
  }

  private synchronized void resetRetryParameters() {
    httpRetrySeconds = random.nextInt(5) + 1;
    httpRetriesRemaining = ORIGINAL_RETRIES;
  }

  private synchronized int getRetryMultiplier() {
    // Return retry multiplier between range of 5 and 2.
    return random.nextInt(3) + 2;
  }

  private synchronized boolean canCreateRealtimeHttpClientTask() {
    return !listeners.isEmpty() && realtimeHttpClientTask == null && !isRealtimeDisabled;
  }

  private synchronized Runnable createRealtimeHttpClientTask(
      ConfigRealtimeHttpClient configRealtimeHttpClient) {
    return new Runnable() {
      @Override
      public void run() {
        configRealtimeHttpClient.beginRealtimeHttpStream();
      }
    };
  }

  /** Retries HTTP stream connection asyncly in random time intervals. */
  @SuppressLint("VisibleForTests")
  public synchronized void retryHTTPConnection() {
    if (httpRetriesRemaining > 0) {
      if (httpRetriesRemaining < ORIGINAL_RETRIES) {
        httpRetrySeconds *= getRetryMultiplier();
      }
      httpRetriesRemaining--;
      ConfigRealtimeHttpClient realtimeHttpClient =
          new ConfigRealtimeHttpClient(
              firebaseApp,
              firebaseInstallations,
              configFetchHandler,
              context,
              namespace,
              listeners,
              scheduledExecutorService);
      scheduledExecutorService.schedule(
          new RealtimeHttpClientFutureTask(
              createRealtimeHttpClientTask(realtimeHttpClient), realtimeHttpClient),
          httpRetrySeconds,
          TimeUnit.SECONDS);
    } else {
      propagateErrors(
          new FirebaseRemoteConfigClientException(
              "Unable to connect to the server. Check your connection and try again.",
              FirebaseRemoteConfigException.Code.CONFIG_UPDATE_STREAM_ERROR));
    }
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
          this.scheduledExecutorService.submit(
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
      realtimeHttpClientTask = null;
      isRealtimeDisabled = configRealtimeHttpClient.getRealtimeDisabledState();
      if (configRealtimeHttpClient.getWasLastAttemptSuccessful()) {
        resetRetryParameters();
      }
      if (configRealtimeHttpClient.canRetryHttpConnection()) {
        retryHTTPConnection();
      }
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
