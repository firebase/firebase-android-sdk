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

package com.google.firebase.segmentation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.segmentation.SetCustomInstallationIdException.Status;
import com.google.firebase.segmentation.local.CustomInstallationIdCache;
import com.google.firebase.segmentation.local.CustomInstallationIdCacheEntryValue;
import com.google.firebase.segmentation.remote.SegmentationServiceClient;
import com.google.firebase.segmentation.remote.SegmentationServiceClient.Code;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Entry point of Firebase Segmentation SDK. */
public class FirebaseSegmentation {

  private final FirebaseApp firebaseApp;
  private final FirebaseInstanceId firebaseInstanceId;
  private final CustomInstallationIdCache localCache;
  private final SegmentationServiceClient backendServiceClient;
  private final Executor executor;

  FirebaseSegmentation(FirebaseApp firebaseApp) {
    this(
        firebaseApp,
        FirebaseInstanceId.getInstance(firebaseApp),
        new CustomInstallationIdCache(firebaseApp),
        new SegmentationServiceClient());
  }

  FirebaseSegmentation(
      FirebaseApp firebaseApp,
      FirebaseInstanceId firebaseInstanceId,
      CustomInstallationIdCache localCache,
      SegmentationServiceClient backendServiceClient) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstanceId = firebaseInstanceId;
    this.localCache = localCache;
    this.backendServiceClient = backendServiceClient;
    this.executor = Executors.newFixedThreadPool(4);
  }

  /**
   * Returns the {@link FirebaseSegmentation} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseSegmentation} instance
   */
  @NonNull
  public static FirebaseSegmentation getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link FirebaseSegmentation} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link FirebaseSegmentation} instance
   */
  @NonNull
  public static FirebaseSegmentation getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return app.get(FirebaseSegmentation.class);
  }

  @NonNull
  public synchronized Task<Void> setCustomInstallationId(@Nullable String customInstallationId) {
    if (customInstallationId == null) {
      return Tasks.call(executor, () -> clearCustomInstallationId());
    }
    return Tasks.call(executor, () -> updateCustomInstallationId(customInstallationId));
  }

  /**
   * Update custom installation id of the {@link FirebaseApp} on Firebase segmentation backend and
   * client side cache.
   *
   * <pre>
   *     The workflow is:
   *         check diff against cache or cache status is not SYNCED
   *                                 |
   *                  get Firebase instance id and token
   *                      |                       |
   *                      |      update cache with cache status PENDING_UPDATE
   *                      |                       |
   *                    send http request to backend
   *                                 |
   *              on success: set cache entry status to SYNCED
   *                                 |
   *                               return
   * </pre>
   */
  @WorkerThread
  private Void updateCustomInstallationId(String customInstallationId)
      throws SetCustomInstallationIdException {
    CustomInstallationIdCacheEntryValue cacheEntryValue = localCache.readCacheEntryValue();
    if (cacheEntryValue != null
        && cacheEntryValue.getCustomInstallationId().equals(customInstallationId)
        && cacheEntryValue.getCacheStatus() == CustomInstallationIdCache.CacheStatus.SYNCED) {
      // If the given custom installation id matches up the cached
      // value, there's no need to update.
      return null;
    }

    InstanceIdResult instanceIdResult;
    try {
      instanceIdResult = Tasks.await(firebaseInstanceId.getInstanceId());
    } catch (ExecutionException | InterruptedException e) {
      throw new SetCustomInstallationIdException(
          "Failed to get Firebase instance id", Status.CLIENT_ERROR);
    }

    boolean firstUpdateCacheResult =
        localCache.insertOrUpdateCacheEntry(
            CustomInstallationIdCacheEntryValue.create(
                customInstallationId,
                instanceIdResult.getId(),
                CustomInstallationIdCache.CacheStatus.PENDING_UPDATE));

    if (!firstUpdateCacheResult) {
      throw new SetCustomInstallationIdException(
          "Failed to update client side cache", Status.CLIENT_ERROR);
    }

    // Start requesting backend when first cache updae is done.
    String iid = instanceIdResult.getId();
    String iidToken = instanceIdResult.getToken();
    Code backendRequestResult =
        backendServiceClient.updateCustomInstallationId(
            Utils.getProjectNumberFromAppId(firebaseApp.getOptions().getApplicationId()),
            firebaseApp.getOptions().getApiKey(),
            customInstallationId,
            iid,
            iidToken);

    boolean finalUpdateCacheResult;
    switch (backendRequestResult) {
      case OK:
        finalUpdateCacheResult =
            localCache.insertOrUpdateCacheEntry(
                CustomInstallationIdCacheEntryValue.create(
                    customInstallationId,
                    instanceIdResult.getId(),
                    CustomInstallationIdCache.CacheStatus.SYNCED));
        break;
      case HTTP_CLIENT_ERROR:
        throw new SetCustomInstallationIdException(Status.CLIENT_ERROR);
      case CONFLICT:
        throw new SetCustomInstallationIdException(Status.DUPLICATED_CUSTOM_INSTALLATION_ID);
      default:
        throw new SetCustomInstallationIdException(Status.BACKEND_ERROR);
    }

    if (finalUpdateCacheResult) {
      return null;
    } else {
      throw new SetCustomInstallationIdException(
          "Failed to update client side cache", Status.CLIENT_ERROR);
    }
  }

  /**
   * Clear custom installation id of the {@link FirebaseApp} on Firebase segmentation backend and
   * client side cache.
   *
   * <pre>
   *     The workflow is:
   *                  get Firebase instance id and token
   *                      |                      |
   *                      |    update cache with cache status PENDING_CLEAR
   *                      |                      |
   *                    send http request to backend
   *                                  |
   *                   on success: delete cache entry
   *                                  |
   *                               return
   * </pre>
   */
  @WorkerThread
  private Void clearCustomInstallationId() throws SetCustomInstallationIdException {
    InstanceIdResult instanceIdResult;
    try {
      instanceIdResult = Tasks.await(firebaseInstanceId.getInstanceId());
    } catch (ExecutionException | InterruptedException e) {
      throw new SetCustomInstallationIdException(
          "Failed to get Firebase instance id", Status.CLIENT_ERROR);
    }

    boolean firstUpdateCacheResult =
        localCache.insertOrUpdateCacheEntry(
            CustomInstallationIdCacheEntryValue.create(
                "", instanceIdResult.getId(), CustomInstallationIdCache.CacheStatus.PENDING_CLEAR));

    if (!firstUpdateCacheResult) {
      throw new SetCustomInstallationIdException(
          "Failed to update client side cache", Status.CLIENT_ERROR);
    }

    String iid = instanceIdResult.getId();
    String iidToken = instanceIdResult.getToken();
    Code backendRequestResult =
        backendServiceClient.clearCustomInstallationId(
            Utils.getProjectNumberFromAppId(firebaseApp.getOptions().getApplicationId()),
            firebaseApp.getOptions().getApiKey(),
            iid,
            iidToken);

    boolean finalUpdateCacheResult;
    switch (backendRequestResult) {
      case OK:
        finalUpdateCacheResult = localCache.clear();
        break;
      case HTTP_CLIENT_ERROR:
        throw new SetCustomInstallationIdException(Status.CLIENT_ERROR);
      default:
        throw new SetCustomInstallationIdException(Status.BACKEND_ERROR);
    }

    if (finalUpdateCacheResult) {
      return null;
    } else {
      throw new SetCustomInstallationIdException(
          "Failed to update client side cache", Status.CLIENT_ERROR);
    }
  }
}
