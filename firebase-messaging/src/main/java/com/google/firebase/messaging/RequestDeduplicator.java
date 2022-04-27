// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.messaging;

import static com.google.firebase.messaging.FirebaseMessaging.TAG;

import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.collection.ArrayMap;
import com.google.android.gms.tasks.Task;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Deduplicates concurrent requests for FCM tokens into the same request to GmsCore/FCM backend.
 *
 * <p>This is to prevent race conditions caused by multiple requests with the same parameters being
 * executed at the same time.
 */
class RequestDeduplicator {

  interface GetTokenRequest {
    Task<String> start();
  }

  private final Executor executor;

  RequestDeduplicator(Executor executor) {
    this.executor = executor;
  }

  /** Map of currently ongoing getTokenRequests keyed by authorizedEntity (sender ID). */
  @GuardedBy("this")
  private final Map<String, Task<String>> getTokenRequests = new ArrayMap<>();

  synchronized Task<String> getOrStartGetTokenRequest(
      String authorizedEntity, GetTokenRequest request) {
    Task<String> ongoingTask = getTokenRequests.get(authorizedEntity);
    if (ongoingTask != null) {
      // There is already an ongoing request with the same authorizedEntity, use the result of that
      // request.
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Joining ongoing request for: " + authorizedEntity);
      }
      return ongoingTask;
    }

    // No current request for this authorized entity, need to make a new request.
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Making new request for: " + authorizedEntity);
    }
    // Use continueWithTask instead of addOnCompleteListener to ensure the task is removed from the
    // map before any later listeners or continuations are invoked.
    Task<String> newTask =
        request
            .start()
            .continueWithTask(
                executor,
                task -> {
                  synchronized (RequestDeduplicator.this) {
                    getTokenRequests.remove(authorizedEntity);
                  }
                  return task;
                });
    getTokenRequests.put(authorizedEntity, newTask);
    return newTask;
  }
}
