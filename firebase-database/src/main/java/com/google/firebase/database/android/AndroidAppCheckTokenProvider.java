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

package com.google.firebase.database.android;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.database.core.TokenProvider;
import com.google.firebase.inject.Deferred;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class AndroidAppCheckTokenProvider implements TokenProvider {
  private final Deferred<InteropAppCheckTokenProvider> deferredAppCheckProvider;
  private final AtomicReference<InteropAppCheckTokenProvider> internalAppCheck;

  public AndroidAppCheckTokenProvider(
      Deferred<InteropAppCheckTokenProvider> deferredAppCheckProvider) {
    this.deferredAppCheckProvider = deferredAppCheckProvider;
    this.internalAppCheck = new AtomicReference<>();

    deferredAppCheckProvider.whenAvailable(
        authProvider -> internalAppCheck.set(authProvider.get()));
  }

  // TODO(b/261014172): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  @Override
  public void getToken(boolean forceRefresh, @NonNull final GetTokenCompletionListener listener) {
    InteropAppCheckTokenProvider appCheckProvider = internalAppCheck.get();

    if (appCheckProvider != null) {
      Task<AppCheckTokenResult> getTokenResult = appCheckProvider.getToken(forceRefresh);

      getTokenResult
          .addOnSuccessListener(result -> listener.onSuccess(result.getToken()))
          .addOnFailureListener(e -> listener.onError(e.getMessage()));
    } else {
      listener.onSuccess(null);
    }
  }

  @Override
  public void addTokenChangeListener(
      final ExecutorService executorService, final TokenChangeListener tokenListener) {
    deferredAppCheckProvider.whenAvailable(
        provider ->
            provider
                .get()
                .addAppCheckTokenListener(
                    tokenResult ->
                        executorService.execute(
                            () -> tokenListener.onTokenChange(tokenResult.getToken()))));
  }

  @Override
  public void removeTokenChangeListener(TokenChangeListener tokenListener) {
    // TODO Implement removeIdTokenListener.
  }
}
