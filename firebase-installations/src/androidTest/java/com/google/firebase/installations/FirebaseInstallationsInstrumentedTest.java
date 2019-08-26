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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_APP_ID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_FID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_PROJECT_ID;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_REFRESH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_TOKEN_EXPIRATION_TIMESTAMP;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.local.PersistedFid;
import com.google.firebase.installations.local.PersistedFidEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
  private Executor executor;
  private PersistedFid persistedFid;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsOk;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsError;
  @Mock private PersistedFid persistedFidReturnsError;
  @Mock private Utils mockUtils;

  @Before
  public void setUp() throws FirebaseInstallationServiceException {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    executor = new ThreadPoolExecutor(0, 2, 30L, TimeUnit.SECONDS, new SynchronousQueue<>());
    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey("api_key")
                .build());
    persistedFid = new PersistedFid(firebaseApp);
    when(backendClientReturnsOk.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            InstallationResponse.builder()
                .setName("/projects/" + TEST_PROJECT_ID + "/installations/" + TEST_FID_1)
                .setRefreshToken(TEST_REFRESH_TOKEN)
                .setAuthToken(
                    InstallationTokenResult.builder()
                        .setToken(TEST_AUTH_TOKEN)
                        .setTokenExpirationTimestampMillis(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                        .build())
                .build());
    when(backendClientReturnsError.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new FirebaseInstallationServiceException(
                "SDK Error", FirebaseInstallationServiceException.Status.SERVER_ERROR));
    when(persistedFidReturnsError.insertOrUpdatePersistedFidEntry(any())).thenReturn(false);
    when(persistedFidReturnsError.readPersistedFidEntryValue()).thenReturn(null);
    when(mockUtils.createRandomFid()).thenReturn(TEST_FID_1);
  }

  @After
  public void cleanUp() throws Exception {
    persistedFid.clear();
  }

  @Test
  public void testGetId_PersistedFidOk_BackendOk() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    // No exception, means success.
    assertWithMessage("getId Task fails.")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(entryValue.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration status doesn't match")
        .that(entryValue.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.PENDING);

    // Waiting for Task that registers FID on the FIS Servers
    Thread.sleep(500);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(updatedFidEntry.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration status doesn't match")
        .that(updatedFidEntry.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.REGISTERED);
  }

  @Test
  public void testGetId_multipleCalls_sameFIDReturned() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    // No exception, means success.
    assertWithMessage("getId Task fails.")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(entryValue.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration status doesn't match")
        .that(entryValue.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.PENDING);

    Tasks.await(firebaseInstallations.getId());

    // Waiting for Task that registers FID on the FIS Servers
    Thread.sleep(500);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(updatedFidEntry.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration status doesn't match")
        .that(updatedFidEntry.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.REGISTERED);
  }

  @Test
  public void testGetId_PersistedFidOk_BackendError() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            executor, firebaseApp, backendClientReturnsError, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.getId());

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(entryValue.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration Fid doesn't match")
        .that(entryValue.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.PENDING);

    // Waiting for Task that registers FID on the FIS Servers
    Thread.sleep(500);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(updatedFidEntry.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration Fid doesn't match")
        .that(updatedFidEntry.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.REGISTER_ERROR);
  }

  @Test
  public void testGetId_PersistedFidError_BackendOk() throws InterruptedException {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            executor, firebaseApp, backendClientReturnsOk, persistedFidReturnsError, mockUtils);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.getId());
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertWithMessage("Exception class doesn't match")
          .that(cause)
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) cause).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.CLIENT_ERROR);
    }
  }
}
