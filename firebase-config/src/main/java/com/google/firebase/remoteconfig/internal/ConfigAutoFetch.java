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
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigServerException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

public class ConfigAutoFetch {

  private static final int MAXIMUM_FETCH_ATTEMPTS = 3;
  private static final String TEMPLATE_VERSION_KEY = "latestTemplateVersionNumber";
  private static final String REALTIME_DISABLED_KEY = "featureDisabled";

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> eventListeners;

  private final HttpURLConnection httpURLConnection;

  private final ConfigFetchHandler configFetchHandler;
  private final ConfigUpdateListener retryCallback;
  private final ScheduledExecutorService scheduledExecutorService;
  private final Random random;

  public ConfigAutoFetch(
      HttpURLConnection httpURLConnection,
      ConfigFetchHandler configFetchHandler,
      Set<ConfigUpdateListener> eventListeners,
      ConfigUpdateListener retryCallback,
      ScheduledExecutorService scheduledExecutorService) {
    this.httpURLConnection = httpURLConnection;
    this.configFetchHandler = configFetchHandler;
    this.eventListeners = eventListeners;
    this.retryCallback = retryCallback;
    this.scheduledExecutorService = scheduledExecutorService;
    this.random = new Random();
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

  private String parseAndValidateConfigUpdateMessage(String message) {
    int left = 0;
    while (left < message.length() && message.charAt(left) != '{') {
      left++;
    }
    int right = message.length() - 1;
    while (right >= 0 && message.charAt(right) != '}') {
      right--;
    }

    return left >= right ? "" : message.substring(left, right + 1);
  }

  // Check connection and establish InputStream
  @VisibleForTesting
  public void listenForNotifications() {
    if (httpURLConnection != null) {
      try {
        InputStream inputStream = httpURLConnection.getInputStream();
        handleNotifications(inputStream);
        inputStream.close();
      } catch (IOException ex) {
        propagateErrors(
            new FirebaseRemoteConfigClientException(
                "Unable to parse config update message.",
                ex.getCause(),
                FirebaseRemoteConfigException.Code.CONFIG_UPDATE_MESSAGE_INVALID));
      } finally {
        httpURLConnection.disconnect();
      }
    }

    retryCallback.onEvent();
    scheduledExecutorService.shutdownNow();
    try {
      scheduledExecutorService.awaitTermination(3L, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Log.d(TAG, "Thread Interrupted.");
    }
  }

  // Auto-fetch new config and execute callbacks on each new message
  private void handleNotifications(InputStream inputStream) throws IOException {
    BufferedReader reader = new BufferedReader((new InputStreamReader(inputStream, "utf-8")));
    String partialConfigUpdateMessage;
    String currentConfigUpdateMessage = "";

    // Multiple config update messages can be sent through this loop. Each message comes in line by
    // line as partialConfigUpdateMessage and are accumulated together into
    // currentConfigUpdateMessage.
    while ((partialConfigUpdateMessage = reader.readLine()) != null) {
      // Accumulate all the partial parts of the message until we have a full message.
      currentConfigUpdateMessage += partialConfigUpdateMessage;

      // Closing bracket indicates a full message has just finished.
      if (partialConfigUpdateMessage.contains("}")) {
        // Strip beginning and ending of message. If there is not an open and closing bracket,
        // parseMessage will return an empty message.
        currentConfigUpdateMessage =
            parseAndValidateConfigUpdateMessage(currentConfigUpdateMessage);
        if (!currentConfigUpdateMessage.isEmpty()) {
          try {
            JSONObject jsonObject = new JSONObject(currentConfigUpdateMessage);

            if (jsonObject.has(REALTIME_DISABLED_KEY)
                && jsonObject.getBoolean(REALTIME_DISABLED_KEY)) {
              retryCallback.onError(
                  new FirebaseRemoteConfigServerException(
                      "The server is temporarily unavailable. Try again in a few minutes.",
                      FirebaseRemoteConfigException.Code.CONFIG_UPDATE_UNAVAILABLE));
              break;
            }
            if (jsonObject.has(TEMPLATE_VERSION_KEY)) {
              long oldTemplateVersion = configFetchHandler.getTemplateVersionNumber();
              long targetTemplateVersion = jsonObject.getLong(TEMPLATE_VERSION_KEY);
              if (targetTemplateVersion > oldTemplateVersion) {
                autoFetch(MAXIMUM_FETCH_ATTEMPTS, targetTemplateVersion);
              }
            }
          } catch (JSONException ex) {
            Log.e(TAG, "Unable to parse latest config update message." + ex.toString());
          }

          currentConfigUpdateMessage = "";
        }
      }
    }

    reader.close();
    inputStream.close();
  }

  private void autoFetch(int remainingAttempts, long targetVersion) {
    if (remainingAttempts == 0) {
      propagateErrors(
          new FirebaseRemoteConfigServerException(
              "Unable to fetch the latest version of the template.",
              FirebaseRemoteConfigException.Code.CONFIG_UPDATE_NOT_FETCHED));
      return;
    }

    // Needs fetch to occur between 0 - 4 seconds. Randomize to not cause ddos alerts in backend
    int timeTillFetch = random.nextInt(4);
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
    int currentAttempts = remainingAttempts - 1;

    // fetchAttemptNumber is calculated by subtracting current attempts from the max number of
    // possible attempts.
    Task<ConfigFetchHandler.FetchResponse> fetchTask =
        configFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, MAXIMUM_FETCH_ATTEMPTS - currentAttempts);
    fetchTask.onSuccessTask(
        (fetchResponse) -> {
          long newTemplateVersion = 0;
          if (fetchResponse.getFetchedConfigs() != null) {
            newTemplateVersion = fetchResponse.getFetchedConfigs().getTemplateVersionNumber();
          } else if (fetchResponse.getStatus()
              == ConfigFetchHandler.FetchResponse.Status.BACKEND_HAS_NO_UPDATES) {
            newTemplateVersion = targetVersion;
          }

          if (newTemplateVersion >= targetVersion) {
            executeAllListenerCallbacks();
          } else {
            Log.d(
                TAG,
                "Fetched template version is the same as SDK's current version."
                    + " Retrying fetch.");
            // Continue fetching until template version number if greater then current.
            autoFetch(currentAttempts, targetVersion);
          }
          return Tasks.forResult(null);
        });
  }
}
