// Copyright 2019 Google LLC
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

package com.google.firebase.installations;

import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.DefaultClock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.installations.local.IidStore;
import com.google.firebase.installations.local.PersistedInstallation;
import com.google.firebase.installations.local.PersistedInstallation.RegistrationStatus;
import com.google.firebase.installations.local.PersistedInstallationEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.InstallationResponse;
import com.google.firebase.installations.remote.InstallationResponse.ResponseCode;
import com.google.firebase.installations.remote.TokenResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for Firebase Installations.
 *
 * <p>Firebase Installations does
 *
 * <ul>
 *   <li>provide unique identifier for a Firebase installation
 *   <li>provide auth token of a Firebase installation
 *   <li>provide a API to GDPR-delete a Firebase installation
 * </ul>
 */
public class FirebaseInstallations implements FirebaseInstallationsApi {
  private static final String TAG = "FirebaseInstallations";

  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationServiceClient serviceClient;
  private final PersistedInstallation persistedInstallation;
  private final ExecutorService executor;
  private final Utils utils;
  private final IidStore iidStore;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private boolean shouldRefreshAuthToken;

  @GuardedBy("lock")
  private final List<StateListener> listeners = new ArrayList<>();

  /** package private constructor. */
  FirebaseInstallations(FirebaseApp firebaseApp) {
    this(
        new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()),
        firebaseApp,
        new FirebaseInstallationServiceClient(firebaseApp.getApplicationContext()),
        new PersistedInstallation(firebaseApp),
        new Utils(DefaultClock.getInstance()),
        new IidStore());
  }

  FirebaseInstallations(
      ExecutorService executor,
      FirebaseApp firebaseApp,
      FirebaseInstallationServiceClient serviceClient,
      PersistedInstallation persistedInstallation,
      Utils utils,
      IidStore iidStore) {
    this.firebaseApp = firebaseApp;
    this.serviceClient = serviceClient;
    this.executor = executor;
    this.persistedInstallation = persistedInstallation;
    this.utils = utils;
    this.iidStore = iidStore;
  }

  /**
   * Returns the {@link FirebaseInstallationsApi} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseInstallationsApi} instance
   */
  @NonNull
  public static FirebaseInstallations getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link FirebaseInstallations} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link FirebaseInstallations} instance
   */
  @NonNull
  public static FirebaseInstallations getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return (FirebaseInstallations) app.get(FirebaseInstallationsApi.class);
  }

  /** Returns the application id of the {@link FirebaseApp} of this {@link FirebaseInstallations} */
  @VisibleForTesting
  String getApplicationId() {
    return firebaseApp.getOptions().getApplicationId();
  }

  /** Returns the nick name of the {@link FirebaseApp} of this {@link FirebaseInstallations} */
  @VisibleForTesting
  String getName() {
    return firebaseApp.getName();
  }

  /**
   * Returns a globally unique identifier of this Firebase app installation. This is a url-safe
   * base64 string of a 128-bit integer.
   */
  @NonNull
  @Override
  public Task<String> getId() {
    Task<String> task = addGetIdListener();
    executor.execute(this::doRegistration);
    return task;
  }

  /**
   * Returns a valid authentication token for the Firebase installation. Generates a new token if
   * one doesn't exist, is expired or about to expire.
   *
   * <p>Should only be called if the Firebase Installation is registered.
   *
   * @param authTokenOption Options to get FIS Auth Token either by force refreshing or not. Accepts
   *     {@link AuthTokenOption} values. Default value of AuthTokenOption = DO_NOT_FORCE_REFRESH.
   */
  @NonNull
  @Override
  public Task<InstallationTokenResult> getAuthToken(@AuthTokenOption int authTokenOption) {
    Task<InstallationTokenResult> task = addGetAuthTokenListener(authTokenOption);
    executor.execute(this::doRegistration);
    return task;
  }

  /**
   * Call to delete this Firebase app installation from Firebase backend. This call would possibly
   * lead Firebase Notification, Firebase RemoteConfig, Firebase Predictions or Firebase In-App
   * Messaging not function properly.
   */
  @NonNull
  @Override
  public Task<Void> delete() {
    return Tasks.call(executor, this::deleteFirebaseInstallationId);
  }

  private Task<String> addGetIdListener() {
    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    StateListener l = new GetIdListener(taskCompletionSource);
    synchronized (lock) {
      listeners.add(l);
    }
    return taskCompletionSource.getTask();
  }

  private Task<InstallationTokenResult> addGetAuthTokenListener(
      @AuthTokenOption int authTokenOption) {
    TaskCompletionSource<InstallationTokenResult> taskCompletionSource =
        new TaskCompletionSource<>();
    StateListener l = new GetAuthTokenListener(utils, taskCompletionSource);
    synchronized (lock) {
      if (authTokenOption == FORCE_REFRESH) {
        shouldRefreshAuthToken = true;
      }
      listeners.add(l);
    }
    return taskCompletionSource.getTask();
  }

  private void triggerOnStateReached(PersistedInstallationEntry persistedInstallationEntry) {
    synchronized (lock) {
      Iterator<StateListener> it = listeners.iterator();
      while (it.hasNext()) {
        StateListener l = it.next();
        boolean doneListening =
            l.onStateReached(persistedInstallationEntry, shouldRefreshAuthToken);
        if (doneListening) {
          it.remove();
        }
      }
    }
  }

  private void triggerOnException(
      PersistedInstallationEntry persistedInstallationEntry, Exception exception) {
    synchronized (lock) {
      Iterator<StateListener> it = listeners.iterator();
      while (it.hasNext()) {
        StateListener l = it.next();
        boolean doneListening = l.onException(persistedInstallationEntry, exception);
        if (doneListening) {
          it.remove();
        }
      }
    }
  }

  private final void doRegistration() {
    Log.d(TAG, "doRegistration");
    try {
      PersistedInstallationEntry persistedInstallationEntry =
          persistedInstallation.readPersistedInstallationEntryValue();
      Log.d(TAG, "status = " + persistedInstallationEntry.getRegistrationStatus());

      // New FID needs to be created
      if (persistedInstallationEntry.isNotGenerated()) {
        Log.d(TAG, "need a new FID");

        // For a default firebase installation read the existing iid. For other custom firebase
        // installations create a new fid
        String fid = readExistingIidOrCreateFid();
        Log.d(TAG, "using fid " + fid);
        persistFid(fid);
        Log.d(TAG, "wrote fid to disk");
        persistedInstallationEntry = persistedInstallation.readPersistedInstallationEntryValue();
        Log.d(TAG, "reread entry: " + persistedInstallationEntry);
      }

      if (persistedInstallationEntry.isErrored()) {
        Log.d(TAG, "in error state, bailing");
        throw new FirebaseInstallationsException(
            persistedInstallationEntry.getFisError(),
            FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
      }

      triggerOnStateReached(persistedInstallationEntry);

      // FID needs to be registered
      if (persistedInstallationEntry.isUnregistered()) {
        Log.d(TAG, "the fid is unregistered, register it now");
        persistedInstallationEntry = registerAndSaveFid(persistedInstallationEntry);
        Log.d(TAG, "registered the fid, entry is now " + persistedInstallationEntry);
        // TODO(fredq): I think this is unnecessary, since we check if the token is expired next
        // // Newly registered Fid will have valid auth token. No refresh required.
        // synchronized (lock) {
        //   shouldRefreshAuthToken = false;
        // }
      }

      // Don't notify the listeners at this point; we might as well make ure the auth token is up
      // to date before letting them know.

      boolean needRefresh = utils.isAuthTokenExpired(persistedInstallationEntry);
      Log.d(TAG, "authtoken expired: " + needRefresh);
      if (!needRefresh) {
        synchronized (lock) {
          needRefresh = shouldRefreshAuthToken;
        }
      }

      // Refresh Auth token if needed
      if (needRefresh) {
        Log.d(TAG, "need to refresh the authtoken");
        TokenResult tokenResult = fetchAuthTokenFromServer(persistedInstallationEntry);
        Log.d(TAG, "fetched token: " + tokenResult);
        persistedInstallationEntry = persistedInstallation.readPersistedInstallationEntryValue();

        // If tokenResult is not null and is not successful, it was cleared due to authentication
        // error during auth token generation.
        if (!tokenResult.isSuccessful()) {
          Log.d(TAG, "fetched token is bad: " + tokenResult);
          triggerOnException(
              persistedInstallationEntry,
              new FirebaseInstallationsException(
                  "Failed to generate auth token for this Firebase Installation. Call getId() "
                      + "to recreate a new Fid and a valid auth token.",
                  FirebaseInstallationsException.Status.AUTHENTICATION_ERROR));
          return;
        }

        synchronized (lock) {
          shouldRefreshAuthToken = false;
        }
      }

      triggerOnStateReached(persistedInstallationEntry);
      Log.d(TAG, "finished doRegistration: " + persistedInstallationEntry);
    } catch (Exception e) {
      PersistedInstallationEntry persistedInstallationEntry =
          persistedInstallation.readPersistedInstallationEntryValue();
      Log.d(TAG, "doRegistration failed: " + persistedInstallationEntry, e);
      PersistedInstallationEntry errorInstallationEntry =
          persistedInstallationEntry
              .toBuilder()
              .setFisError(e.getMessage())
              .setRegistrationStatus(RegistrationStatus.REGISTER_ERROR)
              .build();
      persistedInstallation.writePreferencesToDisk(errorInstallationEntry);
      triggerOnException(errorInstallationEntry, e);
    }
  }

  private String readExistingIidOrCreateFid() {
    // Check if this firebase app is the default (first initialized) instance
    if (!firebaseApp.equals(FirebaseApp.getInstance())) {
      return utils.createRandomFid();
    }
    // For a default firebase installation, read the existing iid from shared prefs
    String fid = iidStore.readIid();
    if (fid == null) {
      fid = utils.createRandomFid();
    }
    return fid;
  }

  private void persistFid(String fid) throws FirebaseInstallationsException {
    persistedInstallation.writePreferencesToDisk(
        PersistedInstallationEntry.builder()
            .setFirebaseInstallationId(fid)
            .setRegistrationStatus(RegistrationStatus.UNREGISTERED)
            .build());
  }

  /** Registers the created Fid with FIS servers and update the shared prefs. */
  private PersistedInstallationEntry registerAndSaveFid(PersistedInstallationEntry entry)
      throws FirebaseInstallationsException {
    try {
      long creationTime = utils.currentTimeInSecs();

      InstallationResponse installationResponse =
          serviceClient.createFirebaseInstallation(
              /*apiKey= */ firebaseApp.getOptions().getApiKey(),
              /*fid= */ entry.getFirebaseInstallationId(),
              /*projectID= */ firebaseApp.getOptions().getProjectId(),
              /*appId= */ getApplicationId());
      if (installationResponse.getResponseCode() == ResponseCode.OK) {
        entry = PersistedInstallationEntry.builder()
            .setFirebaseInstallationId(installationResponse.getFid())
            .setRegistrationStatus(RegistrationStatus.REGISTERED)
            .setAuthToken(installationResponse.getAuthToken().getToken())
            .setRefreshToken(installationResponse.getRefreshToken())
            .setExpiresInSecs(installationResponse.getAuthToken().getTokenExpirationTimestamp())
            .setTokenCreationEpochInSecs(creationTime)
            .build();
        persistedInstallation.writePreferencesToDisk(entry);
      }
      return entry;

    } catch (FirebaseException e) {
      throw new FirebaseInstallationsException("error registering fid",
          FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR, e);
    }
  }

  /** Calls the FIS servers to generate an auth token for this Firebase installation. */
  private TokenResult fetchAuthTokenFromServer(
      PersistedInstallationEntry entry) throws FirebaseInstallationsException {
    try {
      long creationTime = utils.currentTimeInSecs();
      TokenResult tokenResult =
          serviceClient.generateAuthToken(
              /*apiKey= */ firebaseApp.getOptions().getApiKey(),
              /*fid= */ entry.getFirebaseInstallationId(),
              /*projectID= */ firebaseApp.getOptions().getProjectId(),
              /*refreshToken= */ entry.getRefreshToken());

      if (tokenResult.isSuccessful()) {
        entry = entry
            .toBuilder()
            .setRegistrationStatus(RegistrationStatus.REGISTERED)
            .setAuthToken(tokenResult.getToken())
            .setExpiresInSecs(tokenResult.getTokenExpirationTimestamp())
            .setTokenCreationEpochInSecs(creationTime)
            .build();
        persistedInstallation.writePreferencesToDisk(
            entry);
        // TODO(fredq): why clear if there is a network error?
      // } else {
      //   persistedInstallation.clear();
      }
      return tokenResult;

    } catch (FirebaseException e) {
      throw new FirebaseInstallationsException(
          "Failed to generate auth token for a Firebase Installation.",
          FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR, e);
    }
  }

  /**
   * Deletes the firebase installation id of the {@link FirebaseApp} from FIS servers and local
   * storage.
   */
  private Void deleteFirebaseInstallationId() throws FirebaseInstallationsException {
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    Log.d(TAG, "deleteFirebaseInstallationId: " + entry);
    if (entry.isRegistered()) {
      // Call the FIS servers to delete this Firebase Installation Id.
      try {
        serviceClient.deleteFirebaseInstallation(
            firebaseApp.getOptions().getApiKey(),
            entry.getFirebaseInstallationId(),
            firebaseApp.getOptions().getProjectId(),
            entry.getRefreshToken());

      } catch (FirebaseException exception) {
        throw new FirebaseInstallationsException(
            "Failed to delete a Firebase Installation.",
            FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
      }
    }

    persistedInstallation.clear();
    return null;
  }
}
