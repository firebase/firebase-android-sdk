// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.auth;

import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.inject.Deferred;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** FirebaseAppCheckTokenProvider uses Firebase AppCheck to get an AppCheck token. */
public final class FirebaseAppCheckTokenProvider extends AppCheckTokenProvider {

  private static final String LOG_TAG = "FirebaseAppCheckTokenProvider";

  private static final int MAXIMUM_TOKEN_WAIT_TIME_MS = 30000;

  /** The Task for retrieving the AppCheck token. */
  private final Task<String> getTokenTask;

  /** Creates a new FirebaseAppCheckTokenProvider. */
  public FirebaseAppCheckTokenProvider(
      Deferred<InternalAppCheckTokenProvider> deferredAppCheckTokenProvider) {
    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    getTokenTask = taskCompletionSource.getTask();

    if (deferredAppCheckTokenProvider == null) {
      taskCompletionSource.setResult(null);
      return;
    }

    deferredAppCheckTokenProvider.whenAvailable(
        provider -> {
          InternalAppCheckTokenProvider internalAppCheckTokenProvider = provider.get();
          if (internalAppCheckTokenProvider == null) {
            taskCompletionSource.setResult(null);
            return;
          }
          internalAppCheckTokenProvider
              .getToken(/* forceRefresh= */ false)
              .addOnCompleteListener(
                  task -> {
                    if (task.isSuccessful()) {
                      AppCheckTokenResult result = task.getResult();
                      if (result.getError() != null) {
                        Logger.warn(
                            LOG_TAG,
                            "Error getting App Check token; using placeholder token instead. Error: "
                                + result.getError());
                      }
                      taskCompletionSource.setResult(result.getToken());
                    } else {
                      Logger.warn(
                          LOG_TAG,
                          "Unexpected error getting App Check token: " + task.getException());
                      taskCompletionSource.setResult(null);
                    }
                  });
        });
  }

  @Nullable
  public String getCurrentAppCheckToken() {
    try {
      return Tasks.await(getTokenTask, MAXIMUM_TOKEN_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      Logger.warn(LOG_TAG, "Unexpected error getting App Check token: " + e);
      return null;
    }
  }
}
