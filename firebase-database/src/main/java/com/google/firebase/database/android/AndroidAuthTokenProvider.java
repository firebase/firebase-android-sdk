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

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.internal.IdTokenListener;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.database.core.AuthTokenProvider;
import com.google.firebase.internal.api.FirebaseNoSignedInUserException;
import java.util.concurrent.ExecutorService;

public abstract class AndroidAuthTokenProvider implements AuthTokenProvider {

  public static AuthTokenProvider forAuthenticatedAccess(
      @NonNull final InternalAuthProvider authProvider) {
    return new AuthTokenProvider() {
      @Override
      public void getToken(
          boolean forceRefresh, @NonNull final GetTokenCompletionListener listener) {
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
      }

      @Override
      public void addTokenChangeListener(
          final ExecutorService executorService, final TokenChangeListener tokenListener) {
        IdTokenListener idTokenListener =
            tokenResult ->
                executorService.execute(
                    () -> tokenListener.onTokenChange(/* nullable */ tokenResult.getToken()));
        authProvider.addIdTokenListener(idTokenListener);
      }

      @Override
      public void removeTokenChangeListener(TokenChangeListener tokenListener) {
        // TODO Implement removeIdTokenListener.
      }
    };
  }

  public static AuthTokenProvider forUnauthenticatedAccess() {
    return new AuthTokenProvider() {
      @Override
      public void getToken(boolean forceRefresh, GetTokenCompletionListener listener) {
        listener.onSuccess(null);
      }

      @Override
      public void addTokenChangeListener(
          ExecutorService executorService, TokenChangeListener listener) {
        executorService.execute(() -> listener.onTokenChange(null));
      }

      @Override
      public void removeTokenChangeListener(TokenChangeListener listener) {}
    };
  }

  private static boolean isUnauthenticatedUsage(Exception e) {
    if (e instanceof FirebaseApiNotAvailableException
        || e instanceof FirebaseNoSignedInUserException) {
      return true;
    }

    return false;
  }
}
