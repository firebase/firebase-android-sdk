// Copyright 2020 Google LLC
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
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.cloudmessaging.Rpc;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.base.Supplier;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import com.google.firebase.messaging.testing.Bundles;
import com.google.firebase.messaging.testing.LibraryVersion;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Robolectric test for the GmsRpcRoboTest. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowPreconditions.class})
public class GmsRpcRoboTest {

  private static final int TIMEOUT_S = 5;

  static final int IID_VIA_SERVICE = 1;

  private static final String EXTRA_SUBTYPE = "subtype";
  private static final String EXTRA_SENDER = "sender";
  private static final String EXTRA_SCOPE = "scope";
  private static final String EXTRA_DELETE = "delete";
  private static final String EXTRA_IID_OPERATION = "iid-operation";

  private static final String PARAM_INSTANCE_ID = "appid";
  private static final String PARAM_CLIENT_VER = "cliv";
  private static final String PARAM_GMP_APP_ID = "gmp_app_id";
  private static final String PARAM_GMS_VER = "gmsv";
  private static final String PARAM_OS_VER = "osv";
  private static final String PARAM_APP_VER_CODE = "app_ver";
  private static final String PARAM_APP_VER_NAME = "app_ver_name";
  private static final String PARAM_USER_AGENT = "Firebase-Client";
  private static final String PARAM_HEARTBEAT_CODE = "Firebase-Client-Log-Type";
  private static final String PARAM_FIS_AUTH_TOKEN = "Goog-Firebase-Installations-Auth";
  // SHA-1 hashed b64 value of (nick)name "[default]" of Firebase Core SDK (a.k.a. FirebaseApp)
  private static final String PARAM_FIREBASE_APP_NAME_HASH = "firebase-app-name-hash";

  // registration result action and extras
  private static final String EXTRA_REGISTRATION_ID = "registration_id";
  private static final String EXTRA_UNREGISTERED = "unregistered";

  private static final String APP_ID = "test_app_id";
  private static final String INSTANCE_ID = "test_instance_id";
  private static final int GMS_VERSION_CODE = 0;
  private static final String APP_VERSION_CODE = "test_app_version_code";
  private static final String APP_VERSION_NAME = "test_app_version_name";
  private static final String EXTRA_TOPIC = "gcm.topic";
  private static final String TOPIC_PREFIX = "/topics/";
  private static final String USER_AGENT = "foo/1.1.1 bar/1.2.2";
  private static final String FIS_AUTH_TOKEN = "fis:auth:token";
  private static final String FIREBASE_APP_NAME = "[default]";
  // Hashed and base64-urlsafe-encoded version of FIREBASE_APP_NAME
  private static final String HASHED_FIREBASE_APP_NAME = "xD-CWoIl_w4XhPw3BV-pHR1B9Pc";
  private static final String SCOPE_ALL = "*";
  private static final String SENDER_ID = "test_sender_id";

  private GmsRpc gmsRpc;
  private Rpc internalRpc;
  private Application context;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FirebaseInstallationsApi mockFirebaseInstallationsApi;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();

    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder().setApplicationId(APP_ID).setGcmSenderId(SENDER_ID).build();
    FirebaseApp app = mock(FirebaseApp.class);
    HeartBeatInfo heartBeatInfoObject =
        new HeartBeatInfo() {
          @Override
          public HeartBeat getHeartBeatCode(@NonNull String heartBeatTag) {
            return HeartBeat.GLOBAL;
          }
        };
    Provider<HeartBeatInfo> heartBeatInfo = () -> heartBeatInfoObject;
    UserAgentPublisher userAgentPublisherObject = () -> USER_AGENT;
    Provider<UserAgentPublisher> userAgentPublisher = () -> userAgentPublisherObject;

    when(mockFirebaseInstallationsApi.getId()).thenReturn(Tasks.forResult(INSTANCE_ID));
    when(mockFirebaseInstallationsApi.getToken(anyBoolean()))
        .thenReturn(
            Tasks.forResult(
                InstallationTokenResult.builder()
                    .setToken(FIS_AUTH_TOKEN)
                    .setTokenCreationTimestamp(0)
                    .setTokenExpirationTimestamp(0)
                    .build()));

    doReturn(firebaseOptions).when(app).getOptions();
    doReturn(context).when(app).getApplicationContext();
    doReturn(FIREBASE_APP_NAME).when(app).getName();

    Metadata metadata = mock(Metadata.class);
    doReturn(GMS_VERSION_CODE).when(metadata).getGmsVersionCode();
    doReturn(APP_VERSION_CODE).when(metadata).getAppVersionCode();
    doReturn(APP_VERSION_NAME).when(metadata).getAppVersionName();
    doReturn(IID_VIA_SERVICE).when(metadata).getIidImplementation();
    internalRpc = mock(Rpc.class);
    gmsRpc =
        new GmsRpc(
            app,
            metadata,
            internalRpc,
            userAgentPublisher,
            heartBeatInfo,
            mockFirebaseInstallationsApi);
  }

  @Test
  public void testGetToken() throws Throwable {
    doReturn(createRegistrationResponse("a_token")).when(internalRpc).send(any(Bundle.class));
    Task<String> tokenTask = gmsRpc.getToken();

    // verify the task
    String token = Tasks.await(tokenTask, TIMEOUT_S, TimeUnit.SECONDS);
    assertEquals("a_token", token);

    // verify the request bundle
    Bundle expectedParameters = getDefaultParameters(SENDER_ID, SCOPE_ALL);
    verifyRpcCallArgument(expectedParameters);
  }

  @Test
  public void testGetToken_propagatesIoException() {
    testPropagatesIoException(() -> gmsRpc.getToken());
  }

  @Test
  public void testDeleteToken() throws Throwable {
    doReturn(createUnregistrationResponse()).when(internalRpc).send(any(Bundle.class));

    Task<?> rpcTask = gmsRpc.deleteToken();
    rpcTask.getResult(IOException.class);
    assertThat(rpcTask.isSuccessful()).isTrue();

    // verify the request bundle
    Bundle expectedParameters = getDefaultParameters(SENDER_ID, SCOPE_ALL);
    expectedParameters.putString(EXTRA_DELETE, "1");
    verifyRpcCallArgument(expectedParameters);
  }

  @Test
  public void testDeleteToken_propagatesIoException() {
    testPropagatesIoException(() -> gmsRpc.deleteToken());
  }

  @Test
  public void testSubscribeToTopic() throws Throwable {
    String topic = "topic_1311";
    String cachedToken = "token_1311";
    doReturn(createTopicResponse()).when(internalRpc).send(any(Bundle.class));

    Task<?> rpcTask = gmsRpc.subscribeToTopic(cachedToken, topic);

    assertThat(rpcTask.isSuccessful()).isTrue();

    // verify the request bundle
    Bundle expectedParameters = getDefaultParameters(cachedToken, TOPIC_PREFIX + topic);
    expectedParameters.putString(EXTRA_TOPIC, TOPIC_PREFIX + topic);
    verifyRpcCallArgument(expectedParameters);
  }

  @Test
  public void testSubscribeToToken_propagatesIoException() {
    testPropagatesIoException(() -> gmsRpc.subscribeToTopic("cachedToken", "topic"));
  }

  @Test
  public void testUnsubscribeFromTopic() throws Throwable {
    String topic = "topic_1311";
    String cachedToken = "token_1311";
    doReturn(createTopicResponse()).when(internalRpc).send(any(Bundle.class));

    Task<?> rpcTask = gmsRpc.unsubscribeFromTopic(cachedToken, topic);

    // verify the task is completed in time
    Tasks.await(rpcTask, TIMEOUT_S, TimeUnit.SECONDS);

    // verify the request bundle
    Bundle expectedParameters = getDefaultParameters(cachedToken, TOPIC_PREFIX + topic);
    expectedParameters.putString(EXTRA_DELETE, "1");
    expectedParameters.putString(EXTRA_TOPIC, TOPIC_PREFIX + topic);
    verifyRpcCallArgument(expectedParameters);
  }

  @Test
  public void testUnsubscribeFromToken_propagatesIoException() {
    testPropagatesIoException(() -> gmsRpc.unsubscribeFromTopic("cachedToken", "topic"));
  }

  @Test
  public void testClientVersionReadsLibraryVersion() throws Exception {
    doReturn(createUnregistrationResponse()).when(internalRpc).send(nullable(Bundle.class));
    LibraryVersion originalLibraryVersion = LibraryVersion.getInstance();
    try {
      LibraryVersion mockLibraryVersion = mock(LibraryVersion.class);
      when(mockLibraryVersion.getVersion(eq("firebase-fcm"))).thenReturn("1337");
      LibraryVersion.setInstanceForTesting(mockLibraryVersion);
      // verify the task is completed in time
      Tasks.await(gmsRpc.getToken(), TIMEOUT_S, TimeUnit.SECONDS);

      // verify the request bundle
      Bundle expectedParameters = getDefaultParameters(/* to= */ SENDER_ID, /* scope= */ SCOPE_ALL);
      expectedParameters.putString(PARAM_CLIENT_VER, "fcm-" + BuildConfig.VERSION_NAME);

      verifyRpcCallArgument(expectedParameters);
    } finally {
      LibraryVersion.setInstanceForTesting(originalLibraryVersion);
    }
  }

  private Bundle getDefaultParameters(String to, String scope) {
    Bundle expectedParameters = new Bundle();
    expectedParameters.putString(EXTRA_SCOPE, scope);
    expectedParameters.putString(EXTRA_SENDER, to);
    expectedParameters.putString(EXTRA_SUBTYPE, to);
    expectedParameters.putString(PARAM_INSTANCE_ID, INSTANCE_ID);
    expectedParameters.putString(PARAM_GMP_APP_ID, APP_ID);
    expectedParameters.putString(PARAM_GMS_VER, Integer.toString(GMS_VERSION_CODE));
    expectedParameters.putString(PARAM_OS_VER, Integer.toString(Build.VERSION.SDK_INT));
    expectedParameters.putString(PARAM_APP_VER_CODE, APP_VERSION_CODE);
    expectedParameters.putString(PARAM_APP_VER_NAME, APP_VERSION_NAME);
    expectedParameters.putString(PARAM_CLIENT_VER, "fcm-" + BuildConfig.VERSION_NAME);
    expectedParameters.putString(PARAM_HEARTBEAT_CODE, "2");
    expectedParameters.putString(PARAM_USER_AGENT, USER_AGENT);
    expectedParameters.putString(PARAM_FIS_AUTH_TOKEN, FIS_AUTH_TOKEN);
    expectedParameters.putString(PARAM_FIREBASE_APP_NAME_HASH, HASHED_FIREBASE_APP_NAME);

    return expectedParameters;
  }

  private void verifyRpcCallArgument(Bundle expected) {
    Bundle actual = getRpcCallArgument();

    assertNotNull(expected);
    assertNotNull(actual);
    Bundles.assertEquals(expected, actual);
  }

  private Bundle getRpcCallArgument() {
    ArgumentCaptor<Bundle> argumentCaptor = ArgumentCaptor.forClass(Bundle.class);
    verify(internalRpc).send(argumentCaptor.capture());
    return argumentCaptor.getValue();
  }

  private static Task<Bundle> createRegistrationResponse(String token) {
    return Tasks.forResult(Bundles.of(EXTRA_REGISTRATION_ID, token));
  }

  private static Task<Bundle> createUnregistrationResponse() {
    return Tasks.forResult(Bundles.of(EXTRA_UNREGISTERED, "fake.package.name"));
  }

  private static Task<Bundle> createTopicResponse() {
    return Tasks.forResult(Bundles.of(EXTRA_REGISTRATION_ID, ""));
  }

  private void testPropagatesIoException(Supplier<Task<?>> request) {
    doReturn(Tasks.forException(new IOException("test"))).when(internalRpc).send(any(Bundle.class));

    Task<?> task = request.get();

    assertThat(task.isSuccessful()).isFalse();

    Exception exception = task.getException();
    assertThat(exception).isInstanceOf(IOException.class);
    assertThat(exception).hasMessageThat().isEqualTo("test");
  }
}
