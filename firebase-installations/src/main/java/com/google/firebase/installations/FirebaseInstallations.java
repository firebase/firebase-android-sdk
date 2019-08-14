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
import androidx.annotation.WorkerThread;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.local.PersistedFid;
import com.google.firebase.installations.local.PersistedFidEntryValue;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
  private final int THREAD_COUNT = 6;

  /** package private constructor. */
  FirebaseInstallations(FirebaseApp firebaseApp) {
    this(firebaseApp, new PersistedFid(firebaseApp), new FirebaseInstallationServiceClient());
  }

  FirebaseInstallations(
      FirebaseApp firebaseApp,
      PersistedFid persistedFid,
      FirebaseInstallationServiceClient serviceClient) {
    this.firebaseApp = firebaseApp;
    this.serviceClient = serviceClient;
    this.executor = Executors.newFixedThreadPool(THREAD_COUNT);
    this.persistedFid = persistedFid;
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
    return Tasks.call(executor, () -> getFirebaseInstallationId());
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

  /**
   * Get firebase installation id for the {@link FirebaseApp}. Either from the persisted fid entry
   * if already exists and is not error state or create a new Fid on the FIS Servers and persist in
   * the shared prefs.
   *
   * <pre>
   *     The workflow is:
   *          Persisted Fid does not exists Or Persisted Fid is in Error state
   *                                 |
   *             return a newly created random Fid and persist the Fid entry
   *
   *
   *
   *                    Persisted Fid exists And is Registered
   *                                  |
   *                          return the Persisted Fid
   *
   *
   *
   *                  Persisted Fid exists and is in Unregistered state
   *                                     |
   *                Register the Fid on FIS backened and update the PersistedFidEntry
   *                                     |
   *                            return the Persisted Fid
   *
   * </pre>
   */
  @WorkerThread
  private String getFirebaseInstallationId() throws FirebaseInstallationsException {

    PersistedFidEntryValue persistedFidEntryValue = persistedFid.readPersistedFidEntryValue();

    if (persistedFidDoesNotExistsOrInErrorState(persistedFidEntryValue)) {
      return createFidAndPersistFidEntry();
    }

    if (persistedFidExistsAndRegistered(persistedFidEntryValue)) {
      return persistedFidEntryValue.getFirebaseInstallationId();
    }

    if (persistedFidExistsAndUnregistered(persistedFidEntryValue)) {
      registerAndSaveFid(persistedFidEntryValue.getFirebaseInstallationId());
      return persistedFidEntryValue.getFirebaseInstallationId();
    }

    return null;
  }

  private static boolean persistedFidDoesNotExistsOrInErrorState(
      PersistedFidEntryValue persistedFidEntryValue) {
    return persistedFidEntryValue == null
        || persistedFidEntryValue.getPersistedStatus()
            == PersistedFid.PersistedStatus.REGISTER_ERROR;
  }

  private static boolean persistedFidExistsAndRegistered(
      PersistedFidEntryValue persistedFidEntryValue) {
    return persistedFidEntryValue != null
        && persistedFidEntryValue.getPersistedStatus() == PersistedFid.PersistedStatus.REGISTERED;
  }

  private static boolean persistedFidExistsAndUnregistered(
      PersistedFidEntryValue persistedFidEntryValue) {
    return persistedFidEntryValue != null
        && persistedFidEntryValue.getPersistedStatus() == PersistedFid.PersistedStatus.UNREGISTERED;
  }

  private String createFidAndPersistFidEntry() throws FirebaseInstallationsException {
    String fid = Utils.createRandomFid();

    boolean firstUpdateCacheResult =
        persistedFid.insertOrUpdatePersistedFidEntry(
            PersistedFidEntryValue.builder()
                .setFirebaseInstallationId(fid)
                .setPersistedStatus(PersistedFid.PersistedStatus.UNREGISTERED)
                .setTokenCreationEpochInSecs(0)
                .setExpiresInSecs(0)
                .build());

    if (!firstUpdateCacheResult) {
      throw new FirebaseInstallationsException(
          "Failed to update client side cache.",
          FirebaseInstallationsException.Status.CLIENT_ERROR);
    }

    registerAndSaveFid(fid);

    return fid;
  }

  /**
   * Registers the created Fid with FIS Servers if the Network is available and update the shared
   * prefs.
   */
  private void registerAndSaveFid(String fid) throws FirebaseInstallationsException {
    if (!Utils.isNetworkAvailable(firebaseApp.getApplicationContext())) {
      return;
    }

    try {
      long creationTime = Utils.getCurrentTimeInSeconds();

      InstallationResponse installationResponse =
          serviceClient.createFirebaseInstallation(
              firebaseApp.getOptions().getApiKey(),
              fid,
              firebaseApp.getOptions().getProjectId(),
              getApplicationId());

      persistedFid.insertOrUpdatePersistedFidEntry(
          PersistedFidEntryValue.builder()
              .setFirebaseInstallationId(fid)
              .setPersistedStatus(PersistedFid.PersistedStatus.REGISTERED)
              .setAuthToken(installationResponse.getAuthToken().getToken())
              .setRefreshToken(installationResponse.getRefreshToken())
              .setExpiresInSecs(
                  installationResponse.getAuthToken().getTokenExpirationTimestampMillis())
              .setTokenCreationEpochInSecs(creationTime)
              .build());

    } catch (FirebaseInstallationServiceException exception) {
      persistedFid.insertOrUpdatePersistedFidEntry(
          PersistedFidEntryValue.builder()
              .setFirebaseInstallationId(fid)
              .setPersistedStatus(PersistedFid.PersistedStatus.REGISTER_ERROR)
              .setTokenCreationEpochInSecs(0)
              .setExpiresInSecs(0)
              .build());
      throw new FirebaseInstallationsException(
          exception.getMessage(), FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }
}
