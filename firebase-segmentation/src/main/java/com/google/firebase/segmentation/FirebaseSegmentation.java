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
import androidx.annotation.RestrictTo;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.segmentation.SetCustomInstallationIdException.Status;
import com.google.firebase.segmentation.local.CustomInstallationIdCache;
import com.google.firebase.segmentation.local.CustomInstallationIdCacheEntryValue;
import com.google.firebase.segmentation.remote.SegmentationServiceClient;
import com.google.firebase.segmentation.remote.SegmentationServiceClient.Code;
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
    this.firebaseApp = firebaseApp;
    this.firebaseInstanceId = FirebaseInstanceId.getInstance(firebaseApp);
    this.localCache = new CustomInstallationIdCache(firebaseApp);
    this.backendServiceClient = new SegmentationServiceClient();
    this.executor = Executors.newFixedThreadPool(4);
  }

  @RestrictTo(RestrictTo.Scope.TESTS)
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
      return clearCustomInstallationId();
    }
    return updateCustomInstallationId(customInstallationId);
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
  private Task<Void> updateCustomInstallationId(String customInstallationId) {
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();

    executor.execute(
        () -> {
          CustomInstallationIdCacheEntryValue cacheEntryValue = localCache.readCacheEntryValue();
          if (cacheEntryValue != null
              && cacheEntryValue.getCustomInstallationId().equals(customInstallationId)
              && cacheEntryValue.getCacheStatus() == CustomInstallationIdCache.CacheStatus.SYNCED) {
            // If the given custom installation id matches up the cached
            // value, there's no need to update.
            taskCompletionSource.setResult(null);
            return;
          }

          InstanceIdResult instanceIdResult;
          try {
            instanceIdResult = Tasks.await(firebaseInstanceId.getInstanceId());
          } catch (Exception e) {
            taskCompletionSource.setException(
                new SetCustomInstallationIdException(
                    "Failed to get Firebase instance id", Status.CLIENT_ERROR));
            return;
          }

          boolean firstUpdateCacheResult =
              localCache.insertOrUpdateCacheEntry(
                  CustomInstallationIdCacheEntryValue.create(
                      customInstallationId,
                      instanceIdResult.getId(),
                      CustomInstallationIdCache.CacheStatus.PENDING_UPDATE));

          if (!firstUpdateCacheResult) {
            taskCompletionSource.setException(
                new SetCustomInstallationIdException(
                    "Failed to update client side cache", Status.CLIENT_ERROR));
            return;
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
              taskCompletionSource.setException(
                  new SetCustomInstallationIdException(Status.CLIENT_ERROR));
              return;
            case CONFLICT:
              taskCompletionSource.setException(
                  new SetCustomInstallationIdException(Status.DUPLICATED_CUSTOM_INSTALLATION_ID));
              return;
            default:
              taskCompletionSource.setException(
                  new SetCustomInstallationIdException(Status.BACKEND_ERROR));
              return;
          }

          if (finalUpdateCacheResult) {
            taskCompletionSource.setResult(null);
          } else {
            taskCompletionSource.setException(
                new SetCustomInstallationIdException(
                    "Failed to update client side cache", Status.CLIENT_ERROR));
          }
        });
    return taskCompletionSource.getTask();
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
  private Task<Void> clearCustomInstallationId() {

    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();

    executor.execute(
        () -> {
          InstanceIdResult instanceIdResult;
          try {
            instanceIdResult = Tasks.await(firebaseInstanceId.getInstanceId());
          } catch (Exception e) {
            taskCompletionSource.setException(
                new SetCustomInstallationIdException(
                    "Failed to get Firebase instance id", Status.CLIENT_ERROR));
            return;
          }

          boolean firstUpdateCacheResult =
              localCache.insertOrUpdateCacheEntry(
                  CustomInstallationIdCacheEntryValue.create(
                      "",
                      instanceIdResult.getId(),
                      CustomInstallationIdCache.CacheStatus.PENDING_CLEAR));

          if (!firstUpdateCacheResult) {
            taskCompletionSource.setException(
                new SetCustomInstallationIdException(
                    "Failed to update client side cache", Status.CLIENT_ERROR));
            return;
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
              taskCompletionSource.setException(
                  new SetCustomInstallationIdException(Status.CLIENT_ERROR));
              return;
            default:
              taskCompletionSource.setException(
                  new SetCustomInstallationIdException(Status.BACKEND_ERROR));
              return;
          }

          if (finalUpdateCacheResult) {
            taskCompletionSource.setResult(null);
          } else {
            taskCompletionSource.setException(
                new SetCustomInstallationIdException(
                    "Failed to update client side cache", Status.CLIENT_ERROR));
          }
        });
    return taskCompletionSource.getTask();
  }
}
