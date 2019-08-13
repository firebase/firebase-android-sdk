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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.local.FiidCache;
import com.google.firebase.installations.local.FiidCacheEntryValue;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
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
public class FirebaseInstallationsInstrumentedTest {
  private FirebaseApp firebaseApp;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsOk;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsError;
  private FiidCache actualCache;
  @Mock private FiidCache cacheReturnsError;

  @Before
  public void setUp() throws FirebaseInstallationServiceException {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:123456789:android:abcdef")
                .setProjectId("project-id")
                .setApiKey("api_key")
                .build());
    actualCache = new FiidCache(firebaseApp);
    when(backendClientReturnsOk.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            InstallationResponse.builder()
                .setName("/projects/123456789/installations/fid")
                .setRefreshToken("refresh-token")
                .setAuthToken(
                    InstallationTokenResult.builder()
                        .setToken("auth-token")
                        .setTokenExpirationTimestampMillis(1000L)
                        .build())
                .build());
    when(backendClientReturnsError.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new FirebaseInstallationServiceException(
                "SDK Error", FirebaseInstallationServiceException.Status.SERVER_ERROR));
    when(cacheReturnsError.insertOrUpdateCacheEntry(any())).thenReturn(false);
    when(cacheReturnsError.readCacheEntryValue()).thenReturn(null);
  }

  @After
  public void cleanUp() throws Exception {
    actualCache.clear();
  }

  @Test
  public void testCreateFirebaseInstallation_CacheOk_BackendOk() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(firebaseApp, actualCache, backendClientReturnsOk);

    // No exception, means success.
    assertThat(Tasks.await(firebaseInstallations.getId())).isNotEmpty();
    FiidCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getFirebaseInstallationId()).isNotEmpty();
    assertThat(entryValue.getCacheStatus()).isEqualTo(FiidCache.CacheStatus.REGISTERED);
  }

  @Test
  public void testCreateFirebaseInstallation_CacheOk_BackendError() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(firebaseApp, actualCache, backendClientReturnsError);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.getId());
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(FirebaseInstallationsException.class);
      assertThat(((FirebaseInstallationsException) cause).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }

    FiidCacheEntryValue entryValue = actualCache.readCacheEntryValue();
    assertThat(entryValue.getFirebaseInstallationId()).isNotEmpty();
    assertThat(entryValue.getCacheStatus()).isEqualTo(FiidCache.CacheStatus.REGISTER_ERROR);
  }

  @Test
  public void testCreateFirebaseInstallation_CacheError_BackendOk() throws InterruptedException {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(firebaseApp, cacheReturnsError, backendClientReturnsOk);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.getId());
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(FirebaseInstallationsException.class);
      assertThat(((FirebaseInstallationsException) cause).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.CLIENT_ERROR);
    }
  }
}
