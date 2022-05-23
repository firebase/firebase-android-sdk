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

import androidx.annotation.GuardedBy;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ConfigAutoFetch {

  private static final Logger logger = Logger.getLogger("Real_Time_RC");
  private static final int FETCH_RETRY = 3;

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> eventListener;

  private final HttpURLConnection httpURLConnection;
  private final ConfigFetchHandler configFetchHandler;
  private final ConfigUpdateListener retryCallback;
  private final ScheduledExecutorService scheduledExecutorService;
  private final Random random;
  private final Executor executor;

  public ConfigAutoFetch(
      HttpURLConnection httpURLConnection,
      ConfigFetchHandler configFetchHandler,
      Set<ConfigUpdateListener> eventListener,
      ConfigUpdateListener retryCallback,
      Executor executor,
      ScheduledExecutorService scheduledExecutorService) {
    this.httpURLConnection = httpURLConnection;
    this.configFetchHandler = configFetchHandler;
    this.eventListener = eventListener;
    this.retryCallback = retryCallback;
    this.scheduledExecutorService = scheduledExecutorService;
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

  // Check connection and establish InputStream
  private void listenForNotifications() {
    if (httpURLConnection != null) {
      try {
        logger.info(httpURLConnection.toString());
        int responseCode = httpURLConnection.getResponseCode();
        logger.info(responseCode + "");
        if (responseCode == 200) {
          InputStream inputStream = httpURLConnection.getInputStream();
          handleNotifications(inputStream);
          inputStream.close();
        } else {
          logger.info("Can't open Realtime stream");
        }
      } catch (IOException ex) {
        logger.info("Error handling messages with exception: " + ex.toString());
      }
    }
    retryCallback.onEvent();
  }

  // Auto-fetch new config and execute callbacks on each new message
  private void handleNotifications(InputStream inputStream) throws IOException {
    BufferedReader reader = new BufferedReader((new InputStreamReader(inputStream, "utf-8")));
    String message;
    while ((message = reader.readLine()) != null) {
      logger.info(message);
      long targetTemplateVersion = configFetchHandler.getTemplateVersionNumber();
      try {
        JSONObject jsonObject = new JSONObject(message);
        if (jsonObject.has("latestTemplateVersionNumber")) {
          targetTemplateVersion = jsonObject.getLong("latestTemplateVersionNumber");
        }
      } catch (JSONException ex) {
        logger.info("Can't get latest config update message.");
      }

      logger.info("Template version is " + targetTemplateVersion);
      autoFetch(FETCH_RETRY, targetTemplateVersion);
    }
    reader.close();
  }

  private synchronized void autoFetch(int remainingAttempts, long targetVersion) {
    if (remainingAttempts == 0) {
      for (ConfigUpdateListener listener : eventListener) {
        listener.onError(new FirebaseRemoteConfigException("Unable to fetch latest version."));
      }

      return;
    }

    if (remainingAttempts == FETCH_RETRY) {
      fetchLatestConfig(remainingAttempts, targetVersion);
    } else {
      // Needs fetch to occur between 2 - 12 seconds. Randomize to not cause ddos alerts in backend
      int timeTillFetch = random.nextInt(11000) + 2000;
      scheduledExecutorService.schedule(
          new Runnable() {
            @Override
            public void run() {
              fetchLatestConfig(remainingAttempts, targetVersion);
            }
          },
          timeTillFetch,
          TimeUnit.MILLISECONDS);
    }
  }

  private synchronized void fetchLatestConfig(int remainingAttempts, long targetVersion) {
    Task<ConfigFetchHandler.FetchResponse> fetchTask = configFetchHandler.fetch(0L);
    fetchTask.onSuccessTask(
        (fetchResponse) -> {
          logger.info("Fetch done...");
          long newTemplateVersion = 0;
          if (fetchResponse.getFetchedConfigs() != null) {
            newTemplateVersion = fetchResponse.getFetchedConfigs().getTemplateVersionNumber();
          }

          if (newTemplateVersion >= targetVersion) {
            for (ConfigUpdateListener listener : eventListener) {
              listener.onEvent();
            }
          } else {
            logger.info(
                "Fetched template version is the same as SDK's current version."
                    + " Retrying fetch.");
            // Continue fetching until template version number if greater then current.
            autoFetch(remainingAttempts - 1, targetVersion);
          }
          return Tasks.forResult(null);
        });
  }
}
