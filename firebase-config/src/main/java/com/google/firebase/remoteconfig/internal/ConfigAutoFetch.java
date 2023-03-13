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
import com.google.firebase.remoteconfig.ConfigUpdate;
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
  private final ConfigCacheClient activatedCache;
  private final ConfigUpdateListener retryCallback;
  private final ScheduledExecutorService scheduledExecutorService;
  private final Random random;

  public ConfigAutoFetch(
      HttpURLConnection httpURLConnection,
      ConfigFetchHandler configFetchHandler,
      ConfigCacheClient activatedCache,
      Set<ConfigUpdateListener> eventListeners,
      ConfigUpdateListener retryCallback,
      ScheduledExecutorService scheduledExecutorService) {
    this.httpURLConnection = httpURLConnection;
    this.configFetchHandler = configFetchHandler;
    this.activatedCache = activatedCache;
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

  private synchronized void executeAllListenerCallbacks(ConfigUpdate configUpdate) {
    for (ConfigUpdateListener listener : eventListeners) {
      listener.onUpdate(configUpdate);
    }
  }

  private synchronized boolean isEventListenersEmpty() {
    return this.eventListeners.isEmpty();
  }

  private String parseAndValidateConfigUpdateMessage(String message) {
    int left = message.indexOf('{');
    int right = message.lastIndexOf('}');

    if (left < 0 || right < 0) {
      return "";
    }

    return left >= right ? "" : message.substring(left, right + 1);
  }

  // Check connection and establish InputStream
  @VisibleForTesting
  public void listenForNotifications() {
    if (httpURLConnection == null) {
      return;
    }

    try {
      InputStream inputStream = httpURLConnection.getInputStream();
      handleNotifications(inputStream);
      inputStream.close();
    } catch (IOException ex) {
      // Stream was interrupted due to a transient issue and the system will retry the connection.
      Log.d(TAG, "Stream was cancelled due to an exception. Retrying the connection...", ex);
    } finally {
      httpURLConnection.disconnect();
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
        if (currentConfigUpdateMessage.isEmpty()) {
          continue;
        }

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

          // If there's an invalidation message and no listeners, ignore the message and close the
          // realtime stream connection. The next time the realtime connection is opened, the client
          // will receive the same invalidation message and call any registered listeners.
          //
          // In effect, stop listening when the last listener is removed. This works around
          // URLConnection.disconnect() being called by ConfigAutoFetch rather than
          // ConfigRealtimeHttpClient.
          if (this.isEventListenersEmpty()) {
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
          // Message was mangled up and so it was unable to be parsed. User is notified of this
          // because it there could be a new configuration that needs to be fetched.
          propagateErrors(
              new FirebaseRemoteConfigClientException(
                  "Unable to parse config update message.",
                  ex.getCause(),
                  FirebaseRemoteConfigException.Code.CONFIG_UPDATE_MESSAGE_INVALID));
          Log.e(TAG, "Unable to parse latest config update message.", ex);
        }

        currentConfigUpdateMessage = "";
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

    // Randomize fetch to occur between 0 - 4 seconds.
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
  public synchronized Task<Void> fetchLatestConfig(int remainingAttempts, long targetVersion) {
    int remainingAttemptsAfterFetch = remainingAttempts - 1;
    int currentAttemptNumber = MAXIMUM_FETCH_ATTEMPTS - remainingAttemptsAfterFetch;

    Task<ConfigFetchHandler.FetchResponse> fetchTask =
        configFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, currentAttemptNumber);
    Task<ConfigContainer> activatedConfigsTask = activatedCache.get();

    return Tasks.whenAllComplete(fetchTask, activatedConfigsTask)
        .continueWithTask(
            scheduledExecutorService,
            (listOfUnusedCompletedTasks) -> {
              if (!fetchTask.isSuccessful()) {
                return Tasks.forException(
                    new FirebaseRemoteConfigClientException(
                        "Failed to auto-fetch config update.", fetchTask.getException()));
              }

              if (!activatedConfigsTask.isSuccessful()) {
                return Tasks.forException(
                    new FirebaseRemoteConfigClientException(
                        "Failed to get activated config for auto-fetch",
                        activatedConfigsTask.getException()));
              }

              ConfigFetchHandler.FetchResponse fetchResponse = fetchTask.getResult();
              ConfigContainer activatedConfigs = activatedConfigsTask.getResult();

              if (!fetchResponseIsUpToDate(fetchResponse, targetVersion)) {
                Log.d(
                    TAG,
                    "Fetched template version is the same as SDK's current version."
                        + " Retrying fetch.");
                // Continue fetching until template version number is greater than current.
                autoFetch(remainingAttemptsAfterFetch, targetVersion);
                return Tasks.forResult(null);
              }

              if (fetchResponse.getFetchedConfigs() == null) {
                Log.d(TAG, "The fetch succeeded, but the backend had no updates.");
                return Tasks.forResult(null);
              }

              // Activate hasn't been called yet, so use an empty container for comparison.
              // See ConfigCacheClient#get() for details on the async operation.
              if (activatedConfigs == null) {
                activatedConfigs = ConfigContainer.newBuilder().build();
              }

              Set<String> updatedKeys =
                  activatedConfigs.getChangedParams(fetchResponse.getFetchedConfigs());
              if (updatedKeys.isEmpty()) {
                Log.d(TAG, "Config was fetched, but no params changed.");
                return Tasks.forResult(null);
              }

              ConfigUpdate configUpdate = ConfigUpdate.create(updatedKeys);
              executeAllListenerCallbacks(configUpdate);
              return Tasks.forResult(null);
            });
  }

  private static Boolean fetchResponseIsUpToDate(
      ConfigFetchHandler.FetchResponse response, long lastKnownVersion) {
    // If there's a config, make sure its version is >= the last known version.
    if (response.getFetchedConfigs() != null) {
      return response.getFetchedConfigs().getTemplateVersionNumber() >= lastKnownVersion;
    }

    // If there isn't a config, return true if the backend had no update.
    // Else, it returned an out of date config.
    return response.getStatus() == ConfigFetchHandler.FetchResponse.Status.BACKEND_HAS_NO_UPDATES;
  }
}
