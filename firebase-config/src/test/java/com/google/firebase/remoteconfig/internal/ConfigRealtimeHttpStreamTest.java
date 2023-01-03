package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.REALTIME_REGEX_URL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class ConfigRealtimeHttpStreamTest {
  private final String NAMESPACE = "firebase";
  private final String PROJECT_NUMBER = "14368190084";
  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String PROJECT_ID = "fake-frc-test-id";

  private static final String API_KEY_HEADER = "X-Goog-Api-Key";
  private static final String X_ANDROID_PACKAGE_HEADER = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER = "X-Android-Cert";
  private static final String X_GOOGLE_GFE_CAN_RETRY = "X-Google-GFE-Can-Retry";
  private static final String INSTALLATIONS_AUTH_TOKEN_HEADER =
      "X-Goog-Firebase-Installations-Auth";
  private static final String X_ACCEPT_RESPONSE_STREAMING = "X-Accept-Response-Streaming";

  @Mock private ScheduledExecutorService scheduledExecutorService;
  @Mock private ConfigFetchHandler configFetchHandler;
  @Mock private FirebaseInstallationsApi firebaseInstallations;
  @Mock private ConfigAutoFetch configAutoFetch;
  @Mock private HttpURLConnection httpURLConnection;
  @Mock private ConfigUpdateListener mockStreamErrorEventListener;

  private FakeHttpURLConnection fakeHttpURLConnection;
  private Set<ConfigUpdateListener> configUpdateListenerSet;
  private ConfigRealtimeHttpStream configRealtimeHttpStream;
  private Context context;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    context = ApplicationProvider.getApplicationContext();
    FirebaseApp firebaseApp = initializeFirebaseApp(context);

    when(firebaseInstallations.getToken(false))
        .thenReturn(
            Tasks.forResult(
                new InstallationTokenResult() {
                  @NonNull
                  @Override
                  public String getToken() {
                    return "10";
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

    configUpdateListenerSet = new HashSet<>();
    configUpdateListenerSet.add(
        new ConfigUpdateListener() {
          @Override
          public void onEvent() {}

          @Override
          public void onError(@NonNull FirebaseRemoteConfigException error) {
            if (error.getCode() == FirebaseRemoteConfigException.Code.CONFIG_UPDATE_STREAM_ERROR) {
              mockStreamErrorEventListener.onError(error);
            }
          }
        });
    when(httpURLConnection.getInputStream())
        .thenReturn(
            new InputStream() {
              @Override
              public int read() throws IOException {
                return 0;
              }
            });
    when(httpURLConnection.getOutputStream())
        .thenReturn(
            new OutputStream() {
              @Override
              public void write(int i) throws IOException {}
            });

    fakeHttpURLConnection =
        new FakeHttpURLConnection(
            new URL(String.format(REALTIME_REGEX_URL, PROJECT_NUMBER, NAMESPACE)));

    configRealtimeHttpStream =
        new ConfigRealtimeHttpStream(
            firebaseApp,
            firebaseInstallations,
            context,
            NAMESPACE,
            configUpdateListenerSet,
            scheduledExecutorService,
            configFetchHandler);
  }

  @Test
  public void successfulConnection_andCorrectHttpRequestParams() throws Exception {
    ConfigRealtimeHttpStream spyConfigRealtimeHttpStream = spy(configRealtimeHttpStream);
    when(spyConfigRealtimeHttpStream.startAutoFetch(httpURLConnection)).thenReturn(configAutoFetch);
    when(configFetchHandler.getTemplateVersionNumber()).thenReturn(100L);
    fakeHttpURLConnection.setFakeResponse(new byte[1], "fetch-123");

    spyConfigRealtimeHttpStream.beginRealtimeHttpStream(fakeHttpURLConnection);

    assertThat(fakeHttpURLConnection.getRequestMethod()).isEqualTo("POST");

    JSONObject requestBody = new JSONObject(fakeHttpURLConnection.getOutputStream().toString());
    assertThat(requestBody.get("project")).isEqualTo(PROJECT_NUMBER);
    assertThat(requestBody.get("namespace")).isEqualTo(NAMESPACE);
    assertThat(requestBody.get("lastKnownVersionNumber")).isEqualTo("100");
    assertThat(requestBody.get("appId")).isEqualTo(APP_ID);
  }

  @Test
  public void successfulConnection_andCorrectHttpHeaders() throws Exception {
    ConfigRealtimeHttpStream spyConfigRealtimeHttpStream = spy(configRealtimeHttpStream);
    when(spyConfigRealtimeHttpStream.startAutoFetch(httpURLConnection)).thenReturn(configAutoFetch);
    when(configFetchHandler.getTemplateVersionNumber()).thenReturn(100L);
    fakeHttpURLConnection.setFakeResponse(new byte[1], "fetch-123");

    spyConfigRealtimeHttpStream.beginRealtimeHttpStream(fakeHttpURLConnection);

    Map<String, String> expectedHeaders = new HashMap();
    expectedHeaders.put(INSTALLATIONS_AUTH_TOKEN_HEADER, "10");
    expectedHeaders.put(API_KEY_HEADER, API_KEY);

    // Headers required for Android API Key Restrictions.
    expectedHeaders.put(X_ANDROID_PACKAGE_HEADER, context.getPackageName());
    expectedHeaders.put(X_ANDROID_CERT_HEADER, null);

    // Header to denote request is retryable on the server.
    expectedHeaders.put(X_GOOGLE_GFE_CAN_RETRY, "yes");

    // Header to tell server that client expects stream response
    expectedHeaders.put(X_ACCEPT_RESPONSE_STREAMING, "true");

    // Headers to denote that the request body is a JSONObject.
    expectedHeaders.put("Content-Type", "application/json");
    expectedHeaders.put("Accept", "application/json");

    assertThat(fakeHttpURLConnection.getRequestHeaders()).isEqualTo(expectedHeaders);
  }

  @Test
  public void beginRealtimeStream_andSuccessfulConnection() throws Exception {
    ConfigRealtimeHttpStream spyConfigRealtimeHttpStream = spy(configRealtimeHttpStream);
    when(spyConfigRealtimeHttpStream.startAutoFetch(httpURLConnection)).thenReturn(configAutoFetch);
    when(httpURLConnection.getResponseCode()).thenReturn(200);

    spyConfigRealtimeHttpStream.beginRealtimeHttpStream(httpURLConnection);

    assertThat(spyConfigRealtimeHttpStream.getRetryState()).isTrue();
    assertThat(spyConfigRealtimeHttpStream.getLastAttemptState()).isTrue();
  }

  @Test
  public void beginRealtimeStream_andUnsuccessfulConnectionWithRetry() throws Exception {
    ConfigRealtimeHttpStream spyConfigRealtimeHttpStream = spy(configRealtimeHttpStream);
    when(spyConfigRealtimeHttpStream.startAutoFetch(httpURLConnection)).thenReturn(configAutoFetch);
    when(httpURLConnection.getResponseCode()).thenReturn(502);

    spyConfigRealtimeHttpStream.beginRealtimeHttpStream(httpURLConnection);

    assertThat(spyConfigRealtimeHttpStream.getRetryState()).isTrue();
    assertThat(spyConfigRealtimeHttpStream.getLastAttemptState()).isFalse();
  }

  @Test
  public void beginRealtimeStream_andUnsuccessfulConnectionWithNoRetry() throws Exception {
    ConfigRealtimeHttpStream spyConfigRealtimeHttpStream = spy(configRealtimeHttpStream);
    when(spyConfigRealtimeHttpStream.startAutoFetch(httpURLConnection)).thenReturn(configAutoFetch);
    when(httpURLConnection.getResponseCode()).thenReturn(400);

    spyConfigRealtimeHttpStream.beginRealtimeHttpStream(httpURLConnection);

    assertThat(spyConfigRealtimeHttpStream.getRetryState()).isFalse();
    assertThat(spyConfigRealtimeHttpStream.getLastAttemptState()).isFalse();
    verify(mockStreamErrorEventListener).onError(any(FirebaseRemoteConfigException.class));
  }

  @Test
  public void beginRealtimeStream_andExceptionThrownWithRetry() throws Exception {
    ConfigRealtimeHttpStream spyConfigRealtimeHttpStream = spy(configRealtimeHttpStream);
    when(spyConfigRealtimeHttpStream.startAutoFetch(httpURLConnection)).thenReturn(configAutoFetch);
    when(httpURLConnection.getResponseCode()).thenThrow(IOException.class);

    spyConfigRealtimeHttpStream.beginRealtimeHttpStream(httpURLConnection);

    assertThat(spyConfigRealtimeHttpStream.getRetryState()).isTrue();
    assertThat(spyConfigRealtimeHttpStream.getLastAttemptState()).isFalse();
    verify(mockStreamErrorEventListener).onError(any(FirebaseRemoteConfigException.class));
  }

  @Test
  public void beginRealtimeStream_andEndWithDisconnection() throws Exception {
    ConfigRealtimeHttpStream spyConfigRealtimeHttpStream = spy(configRealtimeHttpStream);
    when(spyConfigRealtimeHttpStream.startAutoFetch(httpURLConnection)).thenReturn(configAutoFetch);
    when(httpURLConnection.getResponseCode()).thenReturn(200);

    spyConfigRealtimeHttpStream.beginRealtimeHttpStream(httpURLConnection);

    verify(httpURLConnection).disconnect();
    verify(httpURLConnection).getInputStream();
  }

  private static FirebaseApp initializeFirebaseApp(Context context) {
    FirebaseApp.clearInstancesForTest();

    return FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(APP_ID)
            .setProjectId(PROJECT_ID)
            .build());
  }
}
