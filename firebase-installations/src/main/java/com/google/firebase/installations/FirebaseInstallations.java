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
import com.google.firebase.installations.local.FiidCache;
import com.google.firebase.installations.local.FiidCacheEntryValue;
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
  private final FiidCache localCache;
  private final Executor executor;

  /** package private constructor. */
  FirebaseInstallations(FirebaseApp firebaseApp) {
    this(firebaseApp, new FiidCache(firebaseApp), new FirebaseInstallationServiceClient());
  }

  FirebaseInstallations(
      FirebaseApp firebaseApp,
      FiidCache localCache,
      FirebaseInstallationServiceClient serviceClient) {
    this.firebaseApp = firebaseApp;
    this.serviceClient = serviceClient;
    this.executor = Executors.newFixedThreadPool(6);
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
   * Create firebase installation id for the {@link FirebaseApp} on FIS Servers and client side
   * cache.
   *
   * <pre>
   *     The workflow is:
   *         check if cache empty or cache status is REGISTER_ERROR
   *                                 |
   *                           create random fid
   *                                 |
   *               update cache with cache status UNREGISTERED
   *                                 |
   *                           send http request to backend
   *                                 |                  |
   *  on success: set cache entry status to REGISTERED  |
   *                                 |                  |
   *                                 |         on failure: set cache entry status to REGISTER_ERROR
   *                                 |                     |
   *                                return          throw exception
   *
   *
   *                      else if cached FID exists
   *                             |            |
   *     if cache status is UNREGISTERED      |
   *                  |                   return cached FID
   *     send http request to backend
   *               |               |
   *  on success: set cache        \
   *  entry status to REGISTERED   \
   *                |              \
   *                |         on failure: set cache entry status to REGISTER_ERROR
   *                |                     |
   *              return          throw exception
   * </pre>
   */
  @WorkerThread
  private String createFirebaseInstallationId() throws FirebaseInstallationsException {

    FiidCacheEntryValue cacheEntryValue = localCache.readCacheEntryValue();

    if (cacheEntryValue == null
        || cacheEntryValue.getCacheStatus() == FiidCache.CacheStatus.REGISTER_ERROR) {
      String fid = Utils.createRandomFid();

      boolean firstUpdateCacheResult =
          localCache.insertOrUpdateCacheEntry(
              FiidCacheEntryValue.create(fid, FiidCache.CacheStatus.UNREGISTERED, "", "", 0, 0));

      if (!firstUpdateCacheResult) {
        throw new FirebaseInstallationsException(
            "Failed to update client side cache.",
            FirebaseInstallationsException.Status.CLIENT_ERROR);
      }

      registerAndSaveFID(fid);

      return fid;
    } else if (!cacheEntryValue.getFirebaseInstallationId().isEmpty()) {

      if (cacheEntryValue.getCacheStatus() == FiidCache.CacheStatus.UNREGISTERED) {
        registerAndSaveFID(cacheEntryValue.getFirebaseInstallationId());
      }

      return cacheEntryValue.getFirebaseInstallationId();
    }
    return null;
  }

  /**
   * Registers the created FID with FIS Servers if the Network is available and update the cache.
   */
  private void registerAndSaveFID(String fid) throws FirebaseInstallationsException {
    try {
      if (Utils.isNetworkAvailable(firebaseApp.getApplicationContext())) {
        long creationTime = Utils.getCurrentTimeInSeconds();

        InstallationResponse installationResponse =
            serviceClient.createFirebaseInstallation(
                firebaseApp.getOptions().getApiKey(),
                firebaseApp.getOptions().getProjectId(),
                fid,
                getApplicationId());

        localCache.insertOrUpdateCacheEntry(
            FiidCacheEntryValue.create(
                fid,
                FiidCache.CacheStatus.REGISTERED,
                installationResponse.getAuthToken().getToken(),
                installationResponse.getRefreshToken(),
                creationTime,
                installationResponse.getAuthToken().getTokenExpirationTimestampMillis()));
      }

    } catch (FirebaseInstallationServiceException exception) {
      localCache.insertOrUpdateCacheEntry(
          FiidCacheEntryValue.create(fid, FiidCache.CacheStatus.REGISTER_ERROR, "", "", 0, 0));
      throw new FirebaseInstallationsException(
          exception.getMessage(), FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }
}
