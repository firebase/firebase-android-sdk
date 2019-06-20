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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.segmentation.local.CustomInstallationIdCache;
import com.google.firebase.segmentation.local.CustomInstallationIdCacheEntryValue;
import com.google.firebase.segmentation.remote.SegmentationServiceClient;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FirebaseSegmentationInstrumentedTest {

  private static final String CUSTOM_INSTALLATION_ID = "123";
  private static final String FIREBASE_INSTANCE_ID = "cAAAAAAAAAA";

  private FirebaseApp firebaseApp;
  @Mock private FirebaseInstanceId firebaseInstanceId;
  @Mock private SegmentationServiceClient backendClientReturnsOk;
  @Mock private SegmentationServiceClient backendClientReturnsError;
  private CustomInstallationIdCache actualCache;
  @Mock private CustomInstallationIdCache cacheReturnsError;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId("1:123456789:android:abcdef").build());
    actualCache = new CustomInstallationIdCache(firebaseApp);

    when(backendClientReturnsOk.updateCustomInstallationId(
            anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(Tasks.forResult(SegmentationServiceClient.Code.OK));
    when(backendClientReturnsOk.clearCustomInstallationId(anyLong(), anyString(), anyString()))
        .thenReturn(Tasks.forResult(SegmentationServiceClient.Code.OK));
    when(backendClientReturnsError.updateCustomInstallationId(
            anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(Tasks.forResult(SegmentationServiceClient.Code.SERVER_INTERNAL_ERROR));
    when(backendClientReturnsError.clearCustomInstallationId(anyLong(), anyString(), anyString()))
        .thenReturn(Tasks.forResult(SegmentationServiceClient.Code.SERVER_INTERNAL_ERROR));
    when(firebaseInstanceId.getInstanceId())
        .thenReturn(
            Tasks.forResult(
                new InstanceIdResult() {
                  @NonNull
                  @Override
                  public String getId() {
                    return FIREBASE_INSTANCE_ID;
                  }

                  @NonNull
                  @Override
                  public String getToken() {
                    return "iid_token";
                  }
                }));
    when(cacheReturnsError.insertOrUpdateCacheEntry(any())).thenReturn(Tasks.forResult(false));
    when(cacheReturnsError.readCacheEntryValue()).thenReturn(null);
  }

  @After
  public void cleanUp() throws Exception {
    Tasks.await(actualCache.clear());
  }

  @Test
  public void testUpdateCustomInstallationId_CacheOk_BackendOk() throws Exception {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstanceId, actualCache, backendClientReturnsOk);

    // No exception, means success.
    assertNull(Tasks.await(firebaseSegmentation.setCustomInstallationId(CUSTOM_INSTALLATION_ID)));
    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId()).isEqualTo(CUSTOM_INSTALLATION_ID);
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo(FIREBASE_INSTANCE_ID);
    assertThat(entryValue.getCacheStatus()).isEqualTo(CustomInstallationIdCache.CacheStatus.SYNCED);
  }

  @Test
  public void testUpdateCustomInstallationId_CacheOk_BackendError() throws InterruptedException {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstanceId, actualCache, backendClientReturnsError);

    // Expect exception
    try {
      Tasks.await(firebaseSegmentation.setCustomInstallationId(CUSTOM_INSTALLATION_ID));
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.BACKEND_ERROR);
    }

    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId()).isEqualTo(CUSTOM_INSTALLATION_ID);
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo(FIREBASE_INSTANCE_ID);
    assertThat(entryValue.getCacheStatus())
        .isEqualTo(CustomInstallationIdCache.CacheStatus.PENDING_UPDATE);
  }

  @Test
  public void testUpdateCustomInstallationId_CacheError_BackendOk() throws InterruptedException {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstanceId, cacheReturnsError, backendClientReturnsOk);

    // Expect exception
    try {
      Tasks.await(firebaseSegmentation.setCustomInstallationId(CUSTOM_INSTALLATION_ID));
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
    Tasks.await(
        actualCache.insertOrUpdateCacheEntry(
            CustomInstallationIdCacheEntryValue.create(
                CUSTOM_INSTALLATION_ID,
                FIREBASE_INSTANCE_ID,
                CustomInstallationIdCache.CacheStatus.SYNCED)));
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstanceId, actualCache, backendClientReturnsOk);

    // No exception, means success.
    assertNull(Tasks.await(firebaseSegmentation.setCustomInstallationId(null)));
    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertNull(entryValue);
  }

  @Test
  public void testClearCustomInstallationId_CacheOk_BackendError() throws Exception {
    Tasks.await(
        actualCache.insertOrUpdateCacheEntry(
            CustomInstallationIdCacheEntryValue.create(
                CUSTOM_INSTALLATION_ID,
                FIREBASE_INSTANCE_ID,
                CustomInstallationIdCache.CacheStatus.SYNCED)));
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstanceId, actualCache, backendClientReturnsError);

    // Expect exception
    try {
      Tasks.await(firebaseSegmentation.setCustomInstallationId(null));
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.BACKEND_ERROR);
    }

    CustomInstallationIdCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getCustomInstallationId().isEmpty()).isTrue();
    assertThat(entryValue.getFirebaseInstanceId()).isEqualTo(FIREBASE_INSTANCE_ID);
    assertThat(entryValue.getCacheStatus())
        .isEqualTo(CustomInstallationIdCache.CacheStatus.PENDING_CLEAR);
  }

  @Test
  public void testClearCustomInstallationId_CacheError_BackendOk() throws InterruptedException {
    FirebaseSegmentation firebaseSegmentation =
        new FirebaseSegmentation(
            firebaseApp, firebaseInstanceId, cacheReturnsError, backendClientReturnsOk);

    // Expect exception
    try {
      Tasks.await(firebaseSegmentation.setCustomInstallationId(CUSTOM_INSTALLATION_ID));
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(SetCustomInstallationIdException.class);
      assertThat(((SetCustomInstallationIdException) cause).getStatus())
          .isEqualTo(SetCustomInstallationIdException.Status.CLIENT_ERROR);
    }
  }
}
