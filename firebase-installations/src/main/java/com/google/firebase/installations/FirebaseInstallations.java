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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.DefaultClock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.local.PersistedFid;
import com.google.firebase.installations.local.PersistedFid.RegistrationStatus;
import com.google.firebase.installations.local.PersistedFidEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
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

  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationServiceClient serviceClient;
  private final PersistedFid persistedFid;
  private final ExecutorService executor;
  private final Utils utils;
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
        new PersistedFid(firebaseApp),
        new Utils(DefaultClock.getInstance()));
  }

  FirebaseInstallations(
      ExecutorService executor,
      FirebaseApp firebaseApp,
      FirebaseInstallationServiceClient serviceClient,
      PersistedFid persistedFid,
      Utils utils) {
    this.firebaseApp = firebaseApp;
    this.serviceClient = serviceClient;
    this.executor = executor;
    this.persistedFid = persistedFid;
    this.utils = utils;
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

  private void triggerOnStateReached(PersistedFidEntry persistedFidEntry) {
    synchronized (lock) {
      Iterator<StateListener> it = listeners.iterator();
      while (it.hasNext()) {
        StateListener l = it.next();
        boolean doneListening = l.onStateReached(persistedFidEntry, shouldRefreshAuthToken);
        if (doneListening) {
          it.remove();
        }
      }
    }
  }

  private final void doRegistration() {
    try {
      PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();

      // New FID needs to be created
      if (persistedFidEntry.isErrored() || persistedFidEntry.isNotGenerated()) {
        String fid = utils.createRandomFid();
        persistFid(fid);
        persistedFidEntry = persistedFid.readPersistedFidEntryValue();
      }

      triggerOnStateReached(persistedFidEntry);

      // FID needs to be registered
      if (persistedFidEntry.isUnregistered()) {
        registerAndSaveFid(persistedFidEntry);
        persistedFidEntry = persistedFid.readPersistedFidEntryValue();
        // Newly registered Fid will have valid auth token. No refresh required.
        synchronized (lock) {
          shouldRefreshAuthToken = false;
        }
      }

      // Don't notify the listeners at this point; we might as well make ure the auth token is up
      // to date before letting them know.

      boolean needRefresh = utils.isAuthTokenExpired(persistedFidEntry);
      if (!needRefresh) {
        synchronized (lock) {
          needRefresh = shouldRefreshAuthToken;
        }
      }

      // Refresh Auth token if needed
      if (needRefresh) {
        fetchAuthTokenFromServer(persistedFidEntry);
        persistedFidEntry = persistedFid.readPersistedFidEntryValue();
        synchronized (lock) {
          shouldRefreshAuthToken = false;
        }
      }

      triggerOnStateReached(persistedFidEntry);
    } catch (Exception e) {
      PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();
      PersistedFidEntry errorFidEntry =
          persistedFidEntry
              .toBuilder()
              .setRegistrationStatus(RegistrationStatus.REGISTER_ERROR)
              .build();
      persistedFid.insertOrUpdatePersistedFidEntry(errorFidEntry);
      triggerOnStateReached(errorFidEntry);
    }
  }

  private void persistFid(String fid) throws FirebaseInstallationsException {
    boolean firstUpdateCacheResult =
        persistedFid.insertOrUpdatePersistedFidEntry(
            PersistedFidEntry.builder()
                .setFirebaseInstallationId(fid)
                .setRegistrationStatus(RegistrationStatus.UNREGISTERED)
                .build());

    if (!firstUpdateCacheResult) {
      throw new FirebaseInstallationsException(
          "Failed to update client side cache.",
          FirebaseInstallationsException.Status.CLIENT_ERROR);
    }
  }

  /** Registers the created Fid with FIS servers and update the shared prefs. */
  private Void registerAndSaveFid(PersistedFidEntry persistedFidEntry)
      throws FirebaseInstallationsException {
    try {
      long creationTime = utils.currentTimeInSecs();

      InstallationResponse installationResponse =
          serviceClient.createFirebaseInstallation(
              /*apiKey= */ firebaseApp.getOptions().getApiKey(),
              /*fid= */ persistedFidEntry.getFirebaseInstallationId(),
              /*projectID= */ firebaseApp.getOptions().getProjectId(),
              /*appId= */ getApplicationId());
      persistedFid.insertOrUpdatePersistedFidEntry(
          PersistedFidEntry.builder()
              .setFirebaseInstallationId(persistedFidEntry.getFirebaseInstallationId())
              .setRegistrationStatus(RegistrationStatus.REGISTERED)
              .setAuthToken(installationResponse.getAuthToken().getToken())
              .setRefreshToken(installationResponse.getRefreshToken())
              .setExpiresInSecs(installationResponse.getAuthToken().getTokenExpirationInSecs())
              .setTokenCreationEpochInSecs(creationTime)
              .build());

    } catch (FirebaseInstallationServiceException exception) {
      throw new FirebaseInstallationsException(
          exception.getMessage(), FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
    return null;
  }

  /** Calls the FIS servers to generate an auth token for this Firebase installation. */
  private InstallationTokenResult fetchAuthTokenFromServer(PersistedFidEntry persistedFidEntry)
      throws FirebaseInstallationsException {
    try {
      long creationTime = utils.currentTimeInSecs();
      InstallationTokenResult tokenResult =
          serviceClient.generateAuthToken(
              /*apiKey= */ firebaseApp.getOptions().getApiKey(),
              /*fid= */ persistedFidEntry.getFirebaseInstallationId(),
              /*projectID= */ firebaseApp.getOptions().getProjectId(),
              /*refreshToken= */ persistedFidEntry.getRefreshToken());

      persistedFid.insertOrUpdatePersistedFidEntry(
          PersistedFidEntry.builder()
              .setFirebaseInstallationId(persistedFidEntry.getFirebaseInstallationId())
              .setRegistrationStatus(RegistrationStatus.REGISTERED)
              .setAuthToken(tokenResult.getToken())
              .setRefreshToken(persistedFidEntry.getRefreshToken())
              .setExpiresInSecs(tokenResult.getTokenExpirationInSecs())
              .setTokenCreationEpochInSecs(creationTime)
              .build());

      return tokenResult;
    } catch (FirebaseInstallationServiceException exception) {
      throw new FirebaseInstallationsException(
          "Failed to generate auth token for a Firebase Installation.",
          FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }

  /**
   * Deletes the firebase installation id of the {@link FirebaseApp} from FIS servers and local
   * storage.
   */
  private Void deleteFirebaseInstallationId() throws FirebaseInstallationsException {

    PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();

    if (persistedFidEntry.isRegistered()) {
      // Call the FIS servers to delete this Firebase Installation Id.
      try {
        serviceClient.deleteFirebaseInstallation(
            firebaseApp.getOptions().getApiKey(),
            persistedFidEntry.getFirebaseInstallationId(),
            firebaseApp.getOptions().getProjectId(),
            persistedFidEntry.getRefreshToken());

      } catch (FirebaseInstallationServiceException exception) {
        throw new FirebaseInstallationsException(
            "Failed to delete a Firebase Installation.",
            FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
      }
    }

    persistedFid.clear();
    return null;
  }
}
