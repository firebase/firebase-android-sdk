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

import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.inject.Provider;
import com.google.firebase.internal.api.FirebaseNoSignedInUserException;

/** A ContextProvider that uses FirebaseAuth to get the token. */
class FirebaseContextProvider implements ContextProvider {
  @Nullable private final Provider<InternalAuthProvider> tokenProvider;
  private final Provider<FirebaseInstanceIdInternal> instanceId;

  FirebaseContextProvider(
      @Nullable Provider<InternalAuthProvider> tokenProvider,
      Provider<FirebaseInstanceIdInternal> instanceId) {
    this.tokenProvider = tokenProvider;
    this.instanceId = instanceId;
  }

  @Override
  public Task<HttpsCallableContext> getContext() {

    if (tokenProvider == null || tokenProvider.get() == null) {
      TaskCompletionSource<HttpsCallableContext> tcs = new TaskCompletionSource<>();
      tcs.setResult(new HttpsCallableContext(null, instanceId.get().getToken()));
      return tcs.getTask();
    }

    return tokenProvider
        .get()
        .getAccessToken(false)
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

              String instanceIdToken = instanceId.get().getToken();

              return new HttpsCallableContext(authToken, instanceIdToken);
            });
  }
}
