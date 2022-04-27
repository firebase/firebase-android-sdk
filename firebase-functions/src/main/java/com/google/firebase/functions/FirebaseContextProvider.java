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

package com.google.firebase.functions;

import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import com.google.firebase.internal.api.FirebaseNoSignedInUserException;
import java.util.concurrent.atomic.AtomicReference;

/** A ContextProvider that uses FirebaseAuth to get the token. */
class FirebaseContextProvider implements ContextProvider {
  private final String TAG = "FirebaseContextProvider";

  private final Provider<InternalAuthProvider> tokenProvider;
  private final Provider<FirebaseInstanceIdInternal> instanceId;
  private final AtomicReference<InternalAppCheckTokenProvider> appCheckRef =
      new AtomicReference<>();

  FirebaseContextProvider(
      Provider<InternalAuthProvider> tokenProvider,
      Provider<FirebaseInstanceIdInternal> instanceId,
      Deferred<InternalAppCheckTokenProvider> appCheckDeferred) {
    this.tokenProvider = tokenProvider;
    this.instanceId = instanceId;
    appCheckDeferred.whenAvailable(
        p -> {
          InternalAppCheckTokenProvider appCheck = p.get();
          appCheckRef.set(appCheck);

          appCheck.addAppCheckTokenListener(
              unused -> {
                // Do nothing; we just need to register a listener so that the App Check SDK knows
                // to auto-refresh the token.
              });
        });
  }

  @Override
  public Task<HttpsCallableContext> getContext() {
    Task<String> authToken = getAuthToken();
    Task<String> appCheckToken = getAppCheckToken();
    return Tasks.whenAll(authToken, appCheckToken)
        .onSuccessTask(
            v ->
                Tasks.forResult(
                    new HttpsCallableContext(
                        authToken.getResult(),
                        instanceId.get().getToken(),
                        appCheckToken.getResult())));
  }

  private Task<String> getAuthToken() {
    InternalAuthProvider auth = tokenProvider.get();
    if (auth == null) {
      return Tasks.forResult(null);
    }
    return auth.getAccessToken(false)
        .continueWith(
            task -> {
              String authToken = null;
              if (!task.isSuccessful()) {
                Exception exception = task.getException();
                if (exception instanceof FirebaseNoSignedInUserException) {
                  // Firebase Auth is linked in, but nobody is signed in, which is fine.
                } else {
                  throw exception;
                }
              } else {
                authToken = task.getResult().getToken();
              }
              return authToken;
            });
  }

  private Task<String> getAppCheckToken() {
    InternalAppCheckTokenProvider appCheck = appCheckRef.get();
    if (appCheck == null) {
      return Tasks.forResult(null);
    }
    return appCheck
        .getToken(false)
        .onSuccessTask(
            result -> {
              if (result.getError() != null) {
                // If there was an error getting the App Check token, do NOT send the placeholder
                // token. Only valid App Check tokens should be sent to the functions backend.
                Log.w(TAG, "Error getting App Check token. Error: " + result.getError());
                return Tasks.forResult(null);
              }
              return Tasks.forResult(result.getToken());
            });
  }
}
