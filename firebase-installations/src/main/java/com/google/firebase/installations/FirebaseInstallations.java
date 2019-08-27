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
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
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
  private final Executor executor;
  private final Clock clock;
  private final Utils utils;

  /** package private constructor. */
  FirebaseInstallations(FirebaseApp firebaseApp) {
    this(
        DefaultClock.getInstance(),
        new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new SynchronousQueue<>()),
        firebaseApp,
        new FirebaseInstallationServiceClient(),
        new PersistedFid(firebaseApp),
        new Utils());
  }

  FirebaseInstallations(
      Clock clock,
      Executor executor,
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
    return Tasks.call(executor, this::getPersistedFid)
        .continueWith(orElse(this::createAndPersistNewFid))
        .onSuccessTask(this::registerFidIfNecessary);
  }

  /** Returns a auth token(public key) of this Firebase app installation. */
  @NonNull
  @Override
  public Task<InstallationTokenResult> getAuthToken(boolean forceRefresh) {
    return Tasks.forResult(InstallationTokenResult.builder().build());
  }

  /**
   * Call to delete this Firebase app installation from Firebase backend. This call would possibly
   * lead Firebase Notification, Firebase RemoteConfig, Firebase Predictions or Firebase In-App
   * Messaging not function properly.
   */
  @NonNull
  @Override
  public Task<Void> delete() {
    return Tasks.forResult(null);
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

  private PersistedFidEntry getPersistedFid() throws FirebaseInstallationsException {
    PersistedFidEntry persistedFidEntry = persistedFid.readPersistedFidEntryValue();
    if (persistedFidMissingOrInErrorState(persistedFidEntry)) {
      throw new FirebaseInstallationsException(
          "Failed to get existing fid.", FirebaseInstallationsException.Status.CLIENT_ERROR);
    }
    return persistedFidEntry;
  }

  private static boolean persistedFidMissingOrInErrorState(PersistedFidEntry persistedFidEntry) {
    return persistedFidEntry == null
        || persistedFidEntry.getRegistrationStatus() == RegistrationStatus.REGISTER_ERROR;
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

  private Task<String> registerFidIfNecessary(PersistedFidEntry persistedFidEntry) {
    String fid = persistedFidEntry.getFirebaseInstallationId();

    // Check if the fid is unregistered
    if (persistedFidEntry.getRegistrationStatus() == RegistrationStatus.UNREGISTERED) {
      updatePersistedFidWithPendingStatus(fid);
      Tasks.call(executor, () -> registerAndSaveFid(persistedFidEntry));
    }
    return Tasks.forResult(fid);
  }

  private void updatePersistedFidWithPendingStatus(String fid) {
    persistedFid.insertOrUpdatePersistedFidEntry(
        PersistedFidEntry.builder()
            .setFirebaseInstallationId(fid)
            .setRegistrationStatus(RegistrationStatus.PENDING)
            .build());
  }

  /** Registers the created Fid with FIS servers and update the shared prefs. */
  private Void registerAndSaveFid(PersistedFidEntry persistedFidEntry)
      throws FirebaseInstallationsException {
    try {
      long creationTime = TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis());

      InstallationResponse installationResponse =
          serviceClient.createFirebaseInstallation(
              firebaseApp.getOptions().getApiKey(),
              persistedFidEntry.getFirebaseInstallationId(),
              firebaseApp.getOptions().getProjectId(),
              getApplicationId());
      persistedFid.insertOrUpdatePersistedFidEntry(
          PersistedFidEntry.builder()
              .setFirebaseInstallationId(persistedFidEntry.getFirebaseInstallationId())
              .setRegistrationStatus(RegistrationStatus.REGISTERED)
              .setAuthToken(installationResponse.getAuthToken().getToken())
              .setRefreshToken(installationResponse.getRefreshToken())
              .setExpiresInSecs(
                  installationResponse.getAuthToken().getTokenExpirationTimestampMillis())
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
}

interface Supplier<T> {
  T get() throws Exception;
}
