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

  /**
   * The {@link Provider} that gives access to the {@link InternalAppCheckTokenProvider} instance;
   * initially, its {@link Provider#get} method returns {@code null}, but will be changed to a new
   * {@link Provider} once the "AppCheck" module becomes available.
   */
  @Nullable private InternalAppCheckTokenProvider internalAppCheckTokenProvider;

  /** The AppCheck token string. May be null if the token has not been retrieved yet. */
  @Nullable private String appCheckToken;

  /** Creates a new FirebaseAppCheckTokenProvider. */
  public FirebaseAppCheckTokenProvider(
      Deferred<InternalAppCheckTokenProvider> deferredAppCheckTokenProvider) {
    deferredAppCheckTokenProvider.whenAvailable(
        provider -> {
          internalAppCheckTokenProvider = provider.get();
          appCheckToken = getCurrentAppCheckToken();
          try {
            Task<AppCheckTokenResult> pendingResult =
                internalAppCheckTokenProvider.getToken(/* forceRefresh= */ false);
            AppCheckTokenResult result =
                Tasks.await(pendingResult, MAXIMUM_TOKEN_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
            if (result.getError() != null) {
              Logger.warn(
                  LOG_TAG,
                  "Error getting App Check token; using placeholder token instead. Error: "
                      + result.getError());
            }
            appCheckToken = result.getToken();
          } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Logger.warn(LOG_TAG, "Unexpected error getting App Check token: " + e);
          }
        });
  }

  @Nullable
  public String getCurrentAppCheckToken() {
    return appCheckToken;
  }
}
