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
import static com.google.firebase.installations.FisAndroidTestConstants.DEFAULT_PERSISTED_FID_ENTRY;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_API_KEY;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_APP_ID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN_3;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN_4;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_FID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_PROJECT_ID;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_REFRESH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_TOKEN_EXPIRATION_TIMESTAMP;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_TOKEN_EXPIRATION_TIMESTAMP_2;
import static com.google.firebase.installations.local.PersistedFidEntrySubject.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.local.PersistedFid;
import com.google.firebase.installations.local.PersistedFid.RegistrationStatus;
import com.google.firebase.installations.local.PersistedFidEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
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
import org.mockito.AdditionalAnswers;
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
  private ExecutorService executor;
  private PersistedFid persistedFid;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsOk;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsError;
  @Mock private PersistedFid persistedFidReturnsError;
  @Mock private Utils mockUtils;
  @Mock private Clock mockClock;
  @Mock private PersistedFid mockPersistedFid;
  @Mock private FirebaseInstallationServiceClient mockClient;

  private static final PersistedFidEntry REGISTERED_FID_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken(TEST_AUTH_TOKEN)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_1)
          .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
          .setRegistrationStatus(RegistrationStatus.REGISTERED)
          .build();

  private static final PersistedFidEntry EXPIRED_AUTH_TOKEN_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken(TEST_AUTH_TOKEN)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_1)
          .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP_2)
          .setRegistrationStatus(RegistrationStatus.REGISTERED)
          .build();

  private static final PersistedFidEntry UNREGISTERED_FID_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken("")
          .setRefreshToken("")
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_1)
          .setExpiresInSecs(0)
          .setRegistrationStatus(RegistrationStatus.UNREGISTERED)
          .build();

  private static final PersistedFidEntry UPDATED_AUTH_TOKEN_FID_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken(TEST_AUTH_TOKEN_2)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_2)
          .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
          .setRegistrationStatus(RegistrationStatus.REGISTERED)
          .build();

  @Before
  public void setUp() throws FirebaseInstallationServiceException {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    executor = new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
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
                        .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                        .build())
                .build());
    when(backendClientReturnsOk.generateAuthToken(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            InstallationTokenResult.builder()
                .setToken(TEST_AUTH_TOKEN_2)
                .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                .build());
    when(backendClientReturnsError.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new FirebaseInstallationServiceException(
                "SDK Error", FirebaseInstallationServiceException.Status.SERVER_ERROR));
    when(persistedFidReturnsError.insertOrUpdatePersistedFidEntry(any())).thenReturn(false);
    when(persistedFidReturnsError.readPersistedFidEntryValue())
        .thenReturn(DEFAULT_PERSISTED_FID_ENTRY);
    when(mockUtils.createRandomFid()).thenReturn(TEST_FID_1);
    when(mockClock.currentTimeMillis()).thenReturn(TEST_CREATION_TIMESTAMP_1);
    // Mocks success on FIS deletion
    doNothing()
        .when(backendClientReturnsOk)
        .deleteFirebaseInstallation(anyString(), anyString(), anyString(), anyString());
    // Mocks server error on FIS deletion
    doThrow(
            new FirebaseInstallationServiceException(
                "Server Error", FirebaseInstallationServiceException.Status.SERVER_ERROR))
        .when(backendClientReturnsError)
        .deleteFirebaseInstallation(anyString(), anyString(), anyString(), anyString());
  }

  @After
  public void cleanUp() throws Exception {
    persistedFid.clear();
  }

  @Test
  public void testGetId_PersistedFidOk_BackendOk() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    // No exception, means success.
    assertWithMessage("getId Task failed")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).hasFid(TEST_FID_1);

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertThat(updatedFidEntry).hasFid(TEST_FID_1);
    assertThat(updatedFidEntry).hasRegistrationStatus(RegistrationStatus.REGISTERED);
  }

  @Test
  public void testGetId_multipleCalls_sameFIDReturned() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    // No exception, means success.
    assertWithMessage("getId Task failed")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).hasFid(TEST_FID_1);

    Tasks.await(firebaseInstallations.getId());

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertThat(updatedFidEntry).hasFid(TEST_FID_1);
    assertThat(updatedFidEntry).hasRegistrationStatus(RegistrationStatus.REGISTERED);
  }

  @Test
  public void testGetId_PersistedFidOk_BackendError() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsError, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.getId());

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).hasFid(TEST_FID_1);

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertThat(updatedFidEntry).hasFid(TEST_FID_1);
    assertThat(updatedFidEntry).hasRegistrationStatus(RegistrationStatus.REGISTER_ERROR);
  }

  @Test
  public void testGetId_fidRegistrationUncheckedException_statusUpdated() throws Exception {
    // Mocking unchecked exception on FIS createFirebaseInstallation
    when(mockClient.createFirebaseInstallation(anyString(), anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              throw new InterruptedException();
            });

    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, mockClient, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.getId());

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).hasFid(TEST_FID_1);
    assertThat(entryValue).hasRegistrationStatus(RegistrationStatus.PENDING);

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // Validate that registration status is REGISTER_ERROR
    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertThat(updatedFidEntry).hasFid(TEST_FID_1);
    assertThat(updatedFidEntry).hasRegistrationStatus(RegistrationStatus.REGISTER_ERROR);
  }

  @Test
  public void testGetId_expiredAuthTokenUncheckedException_statusUpdated() throws Exception {
    // Update local storage with fid entry that has auth token expired.
    persistedFid.insertOrUpdatePersistedFidEntry(EXPIRED_AUTH_TOKEN_ENTRY);
    // Mocking unchecked exception on FIS generateAuthToken
    when(mockClient.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              throw new InterruptedException();
            });

    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, mockClient, persistedFid, mockUtils);

    assertWithMessage("getId Task failed")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).hasFid(TEST_FID_1);
    assertThat(entryValue).hasRegistrationStatus(RegistrationStatus.PENDING);

    // Waiting for Task that generates auth token with the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // Validate that registration status is REGISTER_ERROR
    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertThat(updatedFidEntry).hasFid(TEST_FID_1);
    assertThat(updatedFidEntry).hasRegistrationStatus(RegistrationStatus.REGISTER_ERROR);
  }

  @Test
  public void testGetId_PersistedFidError_BackendOk() throws InterruptedException {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock,
            executor,
            firebaseApp,
            backendClientReturnsOk,
            persistedFidReturnsError,
            mockUtils);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.getId());
      fail("Could not update local storage.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) expected.getCause()).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.CLIENT_ERROR);
    }
  }

  @Test
  public void testGetId_expiredAuthToken_refreshesAuthToken() throws Exception {
    // Update local storage with fid entry that has auth token expired.
    persistedFid.insertOrUpdatePersistedFidEntry(EXPIRED_AUTH_TOKEN_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    assertWithMessage("getId Task failed")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).hasFid(TEST_FID_1);

    // Waiting for Task that generates auth token with the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // Validate that registration is complete with updated auth token
    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(updatedFidEntry)
        .isEqualTo(UPDATED_AUTH_TOKEN_FID_ENTRY);
    verify(backendClientReturnsOk, never())
        .createFirebaseInstallation(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_APP_ID_1);
    verify(backendClientReturnsOk, times(1))
        .generateAuthToken(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testGetAuthToken_fidDoesNotExist_successful() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).hasAuthToken(TEST_AUTH_TOKEN);
  }

  @Test
  public void testGetAuthToken_PersistedFidError_failure() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock,
            executor,
            firebaseApp,
            backendClientReturnsOk,
            persistedFidReturnsError,
            mockUtils);

    // Expect exception
    try {
      Tasks.await(
          firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));
      fail("Could not update local storage.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) expected.getCause()).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }

  @Test
  public void testGetAuthToken_fidExists_successful() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(REGISTERED_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    InstallationTokenResult installationTokenResult =
        Tasks.await(
            firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN);
  }

  @Test
  public void testGetAuthToken_expiredAuthToken_fetchedNewTokenFromFIS() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(EXPIRED_AUTH_TOKEN_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    InstallationTokenResult installationTokenResult =
        Tasks.await(
            firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
  }

  @Test
  public void testGetAuthToken_unregisteredFid_fetchedNewTokenFromFIS() throws Exception {
    // Using mockPersistedFid to ensure the order of returning persistedFidEntry. This test
    // validates that getAuthToken calls getId to ensure FID registration and returns a valid auth
    // token.
    when(mockPersistedFid.readPersistedFidEntryValue())
        .thenReturn(UNREGISTERED_FID_ENTRY, REGISTERED_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    InstallationTokenResult installationTokenResult =
        Tasks.await(
            firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN);
  }

  @Test
  public void testGetAuthToken_serverError_failure() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(REGISTERED_FID_ENTRY);
    when(backendClientReturnsError.generateAuthToken(
            anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new FirebaseInstallationServiceException(
                "Server Error", FirebaseInstallationServiceException.Status.SERVER_ERROR));
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock,
            executor,
            firebaseApp,
            backendClientReturnsError,
            mockPersistedFid,
            mockUtils);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.getAuthToken(FirebaseInstallationsApi.FORCE_REFRESH));
      fail("getAuthToken() failed due to Server Error.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) expected.getCause()).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }

  @Test
  public void testGetAuthToken_multipleCallsDoNotForceRefresh_fetchedNewTokenOnce()
      throws Exception {
    // Update local storage with fid entry that has auth token expired. When 2 tasks are triggered
    // simultaneously, Task2 waits for Task1 to complete. On Task1 completion, task2 reads  the
    // UPDATED_AUTH_TOKEN_FID_ENTRY by Task1 on execution.
    persistedFid.insertOrUpdatePersistedFidEntry(EXPIRED_AUTH_TOKEN_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    // Call getAuthToken multiple times with DO_NOT_FORCE_REFRESH option
    Task<InstallationTokenResult> task1 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH);
    Task<InstallationTokenResult> task2 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH);

    Tasks.await(Tasks.whenAllComplete(task1, task2));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task1.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task2.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
    verify(backendClientReturnsOk, times(1))
        .generateAuthToken(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testGetAuthToken_multipleCallsForceRefresh_fetchedNewTokenTwice() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(REGISTERED_FID_ENTRY);
    // Use a mock ServiceClient for network calls with delay(1000ms) to ensure first task is not
    // completed before the second task starts. Hence, we can test multiple calls to getAuthToken()
    // and verify one task waits for another task to complete.

    doAnswer(
            AdditionalAnswers.answersWithDelay(
                1000,
                (unused) ->
                    InstallationTokenResult.builder()
                        .setToken(TEST_AUTH_TOKEN_3)
                        .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                        .build()))
        .doAnswer(
            AdditionalAnswers.answersWithDelay(
                1000,
                (unused) ->
                    InstallationTokenResult.builder()
                        .setToken(TEST_AUTH_TOKEN_4)
                        .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                        .build()))
        .when(backendClientReturnsOk)
        .generateAuthToken(anyString(), anyString(), anyString(), anyString());

    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    // Call getAuthToken multiple times with FORCE_REFRESH option.
    Task<InstallationTokenResult> task1 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.FORCE_REFRESH);
    Task<InstallationTokenResult> task2 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.FORCE_REFRESH);
    Tasks.await(Tasks.whenAllComplete(task1, task2));

    // As we cannot ensure which task got executed first, verifying with both expected values
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task1.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_3);
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task2.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_4);
    verify(backendClientReturnsOk, times(2))
        .generateAuthToken(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testDelete_registeredFID_successful() throws Exception {
    // Update local storage with a registered fid entry
    persistedFid.insertOrUpdatePersistedFidEntry(REGISTERED_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.delete());

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).isEqualTo(DEFAULT_PERSISTED_FID_ENTRY);
    verify(backendClientReturnsOk, times(1))
        .deleteFirebaseInstallation(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testDelete_unregisteredFID_successful() throws Exception {
    // Update local storage with a unregistered fid entry
    persistedFid.insertOrUpdatePersistedFidEntry(UNREGISTERED_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.delete());

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).isEqualTo(DEFAULT_PERSISTED_FID_ENTRY);
    verify(backendClientReturnsOk, never())
        .deleteFirebaseInstallation(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testDelete_emptyPersistedFidEntry_successful() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.delete());

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertThat(entryValue).isEqualTo(DEFAULT_PERSISTED_FID_ENTRY);
    verify(backendClientReturnsOk, never())
        .deleteFirebaseInstallation(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testDelete_serverError_failure() throws Exception {
    // Update local storage with a registered fid entry
    persistedFid.insertOrUpdatePersistedFidEntry(REGISTERED_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsError, persistedFid, mockUtils);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.delete());
      fail("delete() failed due to Server Error.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) expected.getCause()).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
      PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
      assertWithMessage("Persisted Fid Entry doesn't match")
          .that(entryValue)
          .isEqualTo(REGISTERED_FID_ENTRY);
    }
  }
}
