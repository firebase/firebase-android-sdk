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

package com.google.firebase.appcheck.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.internal.util.Clock;
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.heartbeatinfo.HeartBeatController;
import com.google.firebase.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultFirebaseAppCheck extends FirebaseAppCheck {

  private static final long BUFFER_TIME_MILLIS = 5 * 60 * 1000; // 5 minutes in milliseconds

  private final FirebaseApp firebaseApp;
  private final Provider<HeartBeatController> heartbeatControllerProvider;
  private final List<AppCheckTokenListener> appCheckTokenListenerList;
  private final List<AppCheckListener> appCheckListenerList;
  private final StorageHelper storageHelper;
  private final TokenRefreshManager tokenRefreshManager;
  private final ExecutorService backgroundExecutor;
  private final Task<Void> retrieveStoredTokenTask;
  private final Clock clock;

  private AppCheckProviderFactory appCheckProviderFactory;
  private AppCheckProvider appCheckProvider;
  private AppCheckToken cachedToken;

  public DefaultFirebaseAppCheck(
      @NonNull FirebaseApp firebaseApp,
      @NonNull Provider<HeartBeatController> heartBeatController) {
    checkNotNull(firebaseApp);
    checkNotNull(heartBeatController);
    this.firebaseApp = firebaseApp;
    this.heartbeatControllerProvider = heartBeatController;
    this.appCheckTokenListenerList = new ArrayList<>();
    this.appCheckListenerList = new ArrayList<>();
    this.storageHelper =
        new StorageHelper(firebaseApp.getApplicationContext(), firebaseApp.getPersistenceKey());
    this.tokenRefreshManager =
        new TokenRefreshManager(firebaseApp.getApplicationContext(), /* firebaseAppCheck= */ this);
    this.backgroundExecutor = Executors.newCachedThreadPool();
    this.retrieveStoredTokenTask = retrieveStoredAppCheckTokenInBackground(this.backgroundExecutor);
    this.clock = new Clock.DefaultClock();
  }

  @VisibleForTesting
  @NonNull
  Task<Void> retrieveStoredAppCheckTokenInBackground(@NonNull ExecutorService executor) {
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          AppCheckToken token = storageHelper.retrieveAppCheckToken();
          if (token != null) {
            setCachedToken(token);
          }
        });
    return taskCompletionSource.getTask();
  }

  @Override
  public void installAppCheckProviderFactory(@NonNull AppCheckProviderFactory factory) {
    installAppCheckProviderFactory(factory, firebaseApp.isDataCollectionDefaultEnabled());
  }

  @Override
  public void installAppCheckProviderFactory(
      @NonNull AppCheckProviderFactory factory, boolean isTokenAutoRefreshEnabled) {
    checkNotNull(factory);
    appCheckProviderFactory = factory;
    appCheckProvider = factory.create(firebaseApp);
    tokenRefreshManager.setIsAutoRefreshEnabled(isTokenAutoRefreshEnabled);
  }

  @VisibleForTesting
  @Nullable
  public AppCheckProviderFactory getInstalledAppCheckProviderFactory() {
    return appCheckProviderFactory;
  }

  @Override
  public void setTokenAutoRefreshEnabled(boolean isTokenAutoRefreshEnabled) {
    tokenRefreshManager.setIsAutoRefreshEnabled(isTokenAutoRefreshEnabled);
  }

  @VisibleForTesting
  public void resetAppCheckState() {
    appCheckProviderFactory = null;
    appCheckProvider = null;
    cachedToken = null;
    storageHelper.clearSharedPrefs();
  }

  @Override
  public void addAppCheckTokenListener(@NonNull AppCheckTokenListener listener) {
    checkNotNull(listener);
    appCheckTokenListenerList.add(listener);
    tokenRefreshManager.onListenerCountChanged(
        appCheckTokenListenerList.size() + appCheckListenerList.size());
    // If there is a token available, trigger the listener with the current token.
    if (hasValidToken()) {
      listener.onAppCheckTokenChanged(
          DefaultAppCheckTokenResult.constructFromAppCheckToken(cachedToken));
    }
  }

  @Override
  public void removeAppCheckTokenListener(@NonNull AppCheckTokenListener listener) {
    checkNotNull(listener);
    appCheckTokenListenerList.remove(listener);
    tokenRefreshManager.onListenerCountChanged(
        appCheckTokenListenerList.size() + appCheckListenerList.size());
  }

  @Override
  public void addAppCheckListener(@NonNull AppCheckListener listener) {
    checkNotNull(listener);
    appCheckListenerList.add(listener);
    tokenRefreshManager.onListenerCountChanged(
        appCheckTokenListenerList.size() + appCheckListenerList.size());
    // If there is a token available, trigger the listener with the current token.
    if (hasValidToken()) {
      listener.onAppCheckTokenChanged(cachedToken);
    }
  }

  @Override
  public void removeAppCheckListener(@NonNull AppCheckListener listener) {
    checkNotNull(listener);
    appCheckListenerList.remove(listener);
    tokenRefreshManager.onListenerCountChanged(
        appCheckTokenListenerList.size() + appCheckListenerList.size());
  }

  @NonNull
  @Override
  public Task<AppCheckTokenResult> getToken(boolean forceRefresh) {
    return retrieveStoredTokenTask.continueWithTask(
        new Continuation<Void, Task<AppCheckTokenResult>>() {
          @Override
          public Task<AppCheckTokenResult> then(@NonNull Task<Void> task) {
            if (!forceRefresh && hasValidToken()) {
              return Tasks.forResult(
                  DefaultAppCheckTokenResult.constructFromAppCheckToken(cachedToken));
            }
            if (appCheckProvider == null) {
              return Tasks.forResult(
                  DefaultAppCheckTokenResult.constructFromError(
                      new FirebaseException("No AppCheckProvider installed.")));
            }
            // TODO: Cache the in-flight task.
            return fetchTokenFromProvider()
                .continueWithTask(
                    new Continuation<AppCheckToken, Task<AppCheckTokenResult>>() {
                      @Override
                      public Task<AppCheckTokenResult> then(@NonNull Task<AppCheckToken> task) {
                        if (task.isSuccessful()) {
                          return Tasks.forResult(
                              DefaultAppCheckTokenResult.constructFromAppCheckToken(
                                  task.getResult()));
                        }
                        // If the token exchange failed, return a dummy token for integrators to
                        // attach in
                        // their headers.
                        return Tasks.forResult(
                            DefaultAppCheckTokenResult.constructFromError(
                                new FirebaseException(
                                    task.getException().getMessage(), task.getException())));
                      }
                    });
          }
        });
  }

  @NonNull
  @Override
  public Task<AppCheckToken> getAppCheckToken(boolean forceRefresh) {
    return retrieveStoredTokenTask.continueWithTask(
        new Continuation<Void, Task<AppCheckToken>>() {
          @Override
          public Task<AppCheckToken> then(@NonNull Task<Void> task) {
            if (!forceRefresh && hasValidToken()) {
              return Tasks.forResult(cachedToken);
            }
            if (appCheckProvider == null) {
              return Tasks.forException(new FirebaseException("No AppCheckProvider installed."));
            }
            return fetchTokenFromProvider();
          }
        });
  }

  /** Fetches an {@link AppCheckToken} via the installed {@link AppCheckProvider}. */
  Task<AppCheckToken> fetchTokenFromProvider() {
    return appCheckProvider
        .getToken()
        .continueWithTask(
            new Continuation<AppCheckToken, Task<AppCheckToken>>() {
              @Override
              public Task<AppCheckToken> then(@NonNull Task<AppCheckToken> task) {
                if (task.isSuccessful()) {
                  AppCheckToken token = task.getResult();
                  updateStoredToken(token);
                  for (AppCheckListener listener : appCheckListenerList) {
                    listener.onAppCheckTokenChanged(token);
                  }
                  AppCheckTokenResult tokenResult =
                      DefaultAppCheckTokenResult.constructFromAppCheckToken(token);
                  for (AppCheckTokenListener listener : appCheckTokenListenerList) {
                    listener.onAppCheckTokenChanged(tokenResult);
                  }
                }
                return task;
              }
            });
  }

  @NonNull
  Provider<HeartBeatController> getHeartbeatControllerProvider() {
    return heartbeatControllerProvider;
  }

  /** Sets the in-memory cached {@link AppCheckToken}. */
  @VisibleForTesting
  void setCachedToken(@NonNull AppCheckToken token) {
    cachedToken = token;
  }

  /**
   * Updates the {@link AppCheckToken} persisted in {@link android.content.SharedPreferences} as
   * well as the in-memory cached {@link AppCheckToken}.
   */
  private void updateStoredToken(@NonNull AppCheckToken token) {
    storageHelper.saveAppCheckToken(token);
    setCachedToken(token);

    tokenRefreshManager.maybeScheduleTokenRefresh(token);
  }

  private boolean hasValidToken() {
    return cachedToken != null
        && cachedToken.getExpireTimeMillis() - clock.currentTimeMillis() > BUFFER_TIME_MILLIS;
  }
}
