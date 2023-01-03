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
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigRealtimeHttpClient {
  @GuardedBy("this")
  private final Set<ConfigUpdateListener> listeners;

  @GuardedBy("this")
  private int httpRetriesRemaining;

  @GuardedBy("this")
  private long httpRetrySeconds;

  @GuardedBy("this")
  private boolean isRealtimeDisabled;

  @GuardedBy("this")
  private Future<?> realtimeHttpStreamFutureTask;

  private final int ORIGINAL_RETRIES = 7;

  private final ScheduledExecutorService scheduledExecutorService;
  private final ConfigFetchHandler configFetchHandler;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final Context context;
  private final String namespace;
  private final Random random;
  private final ConfigCacheClient activatedCacheClient;

  public ConfigRealtimeHttpClient(
      ConfigCacheClient activatedCacheClient,
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      ConfigFetchHandler configFetchHandler,
      Context context,
      String namespace,
      Set<ConfigUpdateListener> listeners,
      ScheduledExecutorService scheduledExecutorService) {

    this.listeners = listeners;
    this.scheduledExecutorService = scheduledExecutorService;

    // Retry parameters
    this.random = new Random();
    resetRetryParameters();

    this.activatedCacheClient = activatedCacheClient;
    this.firebaseApp = firebaseApp;
    this.configFetchHandler = configFetchHandler;
    this.firebaseInstallations = firebaseInstallations;
    this.context = context;
    this.namespace = namespace;
    this.isRealtimeDisabled = false;
    this.realtimeHttpStreamFutureTask = null;
  }

  private synchronized void propagateErrors(FirebaseRemoteConfigException exception) {
    for (ConfigUpdateListener listener : listeners) {
      listener.onError(exception);
    }
  }

  private synchronized int getRetryMultiplier() {
    // Return retry multiplier between range of 5 and 2.
    return random.nextInt(3) + 2;
  }

  private synchronized void resetRetryParameters() {
    httpRetrySeconds = random.nextInt(5) + 1;
    httpRetriesRemaining = ORIGINAL_RETRIES;
  }

  private synchronized boolean canMakeHttpStreamConnection() {
    return !listeners.isEmpty() && realtimeHttpStreamFutureTask == null && !isRealtimeDisabled;
  }

  /** Retries HTTP stream connection asyncly in random time intervals. */
  private synchronized void retryHTTPConnection() {
    if (canMakeHttpStreamConnection() && httpRetriesRemaining > 0) {
      if (httpRetriesRemaining < ORIGINAL_RETRIES) {
        httpRetrySeconds *= getRetryMultiplier();
      }
      httpRetriesRemaining--;
      realtimeHttpStreamFutureTask =
          scheduledExecutorService.schedule(
              createRealtimeHttpStreamFutureTask(createRealtimeHttpStream()),
              httpRetrySeconds,
              TimeUnit.SECONDS);
    } else {
      propagateErrors(
          new FirebaseRemoteConfigClientException(
              "Unable to connect to the server. Check your connection and try again.",
              FirebaseRemoteConfigException.Code.CONFIG_UPDATE_STREAM_ERROR));
    }
  }

  private ConfigRealtimeHttpStream createRealtimeHttpStream() {
    return new ConfigRealtimeHttpStream(
        activatedCacheClient,
        firebaseApp,
        firebaseInstallations,
        context,
        namespace,
        listeners,
        scheduledExecutorService,
        configFetchHandler);
  }

  @SuppressLint("VisibleForTests")
  public synchronized RealtimeHttpStreamFutureTask createRealtimeHttpStreamFutureTask(
      ConfigRealtimeHttpStream configRealtimeHttpStream) {
    Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            HttpURLConnection httpURLConnection = null;
            try {
              httpURLConnection = configRealtimeHttpStream.createRealtimeConnection();
            } catch (IOException ex) {
              propagateErrors(
                  new FirebaseRemoteConfigClientException(
                      "Unable to connect to the server. Check your connection and try again.",
                      FirebaseRemoteConfigException.Code.CONFIG_UPDATE_STREAM_ERROR));
            }
            if (httpURLConnection != null) {
              configRealtimeHttpStream.beginRealtimeHttpStream(httpURLConnection);
            }
          }
        };

    return new RealtimeHttpStreamFutureTask(runnable, configRealtimeHttpStream);
  }

  @SuppressLint("VisibleForTests")
  public synchronized void startRealtimeHttpStream() {
    if (canMakeHttpStreamConnection()) {
      realtimeHttpStreamFutureTask =
          scheduledExecutorService.submit(
              createRealtimeHttpStreamFutureTask(createRealtimeHttpStream()));
    }
  }

  @SuppressLint("VisibleForTests")
  public synchronized void endRealtimeHttpStream() {
    if (realtimeHttpStreamFutureTask != null) {
      realtimeHttpStreamFutureTask.cancel(true);
      realtimeHttpStreamFutureTask = null;
    }
  }

  @SuppressLint("VisibleForTests")
  public class RealtimeHttpStreamFutureTask extends FutureTask<String> {
    private final ConfigRealtimeHttpStream configRealtimeHttpStream;

    public RealtimeHttpStreamFutureTask(
        Runnable runnable, ConfigRealtimeHttpStream configRealtimeHttpStream) {
      super(runnable, null);
      this.configRealtimeHttpStream = configRealtimeHttpStream;
    }

    @Override
    protected void done() {
      realtimeHttpStreamFutureTask = null;
      isRealtimeDisabled = configRealtimeHttpStream.getBackoffState();
      if (configRealtimeHttpStream.getLastAttemptState()) {
        resetRetryParameters();
      }
      if (configRealtimeHttpStream.getRetryState()) {
        retryHTTPConnection();
      }
    }
  }
}
