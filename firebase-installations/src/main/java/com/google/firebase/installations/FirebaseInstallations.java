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
import com.google.firebase.installations.local.FirebaseInstallationIdCache;
import com.google.firebase.installations.local.FirebaseInstallationIdCacheEntryValue;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Entry point for Firebase Installations SDK.
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
  private final FirebaseInstallationIdCache localCache;
  private final Executor executor;

  /** package private constructor. */
  FirebaseInstallations(FirebaseApp firebaseApp) {
    this(
        firebaseApp,
        new FirebaseInstallationIdCache(firebaseApp),
        new FirebaseInstallationServiceClient());
  }

  FirebaseInstallations(
      FirebaseApp firebaseApp,
      FirebaseInstallationIdCache localCache,
      FirebaseInstallationServiceClient serviceClient) {
    this.firebaseApp = firebaseApp;
    this.serviceClient = serviceClient;
    this.executor = Executors.newFixedThreadPool(4);
    this.localCache = localCache;
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
    return Tasks.call(executor, () -> createFirebaseInstallationId());
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
   * Create firebase installation id of the {@link FirebaseApp} on Firebase Installation backend and
   * client side cache.
   *
   * <pre>
   *     The workflow is:
   *         check diff against cache or cache status is not REGISTERED
   *                                 |
   *               cache status is UNREGISTERED and Network is available
   *                                 |
   *                           send http request to backend
   *                                 |                  |
   *  on success: set cache entry status to REGISTERED  |
   *                                 |                  |
   *                                 |         on failure: set cache entry status to REGISTER_ERROR
   *                                 |                     |
   *                                return          throw exception
   *
   *                  if cache is empty or cache status is  REGISTER_ERROR
   *                                         |
   *                                  create random fid
   *                                         |
   *                            update cache with cache status UNREGISTERED
   *                                         |
   *                           send http request to backend
   *                                 |                  |
   *  on success: set cache entry status to REGISTERED  |
   *                                 |                  |
   *                                 |         on failure: set cache entry status to REGISTER_ERROR
   *                                 |                     |
   *                                return          throw exception
   * </pre>
   */
  /**
   * Create firebase installation id of the {@link FirebaseApp} on Firebase Installation backend.
   */
  @WorkerThread
  private String createFirebaseInstallationId() throws FirebaseInstallationException {

    FirebaseInstallationIdCacheEntryValue cacheEntryValue = localCache.readCacheEntryValue();
    if (cacheEntryValue != null && !cacheEntryValue.getFirebaseInstallationId().isEmpty()) {
      if (cacheEntryValue.getCacheStatus() == FirebaseInstallationIdCache.CacheStatus.REGISTERED) {
        // If the firebase installation id is created and cached, there's no need to update.
        return cacheEntryValue.getFirebaseInstallationId();
      } else if (cacheEntryValue.getCacheStatus()
              == FirebaseInstallationIdCache.CacheStatus.UNREGISTERED
          && Utils.isNetworkAvailable(firebaseApp.getApplicationContext())) {
        callCreateFirebaseInstallationService(cacheEntryValue.getFirebaseInstallationId());
        return cacheEntryValue.getFirebaseInstallationId();
      }
    }

    if (cacheEntryValue == null
        || cacheEntryValue.getCacheStatus()
            == FirebaseInstallationIdCache.CacheStatus.REGISTER_ERROR) {
      String fid = Utils.createRandomFid();

      boolean firstUpdateCacheResult =
          localCache.insertOrUpdateCacheEntry(
              FirebaseInstallationIdCacheEntryValue.create(
                  fid, FirebaseInstallationIdCache.CacheStatus.UNREGISTERED, "", "", 0, 0));

      if (!firstUpdateCacheResult) {
        throw new FirebaseInstallationException(
            "Failed to update client side cache.",
            FirebaseInstallationException.Status.CLIENT_ERROR);
      }

      if (Utils.isNetworkAvailable(firebaseApp.getApplicationContext())) {
        callCreateFirebaseInstallationService(fid);
      }
      return fid;
    }
    return null;
  }

  /**
   * Calls the Firebase installation backend to create a firebase installtion with the given fid.
   */
  private void callCreateFirebaseInstallationService(String fid)
      throws FirebaseInstallationException {
    try {
      long creationTime = Utils.getCurrentTimeInSeconds();
      InstallationResponse installationResponse =
          serviceClient.createFirebaseInstallation(
              firebaseApp.getOptions().getProjectId(),
              firebaseApp.getOptions().getApiKey(),
              fid,
              getApplicationId());

      localCache.insertOrUpdateCacheEntry(
          FirebaseInstallationIdCacheEntryValue.create(
              fid,
              FirebaseInstallationIdCache.CacheStatus.REGISTERED,
              installationResponse.getAuthToken().getToken(),
              installationResponse.getRefreshToken(),
              creationTime,
              installationResponse.getAuthToken().getTokenExpirationTimestampMillis()));

    } catch (FirebaseInstallationServiceException exception) {
      localCache.insertOrUpdateCacheEntry(
          FirebaseInstallationIdCacheEntryValue.create(
              fid, FirebaseInstallationIdCache.CacheStatus.REGISTER_ERROR, "", "", 0, 0));
      throw new FirebaseInstallationException(
          "Failed to create a Firebase Installations.",
          FirebaseInstallationException.Status.SDK_INTERNAL_ERROR);
    }
  }
}
