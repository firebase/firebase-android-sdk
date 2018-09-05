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

import android.support.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseApp.IdTokenListener;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.database.core.AuthTokenProvider;
import com.google.firebase.internal.InternalTokenResult;
import com.google.firebase.internal.api.FirebaseNoSignedInUserException;
import java.util.concurrent.ScheduledExecutorService;

public class AndroidAuthTokenProvider implements AuthTokenProvider {
  private final ScheduledExecutorService executorService;
  private final FirebaseApp firebaseApp;

  public AndroidAuthTokenProvider(
      @NonNull FirebaseApp firebaseApp, @NonNull ScheduledExecutorService executorService) {
    this.firebaseApp = firebaseApp;
    this.executorService = executorService;
  }

  @Override
  public void getToken(boolean forceRefresh, @NonNull final GetTokenCompletionListener listener) {
    Task<GetTokenResult> getTokenResult = firebaseApp.getToken(forceRefresh);

    getTokenResult
        .addOnSuccessListener(
            executorService,
            new OnSuccessListener<GetTokenResult>() {
              @Override
              public void onSuccess(GetTokenResult result) {
                listener.onSuccess(result.getToken());
              }
            })
        .addOnFailureListener(
            executorService,
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                if (isUnauthenticatedUsage(e)) {
                  listener.onSuccess(null);
                } else {
                  // TODO: Figure out how to plumb errors through in a sane
                  // way.
                  listener.onError(e.getMessage());
                }
              }

              private boolean isUnauthenticatedUsage(Exception e) {
                if (e instanceof FirebaseApiNotAvailableException
                    || e instanceof FirebaseNoSignedInUserException) {
                  return true;
                }

                return false;
              }
            });
  }

  @Override
  public void addTokenChangeListener(final TokenChangeListener tokenListener) {
    IdTokenListener idTokenListener = produceIdTokenListener(tokenListener);
    firebaseApp.addIdTokenListener(idTokenListener);
  }

  private IdTokenListener produceIdTokenListener(final TokenChangeListener tokenListener) {
    return new FirebaseApp.IdTokenListener() {
      @Override
      public void onIdTokenChanged(@NonNull final InternalTokenResult tokenResult) {
        executorService.execute(
            new Runnable() {
              @Override
              public void run() {
                tokenListener.onTokenChange(/* nullable */ tokenResult.getToken());
              }
            });
      }
    };
  }

  @Override
  public void removeTokenChangeListener(TokenChangeListener tokenListener) {
    // TODO Implement removeIdTokenListener.
  }
}
