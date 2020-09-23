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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.internal.IdTokenListener;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.firestore.util.Logger;

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
public final class FirebaseAuthCredentialsProvider extends CredentialsProvider {

  private static final String LOG_TAG = "FirebaseAuthCredentialsProvider";

  private final InternalAuthProvider authProvider;

  /**
   * The listener registered with FirebaseApp; used to stop receiving auth changes once
   * changeListener is removed.
   */
  private final IdTokenListener idTokenListener;

  /** The listener to be notified of credential changes (sign-in / sign-out, token changes). */
  @Nullable private Listener<User> changeListener;

  /** The current user as reported to us via our IdTokenListener. */
  private User currentUser;

  /** Counter used to detect if the token changed while a getToken request was outstanding. */
  private int tokenCounter;

  private boolean forceRefresh;

  /** Creates a new FirebaseAuthCredentialsProvider. */
  public FirebaseAuthCredentialsProvider(InternalAuthProvider authProvider) {
    this.authProvider = authProvider;
    this.idTokenListener =
        token -> {
          synchronized (this) {
            currentUser = getUser();
            tokenCounter++;

            if (changeListener != null) {
              changeListener.onValue(currentUser);
            }
          }
        };
    currentUser = getUser();
    tokenCounter = 0;

    authProvider.addIdTokenListener(idTokenListener);
  }

  @Override
  public synchronized Task<String> getToken() {
    boolean doForceRefresh = forceRefresh;
    forceRefresh = false;
    Task<GetTokenResult> res = authProvider.getAccessToken(doForceRefresh);

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
    changeListener.onValue(currentUser);
  }

  @Override
  public synchronized void removeChangeListener() {
    changeListener = null;
    authProvider.removeIdTokenListener(idTokenListener);
  }

  /** Returns the current {@link User} as obtained from the given FirebaseApp instance. */
  private User getUser() {
    @Nullable String uid = authProvider.getUid();
    return uid != null ? new User(uid) : User.UNAUTHENTICATED;
  }
}
