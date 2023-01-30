// Copyright 2018 Google LLC
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
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.database.core.TokenProvider;
import com.google.firebase.inject.Deferred;
import com.google.firebase.internal.api.FirebaseNoSignedInUserException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class AndroidAuthTokenProvider implements TokenProvider {
  private final Deferred<InternalAuthProvider> deferredAuthProvider;
  private final AtomicReference<InternalAuthProvider> internalAuth;

  public AndroidAuthTokenProvider(Deferred<InternalAuthProvider> deferredAuthProvider) {
    this.deferredAuthProvider = deferredAuthProvider;
    this.internalAuth = new AtomicReference<>();

    deferredAuthProvider.whenAvailable(authProvider -> internalAuth.set(authProvider.get()));
  }

  // TODO(b/261014172): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  @Override
  public void getToken(boolean forceRefresh, @NonNull final GetTokenCompletionListener listener) {
    InternalAuthProvider authProvider = internalAuth.get();

    if (authProvider != null) {
      Task<GetTokenResult> getTokenResult = authProvider.getAccessToken(forceRefresh);

      getTokenResult
          .addOnSuccessListener(result -> listener.onSuccess(result.getToken()))
          .addOnFailureListener(
              e -> {
                if (isUnauthenticatedUsage(e)) {
                  listener.onSuccess(null);
                } else {
                  // TODO: Figure out how to plumb errors through in a sane way.
                  listener.onError(e.getMessage());
                }
              });
    } else {
      listener.onSuccess(null);
    }
  }

  @Override
  public void addTokenChangeListener(
      final ExecutorService executorService, final TokenChangeListener tokenListener) {
    deferredAuthProvider.whenAvailable(
        provider ->
            provider
                .get()
                .addIdTokenListener(
                    tokenResult ->
                        executorService.execute(
                            () -> tokenListener.onTokenChange(tokenResult.getToken()))));
  }

  @Override
  public void removeTokenChangeListener(TokenChangeListener tokenListener) {
    // TODO Implement removeIdTokenListener.
  }

  private static boolean isUnauthenticatedUsage(Exception e) {
    return e instanceof FirebaseApiNotAvailableException
        || e instanceof FirebaseNoSignedInUserException;
  }
}
