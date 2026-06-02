// Copyright 2026 Google LLC
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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.cloudmessaging.CloudMessagingClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowPreconditions.class)
@LooperMode(Mode.PAUSED)
public class GmsRegistrationClientRoboTest {
  private static final String TEST_TOKEN = "test_token";
  private static final String TEST_FID = "test_fid";
  private static final String SENDER_ID = "1234567890";
  private static final String API_KEY = "test_api_key";
  private static final String APP_ID = "1:1234567890:android:321abc456def7890";

  @Mock private FirebaseInstallationsApi mockFirebaseInstallationsApi;
  @Mock private GmsRpc mockGmsRpc;
  @Mock private Metadata metadata;

  private CloudMessagingClient proxyCloudMessagingClient;
  private GmsRegistrationClient client;
  private Context context;
  private FirebaseApp firebaseApp;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    context = ApplicationProvider.getApplicationContext();

    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApplicationId(APP_ID)
            .setProjectId("test_project_id")
            .setApiKey(API_KEY)
            .setGcmSenderId(SENDER_ID)
            .build();
    FirebaseApp.clearInstancesForTest();
    firebaseApp = FirebaseApp.initializeApp(context, options);

    InstallationTokenResult tokenResult =
        InstallationTokenResult.builder()
            .setToken(TEST_TOKEN)
            .setTokenExpirationTimestamp(0)
            .setTokenCreationTimestamp(0)
            .build();
    when(mockFirebaseInstallationsApi.getToken(false)).thenReturn(Tasks.forResult(tokenResult));
    when(mockFirebaseInstallationsApi.getId()).thenReturn(Tasks.forResult(TEST_FID));

    proxyCloudMessagingClient =
        (CloudMessagingClient)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {CloudMessagingClient.class},
                (proxy, method, args) -> {
                  System.out.println("Proxy called: " + method.getName());
                  if (method.getName().equals("register")
                      || method.getName().equals("registerApp")) {
                    return Tasks.forResult(TEST_FID);
                  } else if (method.getName().equals("unregister")
                      || method.getName().equals("unregisterApp")) {
                    return Tasks.forResult(null);
                  }
                  // For Object methods like toString, hashCode
                  if (method.getName().equals("toString")) return "Proxy(CloudMessagingClient)";

                  return Tasks.forResult(
                      null); // default to a completed task instead of null to avoid NPE
                });

    client =
        new GmsRegistrationClient(
            firebaseApp,
            mockFirebaseInstallationsApi,
            mockGmsRpc,
            proxyCloudMessagingClient,
            metadata);
  }

  private void setV1RegistrationEnabled(boolean enabled) throws Exception {
    ApplicationInfo applicationInfo =
        shadowOf(context.getPackageManager())
            .getInternalMutablePackageInfo(context.getPackageName())
            .applicationInfo;
    if (applicationInfo.metaData == null) {
      applicationInfo.metaData = new Bundle();
    }
    applicationInfo.metaData.putBoolean("firebase_messaging_installation_id_enabled", enabled);

    // Some Robolectric versions require setting it directly on the context's ApplicationInfo too:
    if (context.getApplicationInfo().metaData == null) {
      context.getApplicationInfo().metaData = new Bundle();
    }
    context
        .getApplicationInfo()
        .metaData
        .putBoolean("firebase_messaging_installation_id_enabled", enabled);
  }

  private <T> T awaitTaskOnBackground(Task<T> task) throws Exception {
    ShadowLooper.idleMainLooper();
    return Tasks.await(task, 5, java.util.concurrent.TimeUnit.SECONDS);
  }

  @Test
  public void testIsV1RegistrationEnabled_defaultsToFalse() {
    assertThat(client.isV1RegistrationEnabled()).isFalse();
  }

  @Test
  public void testIsV1RegistrationEnabled_whenEnabledInManifest() throws Exception {
    setV1RegistrationEnabled(true);
    assertThat(client.isV1RegistrationEnabled()).isTrue();
  }

  @Test
  public void testRegister_legacyFlow() throws Exception {
    setV1RegistrationEnabled(false);
    when(mockGmsRpc.getToken(false)).thenReturn(Tasks.forResult("legacy_token"));

    Task<String> task = client.register();

    assertThat(awaitTaskOnBackground(task)).isEqualTo("legacy_token");
    verify(mockGmsRpc).getToken(false);
  }

  @Test
  public void testRegister_v1Flow_success() throws Exception {
    setV1RegistrationEnabled(true);
    when(mockGmsRpc.getToken(true)).thenReturn(Tasks.forResult(TEST_FID));
    // haveV1RegistrationSupport currently returns false, so fallback is used

    Task<String> task = client.register();

    // The stubbed `register` request above returns "test_token_fid_test_fid".
    // The actual install ID is "test_fid" and it is checked by `validateToken`.
    // Validating it works out:
    assertThat(awaitTaskOnBackground(task)).isEqualTo(TEST_FID);
  }

  @Test
  public void testUnregister_legacyFlow() throws Exception {
    setV1RegistrationEnabled(false);
    when(mockGmsRpc.deleteToken(false)).thenReturn(Tasks.forResult(null));

    Task<?> task = client.unregister();

    assertThat(awaitTaskOnBackground(task)).isNull();
    verify(mockGmsRpc).deleteToken(false);
  }

  @Test
  public void testUnregister_v1Flow() throws Exception {
    setV1RegistrationEnabled(true);
    when(mockGmsRpc.deleteToken(true)).thenReturn(Tasks.forResult(null));

    Task<?> task = client.unregister();

    assertThat(awaitTaskOnBackground(task)).isNull();
  }

  @Test
  public void testRegister_v1Flow_withSupport_success() throws Exception {
    setV1RegistrationEnabled(true);
    when(metadata.getGmsVersionCode()).thenReturn(261200000); // support V1

    Task<String> task = client.register();

    assertThat(awaitTaskOnBackground(task)).isEqualTo(TEST_FID);
  }

  @Test
  public void testRegister_v1Flow_getTokenFailure() throws Exception {
    setV1RegistrationEnabled(true);
    when(metadata.getGmsVersionCode()).thenReturn(261200000); // support V1

    // Mock getToken to fail
    Exception exception = new Exception("Simulated FIS Failure");
    when(mockFirebaseInstallationsApi.getToken(false)).thenReturn(Tasks.forException(exception));

    Task<String> task = client.register();

    try {
      awaitTaskOnBackground(task);
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertThat(e.getCause()).hasMessageThat().contains("Simulated FIS Failure");
    }
  }

  @Test
  public void testUnregister_v1Flow_withSupport_success() throws Exception {
    setV1RegistrationEnabled(true);
    when(metadata.getGmsVersionCode()).thenReturn(261200000); // support V1

    Task<?> task = client.unregister();

    assertThat(awaitTaskOnBackground(task)).isNull();
  }

  @Test
  public void testUnregister_v1Flow_getTokenFailure() throws Exception {
    setV1RegistrationEnabled(true);
    when(metadata.getGmsVersionCode()).thenReturn(261200000); // support V1

    // Mock getToken to fail
    Exception exception = new Exception("Simulated FIS Failure");
    when(mockFirebaseInstallationsApi.getToken(false)).thenReturn(Tasks.forException(exception));

    Task<?> task = client.unregister();

    try {
      awaitTaskOnBackground(task);
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertThat(e.getCause()).hasMessageThat().contains("Simulated FIS Failure");
    }
  }
}
