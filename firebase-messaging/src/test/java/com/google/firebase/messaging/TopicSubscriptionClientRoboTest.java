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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TopicSubscriptionClientRoboTest {

  private static final String TEST_TOKEN = "test_token";
  private static final String TEST_FID = "test_fid";
  private static final String TEST_PROJECT_ID = "test_project_id";
  private static final String TEST_API_KEY = "test_api_key";
  private static final String TEST_TOPIC = "test_topic";

  @Mock private FirebaseMessaging mockFirebaseMessaging;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallationsApi;
  @Mock private Metadata mockMetadata;
  @Mock private HttpURLConnection mockConnection;

  private Context context;
  private FirebaseApp firebaseApp;
  private TopicSubscriptionClient client;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();

    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApplicationId("1:1234567890:android:321abc456def7890")
            .setProjectId(TEST_PROJECT_ID)
            .setApiKey(TEST_API_KEY)
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

    // Spy on the client to mock createConnection
    client =
        spy(
            new TopicSubscriptionClient(
                firebaseApp, mockFirebaseMessaging, mockFirebaseInstallationsApi));
    doReturn(mockConnection).when(client).createConnection(any(URL.class));
  }

  @Test
  public void testSubscribe_success() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(200);

    runOnBackground(() -> client.subscribe(TEST_TOPIC));

    // Verify no exception is thrown
  }

  @Test
  public void testSubscribe_triggersFcmRegistration() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(200);

    runOnBackground(() -> client.subscribe(TEST_TOPIC));

    verify(mockFirebaseMessaging).blockingRegister(false);
  }

  @Test
  public void testSubscribe_failure404_throwsIOException() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(404);
    when(mockConnection.getResponseMessage()).thenReturn("Not Found");

    IOException exception =
        assertThrows(IOException.class, () -> runOnBackground(() -> client.subscribe(TEST_TOPIC)));
    assertThat(exception.getMessage()).contains("Topic subscribe failed: Not Found");
  }

  @Test
  public void testSubscribe_failure500_throwsInternalServerError() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(500);

    IOException exception =
        assertThrows(IOException.class, () -> runOnBackground(() -> client.subscribe(TEST_TOPIC)));
    assertThat(exception.getMessage())
        .isEqualTo(TopicSubscriptionClient.ERROR_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testUnsubscribe_success() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(200);

    runOnBackground(() -> client.unsubscribe(TEST_TOPIC));
  }

  @Test
  public void testUnsubscribe_failure403_throwsIOException() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(403);
    when(mockConnection.getResponseMessage()).thenReturn("Forbidden");

    IOException exception =
        assertThrows(
            IOException.class, () -> runOnBackground(() -> client.unsubscribe(TEST_TOPIC)));
    assertThat(exception.getMessage()).contains("Topic unsubscribe failed: Forbidden");
  }

  @Test
  public void testUnsubscribe_failure503_throwsUnknownStatus() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(503);

    IOException exception =
        assertThrows(
            IOException.class, () -> runOnBackground(() -> client.unsubscribe(TEST_TOPIC)));
    assertThat(exception.getMessage())
        .isEqualTo(TopicSubscriptionClient.ERROR_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testSubscribe_success_closesInputStreamAndDisconnects() throws Exception {
    CloseTrackingInputStream inputStream = new CloseTrackingInputStream();
    when(mockConnection.getResponseCode()).thenReturn(200);
    when(mockConnection.getInputStream()).thenReturn(inputStream);

    runOnBackground(() -> client.subscribe(TEST_TOPIC));

    assertThat(inputStream.isClosed()).isTrue();
    verify(mockConnection).disconnect();
  }

  @Test
  public void testSubscribe_failure404_closesErrorStreamAndDisconnects() throws Exception {
    CloseTrackingInputStream errorStream = new CloseTrackingInputStream();
    when(mockConnection.getResponseCode()).thenReturn(404);
    when(mockConnection.getResponseMessage()).thenReturn("Not Found");
    when(mockConnection.getInputStream()).thenThrow(new IOException("Error"));
    when(mockConnection.getErrorStream()).thenReturn(errorStream);

    assertThrows(IOException.class, () -> runOnBackground(() -> client.subscribe(TEST_TOPIC)));

    assertThat(errorStream.isClosed()).isTrue();
    verify(mockConnection).disconnect();
  }

  @Test
  public void testSubscribe_percentCharactersInTopic_arePathEncodedOnce() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(200);
    String topic = "email.example.name%2Bafterplus%40example.com";

    runOnBackground(() -> client.subscribe(topic));

    assertThat(capturedRequestUrl().getPath())
        .isEqualTo(
            topicSubscriptionPath("email.example.name%252Bafterplus%2540example.com", "subscribe"));
  }

  @Test
  public void testSubscribe_documentedTopicCharset_encodesOnlyPercent() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(200);
    String topic = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~%";

    runOnBackground(() -> client.subscribe(topic));

    String path = capturedRequestUrl().getPath();
    assertThat(path)
        .isEqualTo(
            topicSubscriptionPath(
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~%25",
                "subscribe"));
    assertThat(path).contains("-_.~%25");
    assertThat(path).doesNotContain("%2D");
    assertThat(path).doesNotContain("%5F");
    assertThat(path).doesNotContain("%2E");
    assertThat(path).doesNotContain("%7E");
  }

  @Test
  public void testUnsubscribe_usesSameTopicEncodingAsSubscribe() throws Exception {
    when(mockConnection.getResponseCode()).thenReturn(200);
    String topic = "email.example.name%2Bafterplus%40example.com";
    String encodedTopic = "email.example.name%252Bafterplus%2540example.com";

    runOnBackground(() -> client.subscribe(topic));
    String subscribePath = capturedRequestUrl().getPath();
    clearInvocations(client);
    runOnBackground(() -> client.unsubscribe(topic));
    String unsubscribePath = capturedRequestUrl().getPath();

    assertThat(subscribePath).isEqualTo(topicSubscriptionPath(encodedTopic, "subscribe"));
    assertThat(unsubscribePath).isEqualTo(topicSubscriptionPath(encodedTopic, "unsubscribe"));
    assertThat(subscribePath).endsWith(":subscribe");
    assertThat(unsubscribePath).endsWith(":unsubscribe");
    assertThat(subscribePath).doesNotContain("%3Asubscribe");
    assertThat(unsubscribePath).doesNotContain("%3Aunsubscribe");
  }

  private URL capturedRequestUrl() throws IOException {
    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    verify(client).createConnection(urlCaptor.capture());
    return urlCaptor.getValue();
  }

  private static String topicSubscriptionPath(String encodedTopic, String operation) {
    return "/v1/projects/"
        + TEST_PROJECT_ID
        + "/registrations/"
        + TEST_FID
        + "/topicSubscriptions/"
        + encodedTopic
        + ":"
        + operation;
  }

  private static class CloseTrackingInputStream extends ByteArrayInputStream {
    private boolean isClosed = false;

    CloseTrackingInputStream() {
      super(new byte[0]);
    }

    @Override
    public void close() throws IOException {
      isClosed = true;
      super.close();
    }

    boolean isClosed() {
      return isClosed;
    }
  }

  private void runOnBackground(ThrowingRunnable runnable) throws Exception {
    Future<?> future =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  try {
                    runnable.run();
                  } catch (Throwable t) {
                    if (t instanceof Exception) {
                      throw (Exception) t;
                    } else {
                      throw new RuntimeException(t);
                    }
                  }
                  return null;
                });
    try {
      future.get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Exception) {
        throw (Exception) e.getCause();
      } else {
        throw e;
      }
    }
  }

  interface ThrowingRunnable {
    void run() throws Throwable;
  }
}
