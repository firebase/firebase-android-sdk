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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.segmentation.local.CustomInstallationIdCache;
import com.google.firebase.segmentation.local.CustomInstallationIdCacheEntryValue;
import com.google.firebase.segmentation.remote.SegmentationServiceClient;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FirebaseSegmentationTest {
  private static final String CUSTOM_INSTALLATION_ID = "123";
  private static final String FIREBASE_INSTALLATION_ID = "fid_is_better_than_iid";
  private static final String FIREBASE_INSTALLATION_ID_TOKEN = "fis_token";

  private FirebaseApp firebaseApp;
  @Mock private FirebaseInstallationsApi firebaseInstallationsApi;
  @Mock private SegmentationServiceClient backendClientReturnsOk;
  @Mock private SegmentationServiceClient backendClientReturnsError;

  private CustomInstallationIdCache actualCache;
  @Mock private CustomInstallationIdCache cacheReturnsError;

  private ExecutorService taskExecutor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:123456789:android:abcdef")
                .setApiKey("api_key")
                .build());
    actualCache = new CustomInstallationIdCache(firebaseApp);

    when(backendClientReturnsOk.updateCustomInstallationId(
            anyLong(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(SegmentationServiceClient.Code.OK);
    when(backendClientReturnsOk.clearCustomInstallationId(
            anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(SegmentationServiceClient.Code.OK);
    when(backendClientReturnsError.updateCustomInstallationId(
            anyLong(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(SegmentationServiceClient.Code.SERVER_ERROR);
    when(backendClientReturnsError.clearCustomInstallationId(
            anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(SegmentationServiceClient.Code.SERVER_ERROR);
    when(firebaseInstallationsApi.getId()).thenReturn(Tasks.forResult(FIREBASE_INSTALLATION_ID));
    when(firebaseInstallationsApi.getToken(Mockito.anyBoolean()))
        .thenReturn(
            Tasks.forResult(
                new InstallationTokenResult() {
                  @NonNull
                  @Override
                  public String getToken() {
                    return FIREBASE_INSTALLATION_ID_TOKEN;
                  }

                  @NonNull
                  @Override
                  public long getTokenExpirationTimestamp() {
                    return 0;
                  }

                  @NonNull
                  @Override
                  public long getTokenCreationTimestamp() {
                    return 0;
                  }

                  @NonNull
                  @Override
                  public Builder toBuilder() {
                    return null;
                  }
                }));
    when(cacheReturnsError.insertOrUpdateCacheEntry(any())).thenReturn(false);
    when(cacheReturnsError.readCacheEntryValue()).thenReturn(null);

    taskExecutor = new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
  }

  @After
  public void cleanUp() throws Exception {
    actualCache.clear();
  }

  @Test
  public void testUpdateCustomInstallationId_CacheOk_BackendOk() throws Exception {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstallationsApi, actualCache, backendClientReturnsOk);

    // No exception, means success.
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    firebaseSegmentation
        .setCustomInstallationId(CUSTOM_INSTALLATION_ID)
        .addOnCompleteListener(taskExecutor, onCompleteListener);
    assertNull(onCompleteListener.await());
    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId()).isEqualTo(CUSTOM_INSTALLATION_ID);
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo(FIREBASE_INSTALLATION_ID);
    assertThat(entryValue.getCacheStatus()).isEqualTo(CustomInstallationIdCache.CacheStatus.SYNCED);
  }

  @Test
  public void testUpdateCustomInstallationId_CacheOk_BackendError_Retryable()
      throws InterruptedException {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstallationsApi, actualCache, backendClientReturnsError);

    // Expect exception
    try {
      TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
      firebaseSegmentation
          .setCustomInstallationId(CUSTOM_INSTALLATION_ID)
          .addOnCompleteListener(taskExecutor, onCompleteListener);
      onCompleteListener.await();
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.BACKEND_ERROR);
    }

    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId()).isEqualTo(CUSTOM_INSTALLATION_ID);
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo(FIREBASE_INSTALLATION_ID);
    assertThat(entryValue.getCacheStatus())
        .isEqualTo(CustomInstallationIdCache.CacheStatus.PENDING_UPDATE);
  }

  @Test
  public void testUpdateCustomInstallationId_CacheOk_BackendError_NotRetryable()
      throws InterruptedException {
    when(backendClientReturnsError.updateCustomInstallationId(
            anyLong(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(SegmentationServiceClient.Code.CONFLICT);
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstallationsApi, actualCache, backendClientReturnsError);

    // Expect exception
    try {
      TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
      firebaseSegmentation
          .setCustomInstallationId(CUSTOM_INSTALLATION_ID)
          .addOnCompleteListener(taskExecutor, onCompleteListener);
      onCompleteListener.await();
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.DUPLICATED_CUSTOM_INSTALLATION_ID);
    }

    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue).isNull();
  }

  @Test
  public void testUpdateCustomInstallationId_CacheError_BackendOk() throws InterruptedException {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstallationsApi, cacheReturnsError, backendClientReturnsOk);

    // Expect exception
    try {
      TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
      firebaseSegmentation
          .setCustomInstallationId(CUSTOM_INSTALLATION_ID)
          .addOnCompleteListener(taskExecutor, onCompleteListener);
      onCompleteListener.await();
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.CLIENT_ERROR);
    }
  }

  @Test
  public void testClearCustomInstallationId_CacheOk_BackendOk() throws Exception {
    actualCache.insertOrUpdateCacheEntry(
        CustomInstallationIdCacheEntryValue.create(
            CUSTOM_INSTALLATION_ID,
            FIREBASE_INSTALLATION_ID,
            CustomInstallationIdCache.CacheStatus.SYNCED));
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstallationsApi, actualCache, backendClientReturnsOk);

    // No exception, means success.
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    firebaseSegmentation
        .setCustomInstallationId(null)
        .addOnCompleteListener(taskExecutor, onCompleteListener);
    assertNull(onCompleteListener.await());
    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertNull(entryValue);
  }

  @Test
  public void testClearCustomInstallationId_CacheOk_BackendError() throws Exception {
    actualCache.insertOrUpdateCacheEntry(
        CustomInstallationIdCacheEntryValue.create(
            CUSTOM_INSTALLATION_ID,
            FIREBASE_INSTALLATION_ID,
            CustomInstallationIdCache.CacheStatus.SYNCED));
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstallationsApi, actualCache, backendClientReturnsError);

    // Expect exception
    try {
      TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
      firebaseSegmentation
          .setCustomInstallationId(null)
          .addOnCompleteListener(taskExecutor, onCompleteListener);
      onCompleteListener.await();
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.BACKEND_ERROR);
    }

    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId().isEmpty()).isTrue();
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo(FIREBASE_INSTALLATION_ID);
    assertThat(entryValue.getCacheStatus())
        .isEqualTo(CustomInstallationIdCache.CacheStatus.PENDING_CLEAR);
  }

  @Test
  public void testClearCustomInstallationId_CacheError_BackendOk() throws InterruptedException {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstallationsApi, cacheReturnsError, backendClientReturnsOk);

    // Expect exception
    try {
      TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
      firebaseSegmentation
          .setCustomInstallationId(null)
          .addOnCompleteListener(taskExecutor, onCompleteListener);
      onCompleteListener.await();
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.CLIENT_ERROR);
    }
  }
}
