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

package com.google.firebase.firestore.auth;

import android.annotation.SuppressLint;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.internal.IdTokenListener;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;

/**
 * FirebaseAuthCredentialsProvider uses Firebase Auth via {@link FirebaseApp} to get an auth token.
 *
 * <p>NOTE: To simplify the implementation, it requires that you call {@link #setChangeListener} no
 * more than once and don't call {@link #getToken} after calling {@link #removeChangeListener}.
 *
 * <p>This class must be implemented to be thread-safe since getToken() and
 * set/removeChangeListener() are called from the Firestore worker thread, but the getToken() Task
 * callbacks and user change notifications will be executed on arbitrary different threads.
 */
public final class FirebaseAuthCredentialsProvider extends CredentialsProvider<User> {

  private static final String LOG_TAG = "FirebaseAuthCredentialsProvider";

  /**
   * The listener registered with FirebaseApp; used to stop receiving auth changes once
   * changeListener is removed.
   */
  private final IdTokenListener idTokenListener = result -> onIdTokenChanged();

  /**
   * The {@link Provider} that gives access to the {@link InternalAuthProvider} instance; initially,
   * its {@link Provider#get} method returns {@code null}, but will be changed to a new {@link
   * Provider} once the "auth" module becomes available.
   */
  @Nullable
  @GuardedBy("this")
  private InternalAuthProvider internalAuthProvider;

  /** The listener to be notified of credential changes (sign-in / sign-out, token changes). */
  @Nullable
  @GuardedBy("this")
  private Listener<User> changeListener;

  /** Counter used to detect if the token changed while a getToken request was outstanding. */
  @GuardedBy("this")
  private int tokenCounter;

  @GuardedBy("this")
  private boolean forceRefresh;

  /** Creates a new FirebaseAuthCredentialsProvider. */
  @SuppressLint("ProviderAssignment") // TODO: Remove this @SuppressLint once b/181014061 is fixed.
  public FirebaseAuthCredentialsProvider(Deferred<InternalAuthProvider> deferredAuthProvider) {
    deferredAuthProvider.whenAvailable(
        provider -> {
          synchronized (this) {
            internalAuthProvider = provider.get();
            onIdTokenChanged();
            internalAuthProvider.addIdTokenListener(idTokenListener);
          }
        });
  }

  @Override
  public synchronized Task<String> getToken() {
    if (internalAuthProvider == null) {
      return Tasks.forException(new FirebaseApiNotAvailableException("auth is not available"));
    }

    Task<GetTokenResult> res = internalAuthProvider.getAccessToken(forceRefresh);
    forceRefresh = false;

    // Take note of the current value of the tokenCounter so that this method can fail (with a
    // FirebaseFirestoreException) if there is a token change while the request is outstanding.
    final int savedCounter = tokenCounter;
    return res.continueWithTask(
        Executors.DIRECT_EXECUTOR,
        task -> {
          synchronized (this) {
            // Cancel the request since the token changed while the request was outstanding so the
            // response is potentially for a previous user (which user, we can't be sure).
            if (savedCounter != tokenCounter) {
              Logger.debug(LOG_TAG, "getToken aborted due to token change");
              return getToken();
            }

            if (task.isSuccessful()) {
              return Tasks.forResult(task.getResult().getToken());
            } else {
              return Tasks.forException(task.getException());
            }
          }
        });
  }

  @Override
  public synchronized void invalidateToken() {
    forceRefresh = true;
  }

  @Override
  public synchronized void setChangeListener(@NonNull Listener<User> changeListener) {
    this.changeListener = changeListener;

    // Fire the initial event.
    changeListener.onValue(getUser());
  }

  @Override
  public synchronized void removeChangeListener() {
    changeListener = null;

    if (internalAuthProvider != null) {
      internalAuthProvider.removeIdTokenListener(idTokenListener);
    }
  }

  /** Invoked when the auth token changes. */
  private synchronized void onIdTokenChanged() {
    tokenCounter++;
    if (changeListener != null) {
      changeListener.onValue(getUser());
    }
  }

  /** Returns the current {@link User} as obtained from the given InternalAuthProvider. */
  private synchronized User getUser() {
    @Nullable String uid = (internalAuthProvider == null) ? null : internalAuthProvider.getUid();
    return uid != null ? new User(uid) : User.UNAUTHENTICATED;
  }
}
