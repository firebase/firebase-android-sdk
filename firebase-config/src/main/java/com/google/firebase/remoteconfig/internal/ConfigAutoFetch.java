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

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigRealtimeUpdateFetchException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigRealtimeUpdateStreamException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

public class ConfigAutoFetch {

  private static final int FETCH_RETRY = 3;

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> eventListeners;

  private final HttpURLConnection httpURLConnection;

  private final ConfigFetchHandler configFetchHandler;
  private final ConfigUpdateListener retryCallback;
  private final ScheduledExecutorService scheduledExecutorService;
  private final Random random;
  private final Executor executor;

  public ConfigAutoFetch(
      HttpURLConnection httpURLConnection,
      ConfigFetchHandler configFetchHandler,
      Set<ConfigUpdateListener> eventListeners,
      ConfigUpdateListener retryCallback,
      Executor executor) {
    this.httpURLConnection = httpURLConnection;
    this.configFetchHandler = configFetchHandler;
    this.eventListeners = eventListeners;
    this.retryCallback = retryCallback;
    this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    ;
    this.random = new Random();
    this.executor = executor;
  }

  public void beginAutoFetch() {
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            listenForNotifications();
          }
        });
  }

  private synchronized void propagateErrors(FirebaseRemoteConfigException exception) {
    for (ConfigUpdateListener listener : eventListeners) {
      listener.onError(exception);
    }
  }

  private synchronized void executeAllListenerCallbacks() {
    for (ConfigUpdateListener listener : eventListeners) {
      listener.onEvent();
    }
  }

  // Check connection and establish InputStream
  @VisibleForTesting
  public void listenForNotifications() {
    if (httpURLConnection != null) {
      try {
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode == 200) {
          InputStream inputStream = httpURLConnection.getInputStream();
          handleNotifications(inputStream);
          inputStream.close();
        } else {
          propagateErrors(
              new FirebaseRemoteConfigRealtimeUpdateStreamException(
                  "Http connection responded with error: " + responseCode));
        }
      } catch (IOException ex) {
        propagateErrors(
            new FirebaseRemoteConfigRealtimeUpdateFetchException(
                "Error handling stream messages while fetching.", ex.getCause()));
      }
    }

    httpURLConnection.disconnect();
    scheduledExecutorService.shutdown();
    try {
      scheduledExecutorService.awaitTermination(3L, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Log.i(TAG, "Thread Interuppted");
    }
    retryCallback.onEvent();
  }

  // Auto-fetch new config and execute callbacks on each new message
  private void handleNotifications(InputStream inputStream) throws IOException {
    BufferedReader reader = new BufferedReader((new InputStreamReader(inputStream, "utf-8")));
    String message;
    while ((message = reader.readLine()) != null) {
      if (!message.contains("latestTemplateVersionNumber")) {
        continue;
      }

      long targetTemplateVersion = configFetchHandler.getTemplateVersionNumber();
      try {
        JSONObject jsonObject = new JSONObject("{" + message + "}");
        if (jsonObject.has("latestTemplateVersionNumber")) {
          targetTemplateVersion = jsonObject.getLong("latestTemplateVersionNumber");
        }

      } catch (JSONException ex) {
        Log.i(TAG, "Unable to parse latest config update message." + ex.toString());
      }

      autoFetch(FETCH_RETRY, targetTemplateVersion);
    }

    reader.close();
    inputStream.close();
  }

  private void autoFetch(int remainingAttempts, long targetVersion) {
    if (remainingAttempts == 0) {
      propagateErrors(
          new FirebaseRemoteConfigRealtimeUpdateFetchException("Unable to fetch latest version."));
      return;
    }

    // Needs fetch to occur between 2 - 12 seconds. Randomize to not cause ddos alerts in backend
    int timeTillFetch = random.nextInt(3) + 1;
    scheduledExecutorService.schedule(
        new Runnable() {
          @Override
          public void run() {
            fetchLatestConfig(remainingAttempts, targetVersion);
          }
        },
        timeTillFetch,
        TimeUnit.SECONDS);
  }

  @VisibleForTesting
  public synchronized void fetchLatestConfig(int remainingAttempts, long targetVersion) {
    Task<ConfigFetchHandler.FetchResponse> fetchTask = configFetchHandler.fetch(0L);
    fetchTask.onSuccessTask(
        (fetchResponse) -> {
          long newTemplateVersion = 0;
          if (fetchResponse.getFetchedConfigs() != null) {
            newTemplateVersion = fetchResponse.getFetchedConfigs().getTemplateVersionNumber();
          } else if (fetchResponse.getStatus()
              == ConfigFetchHandler.FetchResponse.Status.BACKEND_HAS_NO_UPDATES) {
            return Tasks.forResult(null);
          }

          if (newTemplateVersion >= targetVersion) {
            executeAllListenerCallbacks();
          } else {
            Log.i(
                TAG,
                "Fetched template version is the same as SDK's current version."
                    + " Retrying fetch.");
            // Continue fetching until template version number if greater then current.
            autoFetch(remainingAttempts - 1, targetVersion);
          }
          return Tasks.forResult(null);
        });
  }
}
