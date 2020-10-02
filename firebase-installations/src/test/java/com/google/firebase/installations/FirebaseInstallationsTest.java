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
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsException.Status;
import com.google.firebase.installations.local.IidStore;
import com.google.firebase.installations.local.PersistedInstallation;
import com.google.firebase.installations.local.PersistedInstallation.RegistrationStatus;
import com.google.firebase.installations.local.PersistedInstallationEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.InstallationResponse;
import com.google.firebase.installations.remote.InstallationResponse.ResponseCode;
import com.google.firebase.installations.remote.TokenResult;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseInstallations}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseInstallationsTest {
  private FirebaseApp firebaseApp;
  private ExecutorService executor;
  private PersistedInstallation persistedInstallation;
  @Mock private FirebaseInstallationServiceClient mockBackend;
  @Mock private IidStore mockIidStore;
  @Mock private RandomFidGenerator mockFidGenerator;

  public static final String TEST_FID_1 = "cccccccccccccccccccccc";

  public static final String TEST_PROJECT_ID = "777777777777";

  public static final String TEST_AUTH_TOKEN = "fis.auth.token";
  public static final String TEST_AUTH_TOKEN_2 = "fis.auth.token2";
  public static final String TEST_AUTH_TOKEN_3 = "fis.auth.token3";
  public static final String TEST_AUTH_TOKEN_4 = "fis.auth.token4";

  public static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";

  public static final String TEST_REFRESH_TOKEN = "1:test-refresh-token";

  public static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";

  public static final long TEST_TOKEN_EXPIRATION_TIMESTAMP = 4000L;

  public static final String TEST_INSTANCE_ID_1 = "ccccccccccc";

  public static final String TEST_INSTANCE_ID_TOKEN_1 = "iid:token";

  public static final InstallationResponse TEST_INSTALLATION_RESPONSE =
      InstallationResponse.builder()
          .setUri("/projects/" + TEST_PROJECT_ID + "/installations/" + TEST_FID_1)
          .setFid(TEST_FID_1)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setAuthToken(
              TokenResult.builder()
                  .setToken(TEST_AUTH_TOKEN)
                  .setTokenExpirationTimestamp(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                  .build())
          .setResponseCode(ResponseCode.OK)
          .build();

  public static final InstallationResponse TEST_INSTALLATION_RESPONSE_WITH_IID =
      InstallationResponse.builder()
          .setUri("/projects/" + TEST_PROJECT_ID + "/installations/" + TEST_INSTANCE_ID_1)
          .setFid(TEST_INSTANCE_ID_1)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setAuthToken(
              TokenResult.builder()
                  .setToken(TEST_AUTH_TOKEN)
                  .setTokenExpirationTimestamp(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                  .build())
          .setResponseCode(ResponseCode.OK)
          .build();

  public static final TokenResult TEST_TOKEN_RESULT =
      TokenResult.builder()
          .setToken(TEST_AUTH_TOKEN_2)
          .setTokenExpirationTimestamp(TEST_TOKEN_EXPIRATION_TIMESTAMP)
          .setResponseCode(TokenResult.ResponseCode.OK)
          .build();

  private static final FirebaseInstallationsException NETWORK_ERROR =
      new FirebaseInstallationsException("simulated network error", Status.UNAVAILABLE);

  private FirebaseInstallations firebaseInstallations;
  private Utils utils;
  private FakeClock fakeClock;

  @Before
  public void setUp() {
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
    persistedInstallation = new PersistedInstallation(firebaseApp);
    persistedInstallation.clearForTesting();

    fakeClock = new FakeClock(5000000L);
    utils = Utils.getInstance(fakeClock);
    firebaseInstallations =
        new FirebaseInstallations(
            executor,
            firebaseApp,
            mockBackend,
            persistedInstallation,
            utils,
            mockIidStore,
            mockFidGenerator);

    when(mockFidGenerator.createRandomFid()).thenReturn(TEST_FID_1);
  }

  @After
  public void cleanUp() {
    persistedInstallation.clearForTesting();
    try {
      executor.awaitTermination(250, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {

    }
  }

  /**
   * Check the id generation process when there is no network. There are three cases:
   *
   * <ul>
   *   <li>no iid -> generate a new fid
   *   <li>iid present -> make that iid into a fid
   *   <li>fid generated -> return that fid
   * </ul>
   */
  @Test
  public void testGetId_noNetwork_noIid() throws Exception {
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenThrow(NETWORK_ERROR);
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(NETWORK_ERROR);
    when(mockIidStore.readIid()).thenReturn(null);
    when(mockIidStore.readToken()).thenReturn(null);

    // Do the actual getId() call under test. Confirm that it returns a generated FID and
    // and that the FID was written to storage.
    // Confirm both that it returns the expected ID, as does reading the prefs from storage.
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();
    assertWithMessage("getId Task failed.").that(fid).isEqualTo(TEST_FID_1);
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_FID_1));

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // The storage should still have the same ID and the status should indicate that the
    // fid is registered.
    entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_FID_1));
    assertTrue("the entry isn't unregistered: " + entry, entry.isUnregistered());
  }

  @Test
  public void testGetId_noNetwork_iidPresent() throws Exception {
    when(mockBackend.createFirebaseInstallation(
            TEST_API_KEY,
            TEST_INSTANCE_ID_1,
            TEST_PROJECT_ID,
            TEST_APP_ID_1,
            TEST_INSTANCE_ID_TOKEN_1))
        .thenThrow(NETWORK_ERROR);
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(NETWORK_ERROR);
    when(mockIidStore.readIid()).thenReturn(TEST_INSTANCE_ID_1);
    when(mockIidStore.readToken()).thenReturn(TEST_INSTANCE_ID_TOKEN_1);

    // Do the actual getId() call under test. Confirm that it returns a generated FID and
    // and that the FID was written to storage.
    // Confirm both that it returns the expected ID, as does reading the prefs from storage.
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();
    assertWithMessage("getId Task failed.").that(fid).isEqualTo(TEST_INSTANCE_ID_1);
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_INSTANCE_ID_1));

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // The storage should still have the same ID and the status should indicate that the
    // fid is registered.
    entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_INSTANCE_ID_1));
    assertTrue("the entry doesn't have an uregistered fid: " + entry, entry.isUnregistered());
  }

  @Test
  public void testGetId_noNetwork_fidAlreadyGenerated() throws Exception {
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenThrow(NETWORK_ERROR);
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(NETWORK_ERROR);

    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withUnregisteredFid("generatedFid"));

    // Do the actual getId() call under test. Confirm that it returns the already generated FID.
    // Confirm both that it returns the expected ID, as does reading the prefs from storage.
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();
    assertWithMessage("getId Task failed.").that(fid).isEqualTo("generatedFid");

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // The storage should still have the same ID and the status should indicate that the
    // fid is registered.
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo("generatedFid"));
    assertTrue("the entry doesn't have an uregistered fid: " + entry, entry.isUnregistered());
  }

  /**
   * Checks that if we have a registered fid then the fid is returned and no backend calls are made.
   */
  @Test
  public void testGetId_ValidIdAndToken_NoBackendCalls() throws Exception {
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // No exception, means success.
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();
    assertWithMessage("getId Task failed.").that(fid).isEqualTo(TEST_FID_1);

    // getId() returns fid immediately but registers fid asynchronously.  Waiting for half a second
    // while we mock fid registration. We dont send an actual request to FIS in tests.
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // check that the mockClient didn't get invoked at all, since the fid is already registered
    // and the authtoken is present and not expired
    verifyZeroInteractions(mockBackend);

    // check that the fid is still the expected one and is registered
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_FID_1));
    assertTrue("the entry doesn't have a registered fid: " + entry, entry.isRegistered());
  }

  /**
   * Checks that if we have an unregistered fid that the fid gets registered with the backend and no
   * other calls are made.
   */
  @Test
  public void testGetId_UnRegisteredId_IssueCreateIdCall() throws Exception {
    when(mockBackend.createFirebaseInstallation(
            anyString(), matches(TEST_FID_1), anyString(), anyString(), any()))
        .thenReturn(TEST_INSTALLATION_RESPONSE);
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withUnregisteredFid(TEST_FID_1));

    // No exception, means success.
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();
    assertWithMessage("getId Task failed.").that(fid).isEqualTo(TEST_FID_1);

    // getId() returns fid immediately but registers fid asynchronously.  Waiting for half a second
    // while we mock fid registration. We dont send an actual request to FIS in tests.
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // check that the mockClient didn't get invoked at all, since the fid is already registered
    // and the authtoken is present and not expired
    verify(mockBackend)
        .createFirebaseInstallation(
            anyString(), matches(TEST_FID_1), anyString(), anyString(), any());
    verify(mockBackend, never())
        .generateAuthToken(anyString(), anyString(), anyString(), anyString());

    // check that the fid is still the expected one and is registered
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_FID_1));
    assertTrue("the entry doesn't have a registered fid: " + entry, entry.isRegistered());
  }

  @Test
  public void testReadToken_wildcard() {
    SharedPreferences prefs = firebaseApp.getApplicationContext().getSharedPreferences("test", 0);
    prefs
        .edit()
        .putString("|T|123|OTHER", "tokenOTHER")
        .putString("|T|unused|*", "tokenFOREIGN")
        .putString("|T|123|GCM", "tokenGCM")
        .putString("|T|123|FCM", "tokenFCM")
        .putString("|T|123|*", "tokenWILDCARD")
        .putString("|T|123|", "tokenEMPTY")
        .commit();

    IidStore iidStore = new IidStore(prefs, "123");
    assertThat(iidStore.readToken(), equalTo("tokenWILDCARD"));
  }

  @Test
  public void testReadToken_fcm() {
    SharedPreferences prefs = firebaseApp.getApplicationContext().getSharedPreferences("test", 0);
    prefs
        .edit()
        .putString("|T|123|OTHER", "tokenOTHER")
        .putString("|T|unused|*", "tokenFOREIGN")
        .putString("|T|123|GCM", "tokenGCM")
        .putString("|T|123|FCM", "tokenFCM")
        .putString("|T|unused|*", "tokenWILDCARD")
        .putString("|T|123|", "tokenEMPTY")
        .commit();

    IidStore iidStore = new IidStore(prefs, "123");
    assertThat(iidStore.readToken(), equalTo("tokenFCM"));
  }

  @Test
  public void testReadToken_gcm() {
    SharedPreferences prefs = firebaseApp.getApplicationContext().getSharedPreferences("test", 0);
    prefs
        .edit()
        .putString("|T|123|OTHER", "tokenOTHER")
        .putString("|T|unused|*", "tokenFOREIGN")
        .putString("|T|123|GCM", "tokenGCM")
        .putString("|T|unused|FCM", "tokenFCM")
        .putString("|T|unused|*", "tokenWILDCARD")
        .putString("|T|123|", "tokenEMPTY")
        .commit();

    IidStore iidStore = new IidStore(prefs, "123");
    assertThat(iidStore.readToken(), equalTo("tokenGCM"));
  }

  @Test
  public void testReadToken_empty() {
    SharedPreferences prefs = firebaseApp.getApplicationContext().getSharedPreferences("test", 0);
    prefs
        .edit()
        .putString("|T|123|OTHER", "tokenOTHER")
        .putString("|T|unused|*", "tokenFOREIGN")
        .putString("|T|unused|GCM", "tokenGCM")
        .putString("|T|unused|FCM", "tokenFCM")
        .putString("|T|unused|*", "tokenWILDCARD")
        .putString("|T|123|", "tokenEMPTY")
        .commit();

    IidStore iidStore = new IidStore(prefs, "123");
    assertThat(iidStore.readToken(), equalTo("tokenEMPTY"));
  }

  @Test
  public void testReadToken_null() {
    SharedPreferences prefs = firebaseApp.getApplicationContext().getSharedPreferences("test", 0);
    prefs
        .edit()
        .putString("|T|123|OTHER", "tokenOTHER")
        .putString("|T|unused|*", "tokenFOREIGN")
        .putString("|T|unused|GCM", "tokenGCM")
        .putString("|T|unused|FCM", "tokenFCM")
        .putString("|T|unused|*", "tokenWILDCARD")
        .putString("|T|123|BLAH", "tokenEMPTY")
        .commit();

    IidStore iidStore = new IidStore(prefs, "123");
    assertNull(iidStore.readToken());
  }

  @Test
  public void testReadToken_withJsonformatting() {
    SharedPreferences prefs = firebaseApp.getApplicationContext().getSharedPreferences("test", 0);
    prefs
        .edit()
        .putString("|T|123|OTHER", "tokenOTHER")
        .putString("|T|unused|*", "tokenFOREIGN")
        .putString("|T|unused|GCM", "tokenGCM")
        .putString("|T|unused|FCM", "tokenFCM")
        .putString("|T|123|*", "{\"token\" : \"thetoken\"}")
        .putString("|T|123|BLAH", "tokenEMPTY")
        .commit();

    IidStore iidStore = new IidStore(prefs, "123");
    assertThat(iidStore.readToken(), equalTo("thetoken"));
  }

  @Test
  public void testGetId_migrateIid_successful() throws Exception {
    when(mockIidStore.readIid()).thenReturn(TEST_INSTANCE_ID_1);
    when(mockIidStore.readToken()).thenReturn(TEST_INSTANCE_ID_TOKEN_1);
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(TEST_INSTALLATION_RESPONSE_WITH_IID);

    // Do the actual getId() call under test.
    // Confirm both that it returns the expected ID, as does reading the prefs from storage.
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();
    assertWithMessage("getId Task failed.").that(fid).isEqualTo(TEST_INSTANCE_ID_1);
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_INSTANCE_ID_1));

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // The storage should still have the same ID and the status should indicate that the
    // fid si registered.
    entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_INSTANCE_ID_1));
    assertTrue("the entry doesn't have a registered fid: " + entry, entry.isRegistered());
  }

  @Test
  public void testGetId_multipleCalls_sameFIDReturned() throws Exception {
    when(mockIidStore.readIid()).thenReturn(null);
    when(mockIidStore.readToken()).thenReturn(null);
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(TEST_INSTALLATION_RESPONSE);

    // Call getId multiple times
    Task<String> task1 = firebaseInstallations.getId();
    Task<String> task2 = firebaseInstallations.getId();
    TestOnCompleteListener<String> onCompleteListener1 = new TestOnCompleteListener<>();
    task1.addOnCompleteListener(executor, onCompleteListener1);
    TestOnCompleteListener<String> onCompleteListener2 = new TestOnCompleteListener<>();
    task2.addOnCompleteListener(executor, onCompleteListener2);
    onCompleteListener1.await();
    onCompleteListener2.await();

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    assertWithMessage("Persisted Fid of Task1 doesn't match.")
        .that(task1.getResult())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Persisted Fid of Task2 doesn't match.")
        .that(task2.getResult())
        .isEqualTo(TEST_FID_1);
    verify(mockBackend, times(1))
        .createFirebaseInstallation(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_APP_ID_1, null);
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_FID_1));
    assertTrue("the entry isn't doesn't have a registered fid: " + entry, entry.isRegistered());
  }

  @Test
  public void testGetId_expiredAuthTokenThrowsException_statusUpdated() throws Exception {
    // Start with a registered FID
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs()
                - TEST_TOKEN_EXPIRATION_TIMESTAMP
                + TimeUnit.MINUTES.toSeconds(30),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // Move the time forward by the token expiration time.
    fakeClock.advanceTimeBySeconds(
        TEST_TOKEN_EXPIRATION_TIMESTAMP - TimeUnit.MINUTES.toSeconds(30));

    // Mocking an exception on FIS generateAuthToken
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new FirebaseInstallationsException(Status.UNAVAILABLE));

    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> getIdTask = firebaseInstallations.getId();
    getIdTask.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();

    assertWithMessage("getId Task failed").that(fid).isEqualTo(TEST_FID_1);

    // Waiting for Task that generates auth token with the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // Validate that registration status is still REGISTER
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_FID_1));
    assertTrue("the entry doesn't have a registered fid: " + entry, entry.isRegistered());
  }

  /**
   * The FID is successfully registered but the token is expired. A getId will cause the token to be
   * refreshed in the background.
   */
  @Test
  public void testGetId_expiredAuthToken_refreshesAuthToken() throws Exception {
    // Start with a registered FID
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            // Set expiration time to 30 minutes from now (within refresh period)
            utils.currentTimeInSecs()
                - TEST_TOKEN_EXPIRATION_TIMESTAMP
                + TimeUnit.MINUTES.toSeconds(30),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // Make the server generateAuthToken() call return a refreshed token
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(TEST_TOKEN_RESULT);

    // Move the time forward by the token expiration time.
    fakeClock.advanceTimeBySeconds(
        TEST_TOKEN_EXPIRATION_TIMESTAMP - TimeUnit.MINUTES.toSeconds(30));

    // Get the ID, which should cause the SDK to realize that the auth token is expired and
    // kick off a refresh of the token.
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> getIdTask = firebaseInstallations.getId();
    getIdTask.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();
    assertWithMessage("getId Task failed").that(fid).isEqualTo(TEST_FID_1);

    TestOnCompleteListener<InstallationTokenResult> onCompleteListener2 =
        new TestOnCompleteListener<>();
    Task<InstallationTokenResult> task = firebaseInstallations.getToken(false);
    task.addOnCompleteListener(executor, onCompleteListener2);
    InstallationTokenResult installationTokenResult = onCompleteListener2.await();

    // Check that the token has been refreshed
    assertWithMessage("auth token is not what is expected after the refresh")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);

    verify(mockBackend, never())
        .createFirebaseInstallation(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_APP_ID_1, null);
    verify(mockBackend, times(1))
        .generateAuthToken(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  /**
   * Checks that if the server rejects a FID during registration the SDK will use the fid in the
   * response as the new fid.
   */
  @Test
  public void testGetId_unregistered_replacesFidWithResponse() throws Exception {
    // Update local storage with installation entry that has invalid fid.
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withUnregisteredFid("tobereplaced"));
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(TEST_INSTALLATION_RESPONSE);

    // The first call will return the existing FID, "tobereplaced"
    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();

    // do a getId(), the unregistered TEST_FID_1 should be returned
    assertWithMessage("getId Task failed.").that(fid).isEqualTo("tobereplaced");

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // The next call should return the FID that was returned by the server
    onCompleteListener = new TestOnCompleteListener<>();
    task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    fid = onCompleteListener.await();

    // do a getId(), the unregistered TEST_FID_1 should be returned
    assertWithMessage("getId Task failed.").that(fid).isEqualTo(TEST_FID_1);
  }

  /**
   * A registration that fails with a SERVER_ERROR will cause the FID to be put into the error
   * state.
   */
  @Test
  public void testGetId_ServerError_UnregisteredFID() throws Exception {
    // start with an unregistered fid
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withUnregisteredFid(TEST_FID_1));

    // have the server return a server error for the registration
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(
            InstallationResponse.builder().setResponseCode(ResponseCode.BAD_CONFIG).build());

    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> task = firebaseInstallations.getId();
    task.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();

    // do a getId(), the unregistered TEST_FID_1 should be returned
    assertWithMessage("getId Task failed.").that(fid).isEqualTo(TEST_FID_1);

    // Waiting for Task that registers FID on the FIS Servers.
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // We expect that the server error will cause the FID to be put into the error state.
    // There is nothing more we can do.
    PersistedInstallationEntry updatedInstallationEntry =
        persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(updatedInstallationEntry.getFirebaseInstallationId(), equalTo(TEST_FID_1));
    assertTrue(
        "the entry doesn't have an error fid: " + updatedInstallationEntry,
        updatedInstallationEntry.isErrored());
  }

  /** A registration that fails will not cause the FID to be put into the error state. */
  @Test
  public void testGetId_fidRegistrationFailed_statusNotUpdated() throws Exception {
    // set initial state to having an unregistered FID
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withUnregisteredFid(TEST_FID_1));

    // Mocking unchecked exception on FIS createFirebaseInstallation
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenThrow(new FirebaseInstallationsException("Registration Failed", Status.BAD_CONFIG));

    TestOnCompleteListener<String> onCompleteListener = new TestOnCompleteListener<>();
    Task<String> getIdTask = firebaseInstallations.getId();
    getIdTask.addOnCompleteListener(executor, onCompleteListener);
    String fid = onCompleteListener.await();

    assertEquals("fid doesn't match expected", TEST_FID_1, fid);

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    // We expect that the IOException will cause the request to fail, but it will not
    // cause the FID to be put into the error state because we expect this to eventually succeed.
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getFirebaseInstallationId(), equalTo(TEST_FID_1));
    assertTrue("the entry doesn't have an unregistered fid: " + entry, entry.isUnregistered());
  }

  @Test
  public void testGetAuthToken_fidDoesNotExist_successful() throws Exception {
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(TEST_INSTALLATION_RESPONSE);

    TestOnCompleteListener<InstallationTokenResult> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<InstallationTokenResult> task = firebaseInstallations.getToken(false);
    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getAuthToken(), equalTo(TEST_AUTH_TOKEN));
  }

  @Test
  public void testGetAuthToken_fidExists_successful() throws Exception {
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    TestOnCompleteListener<InstallationTokenResult> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<InstallationTokenResult> task = firebaseInstallations.getToken(false);
    task.addOnCompleteListener(executor, onCompleteListener);
    InstallationTokenResult installationTokenResult = onCompleteListener.await();

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN);
    verify(mockBackend, never())
        .generateAuthToken(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testGetAuthToken_expiredAuthToken_fetchedNewTokenFromFIS() throws Exception {
    // start with a registered FID and valid auth token
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            // Set expiration time to 30 minutes from now (within refresh period)
            utils.currentTimeInSecs()
                - TEST_TOKEN_EXPIRATION_TIMESTAMP
                + TimeUnit.MINUTES.toSeconds(30),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // Move the time forward by the token expiration time.
    fakeClock.advanceTimeBySeconds(
        TEST_TOKEN_EXPIRATION_TIMESTAMP - TimeUnit.MINUTES.toSeconds(30));

    // have the server respond with a new token
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(TEST_TOKEN_RESULT);

    TestOnCompleteListener<InstallationTokenResult> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<InstallationTokenResult> task = firebaseInstallations.getToken(false);
    task.addOnCompleteListener(executor, onCompleteListener);
    InstallationTokenResult installationTokenResult = onCompleteListener.await();

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
  }

  @Test
  public void testGetAuthToken_multipleCallsDoNotForceRefresh_fetchedNewTokenOnce()
      throws Exception {
    // start with a valid fid and authtoken
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            // Set expiration time to 30 minutes from now (within refresh period)
            utils.currentTimeInSecs()
                - TEST_TOKEN_EXPIRATION_TIMESTAMP
                + TimeUnit.MINUTES.toSeconds(30),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // Make the server generateAuthToken() call return a refreshed token
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(TEST_TOKEN_RESULT);

    // expire the authtoken by advancing the clock
    fakeClock.advanceTimeBySeconds(
        TEST_TOKEN_EXPIRATION_TIMESTAMP - TimeUnit.MINUTES.toSeconds(30));

    // Call getToken multiple times with DO_NOT_FORCE_REFRESH option
    Task<InstallationTokenResult> task1 = firebaseInstallations.getToken(false);
    Task<InstallationTokenResult> task2 = firebaseInstallations.getToken(false);
    TestOnCompleteListener<InstallationTokenResult> onCompleteListener1 =
        new TestOnCompleteListener<>();
    task1.addOnCompleteListener(executor, onCompleteListener1);
    TestOnCompleteListener<InstallationTokenResult> onCompleteListener2 =
        new TestOnCompleteListener<>();
    task2.addOnCompleteListener(executor, onCompleteListener2);
    onCompleteListener1.await();
    onCompleteListener2.await();

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task1.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task2.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
    verify(mockBackend, times(1))
        .generateAuthToken(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testGetToken_unregisteredFid_fetchedNewTokenFromFIS() throws Exception {
    // Update local storage with a unregistered installation entry to validate that getToken
    // calls getId to ensure FID registration and returns a valid auth token.
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withUnregisteredFid(TEST_FID_1));
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(TEST_INSTALLATION_RESPONSE);

    TestOnCompleteListener<InstallationTokenResult> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<InstallationTokenResult> task = firebaseInstallations.getToken(false);
    task.addOnCompleteListener(executor, onCompleteListener);
    InstallationTokenResult installationTokenResult = onCompleteListener.await();

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN);
  }

  @Test
  public void testGetAuthToken_authError_persistedInstallationCleared() throws Exception {
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // Mocks error during auth token generation
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            TokenResult.builder().setResponseCode(TokenResult.ResponseCode.AUTH_ERROR).build());

    // Expect exception
    try {
      TestOnCompleteListener<InstallationTokenResult> onCompleteListener =
          new TestOnCompleteListener<>();
      Task<InstallationTokenResult> task = firebaseInstallations.getToken(true);
      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
      fail("the getAuthToken() call should have failed due to Auth Error.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(IOException.class);
    }

    assertTrue(persistedInstallation.readPersistedInstallationEntryValue().isNotGenerated());
  }

  /**
   * Check that a call to generateAuthToken(FORCE_REFRESH) fails if the backend client call fails.
   */
  @Test
  public void testGetAuthToken_serverError_failure() throws Exception {
    // start the test with a registered FID
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // have the backend fail when generateAuthToken is invoked.
    when(mockBackend.generateAuthToken(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            TokenResult.builder().setResponseCode(TokenResult.ResponseCode.BAD_CONFIG).build());

    // Make the forced getAuthToken call, which should fail.
    try {
      TestOnCompleteListener<InstallationTokenResult> onCompleteListener =
          new TestOnCompleteListener<>();
      Task<InstallationTokenResult> task = firebaseInstallations.getToken(true);
      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
      fail(
          "getAuthToken() succeeded but should have failed due to the BAD_CONFIG error "
              + "returned by the network call.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) expected.getCause()).getStatus())
          .isEqualTo(Status.BAD_CONFIG);
    }
  }

  @Test
  @Ignore("the code doesn't currently enforce a single token fetch at a time")
  public void testGetAuthToken_multipleCallsForceRefresh_fetchedNewTokenTwice() throws Exception {
    // start with a valid fid and authtoken
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    // Use a mock ServiceClient for network calls with delay(500ms) to ensure first task is not
    // completed before the second task starts. Hence, we can test multiple calls to getToken()
    // and verify one task waits for another task to complete.

    doAnswer(
            AdditionalAnswers.answersWithDelay(
                500,
                (unused) ->
                    TokenResult.builder()
                        .setToken(TEST_AUTH_TOKEN_3)
                        .setTokenExpirationTimestamp(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                        .setResponseCode(TokenResult.ResponseCode.OK)
                        .build()))
        .doAnswer(
            AdditionalAnswers.answersWithDelay(
                500,
                (unused) ->
                    TokenResult.builder()
                        .setToken(TEST_AUTH_TOKEN_4)
                        .setTokenExpirationTimestamp(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                        .setResponseCode(TokenResult.ResponseCode.OK)
                        .build()))
        .when(mockBackend)
        .generateAuthToken(anyString(), anyString(), anyString(), anyString());

    // Call getToken multiple times with FORCE_REFRESH option.
    // Call getToken multiple times with DO_NOT_FORCE_REFRESH option
    Task<InstallationTokenResult> task1 = firebaseInstallations.getToken(true);
    Task<InstallationTokenResult> task2 = firebaseInstallations.getToken(true);
    TestOnCompleteListener<InstallationTokenResult> onCompleteListener1 =
        new TestOnCompleteListener<>();
    task1.addOnCompleteListener(executor, onCompleteListener1);
    TestOnCompleteListener<InstallationTokenResult> onCompleteListener2 =
        new TestOnCompleteListener<>();
    task2.addOnCompleteListener(executor, onCompleteListener2);
    onCompleteListener1.await();
    onCompleteListener2.await();

    // As we cannot ensure which task got executed first, verifying with both expected values
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task1.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_3);
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task2.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_3);
    verify(mockBackend, times(1))
        .generateAuthToken(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertThat(entry.getAuthToken(), equalTo(TEST_AUTH_TOKEN_3));
  }

  @Test
  public void testDelete_registeredFID_successful() throws Exception {
    // Update local storage with a registered installation entry
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));
    when(mockBackend.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(TEST_INSTALLATION_RESPONSE);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    firebaseInstallations.delete().addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    PersistedInstallationEntry entryValue =
        persistedInstallation.readPersistedInstallationEntryValue();
    assertEquals(entryValue.getRegistrationStatus(), RegistrationStatus.NOT_GENERATED);
    verify(mockBackend, times(1))
        .deleteFirebaseInstallation(TEST_API_KEY, TEST_FID_1, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);
  }

  @Test
  public void testDelete_unregisteredFID_successful() throws Exception {
    // Update local storage with a unregistered installation entry
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withUnregisteredFid(TEST_FID_1));

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    firebaseInstallations.delete().addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    PersistedInstallationEntry entryValue =
        persistedInstallation.readPersistedInstallationEntryValue();
    assertEquals(entryValue.getRegistrationStatus(), RegistrationStatus.NOT_GENERATED);
    verify(mockBackend, never())
        .deleteFirebaseInstallation(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void testDelete_emptyPersistedFidEntry_successful() throws Exception {
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withNoGeneratedFid());

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    firebaseInstallations.delete().addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    assertTrue(
        "the entry was expected to need a newly generated fid: " + entry, entry.isNotGenerated());

    verify(mockBackend, never())
        .deleteFirebaseInstallation(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void testDelete_serverError_badConfig() throws Exception {
    // Update local storage with a registered installation entry
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    doThrow(new FirebaseInstallationsException("Server Error", Status.BAD_CONFIG))
        .when(mockBackend)
        .deleteFirebaseInstallation(anyString(), anyString(), anyString(), anyString());

    // Expect exception
    try {
      TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
      firebaseInstallations.delete().addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
      fail("firebaseInstallations.delete() failed due to Server Error.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) expected.getCause()).getStatus())
          .isEqualTo(Status.BAD_CONFIG);
      PersistedInstallationEntry entry =
          persistedInstallation.readPersistedInstallationEntryValue();
      assertTrue("the entry was expected to still be registered: " + entry, entry.isRegistered());
    }
  }

  @Test
  public void testDelete_networkError() throws Exception {
    // Update local storage with a registered installation entry
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(
        PersistedInstallationEntry.INSTANCE.withRegisteredFid(
            TEST_FID_1,
            TEST_REFRESH_TOKEN,
            utils.currentTimeInSecs(),
            TEST_AUTH_TOKEN,
            TEST_TOKEN_EXPIRATION_TIMESTAMP));

    doThrow(NETWORK_ERROR)
        .when(mockBackend)
        .deleteFirebaseInstallation(anyString(), anyString(), anyString(), anyString());

    // Expect exception
    try {
      TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
      firebaseInstallations.delete().addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
      fail("firebaseInstallations.delete() should have failed due to a Network Error.");
    } catch (ExecutionException expected) {
      assertWithMessage("Exception class doesn't match")
          .that(expected)
          .hasCauseThat()
          .isInstanceOf(FirebaseInstallationsException.class);
      PersistedInstallationEntry entry =
          persistedInstallation.readPersistedInstallationEntryValue();
      assertTrue(
          "the entry was expected to still be registered since the delete failed: " + entry,
          entry.isRegistered());
    }
  }

  @Test
  public void testAppIdCheck() {
    // valid appid
    assertTrue(Utils.isValidAppIdFormat("1:123456789:android:abcdef"));
    assertTrue(Utils.isValidAppIdFormat("1:515438998704:android:e78ec19738058349"));
    assertTrue(Utils.isValidAppIdFormat("1:208472424340:android:a243f98a00873753"));
    assertTrue(Utils.isValidAppIdFormat("1:755541669657:ios:4d6d5a5ce71e9d30"));
    assertTrue(Utils.isValidAppIdFormat("1:1086610230652:ios:852c7f6ee799ff89"));
    assertTrue(Utils.isValidAppIdFormat("1:35006771263:web:32b6f4a5b95acd2c"));
    // invalid appid
    assertFalse(Utils.isValidAppIdFormat("abc.abc.abc"));
    assertFalse(
        Utils.isValidAppIdFormat(
            "com.google.firebase.samples.messaging.advanced")); // using pakage name as App ID
  }

  @Test
  public void testApiKeyCheck() {
    // valid ApiKey
    assertTrue(Utils.isValidApiKeyFormat("AIzaSyabcdefghijklmnopqrstuvwxyz1234567"));
    assertTrue(Utils.isValidApiKeyFormat("AIzaSyA4UrcGxgwQFTfaI3no3t7Lt1sjmdnP5sQ"));
    assertTrue(Utils.isValidApiKeyFormat("AIzaSyA5_iVawFQ8ABuTZNUdcwERLJv_a_p4wtM"));
    assertTrue(Utils.isValidApiKeyFormat("AIzaSyANUvH9H9BsUccjsu2pCmEkOPjjaXeDQgY"));
    assertTrue(Utils.isValidApiKeyFormat("AIzaSyASWm6HmTMdYWpgMnjRBjxcQ9CKctWmLd4"));
    assertTrue(Utils.isValidApiKeyFormat("AIzaSyAdOS2zB6NCsk1pCdZ4-P6GBdi_UUPwX7c"));
    assertTrue(Utils.isValidApiKeyFormat("AIzaSyAnLA7NfeLquW1tJFpx_eQCxoX-oo6YyIs"));
    // invalid ApiKey
    assertFalse(
        Utils.isValidApiKeyFormat("BIzaSyabcdefghijklmnopqrstuvwxyz1234567")); // wrong prefix
    assertFalse(Utils.isValidApiKeyFormat("AIzaSyabcdefghijklmnopqrstuvwxyz")); // wrong length
    assertFalse(Utils.isValidApiKeyFormat("AIzaSyabcdefghijklmno:qrstuvwxyzabcdefg")); // wrong char
    assertFalse(Utils.isValidApiKeyFormat("AIzaSyabcdefghijklmno qrstuvwxyzabcdefg")); // wrong char
    assertFalse(
        Utils.isValidApiKeyFormat(
            "AAAAdpB7anM:APA91bFFK03DIT8y3l5uymwbKcUDJdYqTRSP9Qcxg8SU5kKPalEpObdx0C0xv8gQttdWlL"
                + "W4hLvvHA0JoDKA6Lrvbi-edUjFCPY_WJkuvHxFwGWXjnj4yI4sPQ27mXuSVIyAbgX4aTK0QY"
                + "pIKq2j1NBi7ZU75gunQg")); // using FCM server key as API key.
  }
}
