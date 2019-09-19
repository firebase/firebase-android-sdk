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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.common.util.DefaultClock;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.local.PersistedFid;
import com.google.firebase.installations.local.PersistedFid.RegistrationStatus;
import com.google.firebase.installations.local.PersistedFidEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
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
  private final Clock clock;
  private final Utils utils;

  private static final long AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS = 3600L; // 1 hour
  private static final long AWAIT_TIMEOUT_IN_SECS = 10L;

  /** package private constructor. */
  FirebaseInstallations(FirebaseApp firebaseApp) {
    this(
        DefaultClock.getInstance(),
        new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()),
        firebaseApp,
        new FirebaseInstallationServiceClient(firebaseApp.getApplicationContext()),
        new PersistedFid(firebaseApp),
        new Utils());
  }

  FirebaseInstallations(
      Clock clock,
      ExecutorService executor,
      FirebaseApp firebaseApp,
      FirebaseInstallationServiceClient serviceClient,
      PersistedFid persistedFid,
      Utils utils) {
    this.clock = clock;
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

  /**
   * Returns a globally unique identifier of this Firebase app installation. This is a url-safe
   * base64 string of a 128-bit integer.
   */
  @NonNull
  @Override
  public Task<String> getId() {
    return getId(null);
  }

  /**
   * Returns a globally unique identifier of this Firebase app installation.Also, updates the {@link
   * AwaitListener} when the FID registration is complete.
   */
  private Task<String> getId(AwaitListener awaitListener) {
    return Tasks.call(executor, this::getPersistedFid)
        .continueWith(orElse(this::createAndPersistNewFid))
        .onSuccessTask(unused -> registerFidIfNecessary(awaitListener));
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
  public synchronized Task<InstallationTokenResult> getAuthToken(
      @AuthTokenOption int authTokenOption) {
    AwaitListener awaitListener = new AwaitListener();
    return getId(awaitListener)
        .continueWith(
            executor,
            awaitFidRegistration(
                () -> refreshAuthTokenIfNecessary(authTokenOption), awaitListener));
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
   * Returns the {@link PersistedFidEntry} from shared prefs.
   *
   * @throws {@link FirebaseInstallationsException} when shared pref is empty or {@link
   *     PersistedFidEntry} is in error state.
   */
  private PersistedFidEntry getPersistedFid() throws FirebaseInstallationsException {
    PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();

    if (persistedFidEntry.isNotGenerated() || persistedFidEntry.isErrored()) {
      throw new FirebaseInstallationsException(
          "Failed to get existing fid.", FirebaseInstallationsException.Status.CLIENT_ERROR);
    }
    return persistedFidEntry;
  }

  @NonNull
  private static <F, T> Continuation<F, T> orElse(@NonNull Supplier<T> supplier) {
    return t -> {
      if (t.isSuccessful()) {
        return (T) t.getResult();
      }
      return supplier.get();
    };
  }

  @NonNull
  private static <F, T> Continuation<F, T> awaitFidRegistration(
      @NonNull Supplier<T> supplier, AwaitListener listener) {
    return t -> {
      // Waiting for Task that registers FID on the FIS Servers
      listener.await(AWAIT_TIMEOUT_IN_SECS, TimeUnit.SECONDS);
      return supplier.get();
    };
  }

  /** Creates a random FID and persists it in the shared prefs with UNREGISTERED status. */
  private PersistedFidEntry createAndPersistNewFid() throws FirebaseInstallationsException {
    String fid = utils.createRandomFid();
    persistFid(fid);
    PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();
    return persistedFidEntry;
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

  /**
   * Registers the FID with FIS servers if FID is in UNREGISTERED state. Also, refreshes auth token
   * if expired.
   *
   * <p>Updates FID registration status to PENDING to avoid multiple network calls to FIS Servers.
   */
  private Task<String> registerFidIfNecessary(AwaitListener listener)
      throws FirebaseInstallationsException {
    boolean isNetworkCallRequired = false;

    synchronized (persistedFid) {
      PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();

      if (persistedFidEntry.isNotGenerated()) {
        throw new FirebaseInstallationsException(
            "Local storage has no persisted Fid entry.",
            FirebaseInstallationsException.Status.CLIENT_ERROR);
      }

      // If FID registration status is REGISTERED and auth token is expired, or registration status
      // is UNREGISTERED network call is required.
      if ((persistedFidEntry.isRegistered() && isAuthTokenExpired(persistedFidEntry))
          || persistedFidEntry.isUnregistered()) {
        isNetworkCallRequired = true;

        // Update FID registration status to PENDING to avoid multiple network calls to FIS Servers.
        persistedFid.insertOrUpdatePersistedFidEntry(
            persistedFidEntry
                .toBuilder()
                .setRegistrationStatus(RegistrationStatus.PENDING)
                .build());
      }
    }

    PersistedFidEntry updatedPersistedFidEntry = persistedFid.readPersistedFidEntryValue();
    String fid = updatedPersistedFidEntry.getFirebaseInstallationId();

    if (!isNetworkCallRequired) {

      // If the Fid is registered, update the listener if awaiting
      if (listener != null && updatedPersistedFidEntry.isRegistered()) {
        listener.onSuccess();
      }
      return Tasks.forResult(fid);
    }

    // If fid registration for this firebase installation is incomplete without an expired auth
    // token, execute Fid registration.
    if (!isAuthTokenExpired(updatedPersistedFidEntry)) {
      Task<Void> fidRegistrationTask =
          Tasks.call(executor, () -> registerAndSaveFid(updatedPersistedFidEntry));

      // Update the listener if awaiting
      if (listener != null) {
        fidRegistrationTask.addOnCompleteListener(listener);
      }
      return Tasks.forResult(fid);
    }

    // If auth token is expired, refresh FID auth token with FIS servers in a background thread
    // and updates the listener on completion
    Task<InstallationTokenResult> refreshAuthTokenTask =
        Tasks.call(executor, () -> refreshAuthTokenIfNecessary(FORCE_REFRESH));

    // Update the listener if awaiting
    if (listener != null) {
      refreshAuthTokenTask.addOnSuccessListener((unused) -> listener.onSuccess());
    }
    return Tasks.forResult(fid);
  }

  /** Registers the created Fid with FIS servers and update the shared prefs. */
  private Void registerAndSaveFid(PersistedFidEntry persistedFidEntry)
      throws FirebaseInstallationsException {
    try {
      long creationTime = currentTimeInSecs();

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
      persistedFid.insertOrUpdatePersistedFidEntry(
          PersistedFidEntry.builder()
              .setFirebaseInstallationId(persistedFidEntry.getFirebaseInstallationId())
              .setRegistrationStatus(RegistrationStatus.REGISTER_ERROR)
              .build());
      throw new FirebaseInstallationsException(
          exception.getMessage(), FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
    return null;
  }

  private InstallationTokenResult refreshAuthTokenIfNecessary(int authTokenOption)
      throws FirebaseInstallationsException {

    PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();

    if (persistedFidEntry.isNotGenerated()
        || persistedFidEntry.isUnregistered()
        || persistedFidEntry.isErrored()) {
      throw new FirebaseInstallationsException(
          "Firebase Installation is not registered.",
          FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }

    switch (authTokenOption) {
      case FORCE_REFRESH:
        return fetchAuthTokenFromServer(persistedFidEntry);
      case DO_NOT_FORCE_REFRESH:
        return getValidAuthToken(persistedFidEntry);
      default:
        throw new FirebaseInstallationsException(
            "Incorrect refreshAuthTokenOption.",
            FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }

  /**
   * Returns a {@link InstallationTokenResult} created from the {@link PersistedFidEntry} if the
   * auth token is valid else generates a new auth token by calling the FIS servers.
   */
  private InstallationTokenResult getValidAuthToken(PersistedFidEntry persistedFidEntry)
      throws FirebaseInstallationsException {

    return isAuthTokenExpired(persistedFidEntry)
        ? fetchAuthTokenFromServer(persistedFidEntry)
        : InstallationTokenResult.builder()
            .setToken(persistedFidEntry.getAuthToken())
            .setTokenExpirationInSecs(persistedFidEntry.getExpiresInSecs())
            .build();
  }

  /** Calls the FIS servers to generate an auth token for this Firebase installation. */
  private InstallationTokenResult fetchAuthTokenFromServer(PersistedFidEntry persistedFidEntry)
      throws FirebaseInstallationsException {
    try {
      long creationTime = currentTimeInSecs();
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
   * Checks if the FIS Auth token is expired or going to expire in next 1 hour
   * (AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS).
   */
  private boolean isAuthTokenExpired(PersistedFidEntry persistedFidEntry) {
    return (persistedFidEntry.getTokenCreationEpochInSecs() + persistedFidEntry.getExpiresInSecs()
        > currentTimeInSecs() + AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS);
  }

  private long currentTimeInSecs() {
    return TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis());
  }

  /**
   * Deletes the firebase installation id of the {@link FirebaseApp} from FIS servers and local
   * storage.
   */
  private Void deleteFirebaseInstallationId() throws FirebaseInstallationsException {

    PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();

    if (persistedFidEntry.isRegistered()) {
      // Call the FIS servers to delete this firebase installation id.
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

interface Supplier<T> {
  T get() throws Exception;
}
