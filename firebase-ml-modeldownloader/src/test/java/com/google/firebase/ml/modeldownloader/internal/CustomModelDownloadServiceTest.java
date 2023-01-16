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

package com.google.firebase.ml.modeldownloader.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.FirebaseMlException;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link CustomModelDownloadService}. */
@RunWith(RobolectricTestRunner.class)
public class CustomModelDownloadServiceTest {

  private static final long TEST_EXPIRATION_IN_MS = 1604512594215L;
  private static final String TEST_EXPIRATION_TIMESTAMP = "2020-11-04T17:56:34.215Z";
  private static final String INCORRECT_EXPIRATION_TIMESTAMP = "2345";
  private static final String PROJECT_ID = "md-androidtest";
  private static final String MODEL_NAME = "ModelDownloaderTest";
  private static final String API_KEY = "my_firebase_project_api_key";
  private static final String PACKAGE_FINGERPRINT_HASH = "my_firebase_project_fingerprint_hash";
  private static final String MODEL_HASH = "ModelHash_392043";

  private static final String TEST_ENDPOINT = "http://localhost:8979";
  private static final String INSTALLATION_TOKEN = "installation_token-ORBShD2yhR";
  private static final InstallationTokenResult INSTALLATION_TOKEN_RESULT =
      new InstallationTokenResult() {
        @NonNull
        @Override
        public String getToken() {
          return INSTALLATION_TOKEN;
        }

        @Override
        public long getTokenExpirationTimestamp() {
          return 0;
        }

        @Override
        public long getTokenCreationTimestamp() {
          return 0;
        }

        @Override
        public Builder toBuilder() {
          return null;
        }
      };

  private static final String DOWNLOAD_URI =
      "https://storage.google.com/myproject/modelfile.tflite";

  private static final Long FILE_SIZE = 562336L;

  private static final String RESPONSE_BODY =
      "{ \"expireTime\" : \" "
          + TEST_EXPIRATION_TIMESTAMP
          + "\","
          + "\"sizeBytes\": \""
          + FILE_SIZE
          + "\","
          + "\"modelFormat\": \"TFLITE\","
          + "\"downloadUri\": \""
          + DOWNLOAD_URI
          + "\""
          + "}";

  @Rule public WireMockRule wireMockRule = new WireMockRule(8979);

  private ExecutorService directExecutor;
  private FirebaseInstallationsApi installationsApiMock;
  @Mock private FirebaseMlLogger mockEventLogger;

  private final ModelFileDownloadService modelFileDownloadService =
      mock(ModelFileDownloadService.class);
  private final CustomModel.Factory modelFactory =
      (name, modelHash, fileSize, downloadId, localFilePath, downloadUrl, downloadUrlExpiry) ->
          new CustomModel(
              modelFileDownloadService,
              name,
              modelHash,
              fileSize,
              downloadId,
              localFilePath,
              downloadUrl,
              downloadUrlExpiry);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    directExecutor = MoreExecutors.newDirectExecutorService();
    installationsApiMock = mock(FirebaseInstallationsApi.class);
    when(installationsApiMock.getToken(anyBoolean()))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));
    // ignore logging
    doNothing().when(mockEventLogger).logDownloadEventWithExactDownloadTime(any(), any(), any());
    doNothing().when(mockEventLogger).logDownloadFailureWithReason(any(), anyBoolean(), anyInt());
  }

  @Test
  public void parseTokenExpirationTimestamp_successful() {
    long actual =
        CustomModelDownloadService.parseTokenExpirationTimestamp(TEST_EXPIRATION_TIMESTAMP);

    assertWithMessage("Expected time to be properly formatted.")
        .that(actual)
        .isEqualTo(TEST_EXPIRATION_IN_MS);
  }

  @Test
  public void parseTokenExpirationTimestamp_failed() {
    long actual =
        CustomModelDownloadService.parseTokenExpirationTimestamp(INCORRECT_EXPIRATION_TIMESTAMP);

    assertWithMessage("Invalid times should return 0.").that(actual).isEqualTo(0);
  }

  @Test
  public void downloadService_noHashSuccess() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader(CustomModelDownloadService.ETAG_HEADER, MODEL_HASH)
                    .withBody(RESPONSE_BODY)));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getNewDownloadUrlWithExpiry(PROJECT_ID, MODEL_NAME);

    Assert.assertEquals(
        modelTask.getResult(),
        modelFactory.create(
            MODEL_NAME, MODEL_HASH, FILE_SIZE, DOWNLOAD_URI, TEST_EXPIRATION_IN_MS));

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));
  }

  @Test
  public void downloadService_fingerPrintHashNull_NoCertHeader() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader(CustomModelDownloadService.ETAG_HEADER, MODEL_HASH)
                    .withBody(RESPONSE_BODY)));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            null,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getNewDownloadUrlWithExpiry(PROJECT_ID, MODEL_NAME);

    Assert.assertEquals(
        modelTask.getResult(),
        modelFactory.create(
            MODEL_NAME, MODEL_HASH, FILE_SIZE, DOWNLOAD_URI, TEST_EXPIRATION_IN_MS));

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withoutHeader(CustomModelDownloadService.X_ANDROID_CERT_HEADER));
  }

  @Test
  public void downloadService_withHashSuccess_noMatch() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader(CustomModelDownloadService.ETAG_HEADER, MODEL_HASH)
                    .withBody(RESPONSE_BODY)));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertEquals(
        modelTask.getResult(),
        modelFactory.create(
            MODEL_NAME, MODEL_HASH, FILE_SIZE, DOWNLOAD_URI, TEST_EXPIRATION_IN_MS));

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));
  }

  @Test
  public void downloadService_withHashSuccess_match() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .withHeader(CustomModelDownloadService.IF_NONE_MATCH_HEADER_KEY, equalTo(MODEL_HASH))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_NOT_MODIFIED) // match found
                    .withHeader(CustomModelDownloadService.ETAG_HEADER, MODEL_HASH)));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertNull(modelTask.getResult());

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));
  }

  @Test
  public void downloadService_modelNotFound() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .withHeader(CustomModelDownloadService.IF_NONE_MATCH_HEADER_KEY, equalTo(MODEL_HASH))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_NOT_FOUND) // not found
                    .withBody(
                        "{\"error\": "
                            + "{\"message\":\"Request entity was not found.\", "
                            + "\"status\":\"NOT_FOUND\",\"code\":404}}")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(), FirebaseMlException.NOT_FOUND);

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));
    verify(mockEventLogger, never()).logModelInfoRetrieverFailure(any(), any(), anyInt());
  }

  @Test
  public void downloadService_badRequest() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .withHeader(CustomModelDownloadService.IF_NONE_MATCH_HEADER_KEY, equalTo(MODEL_HASH))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withBody(
                        "{\"error\": "
                            + "{\"message\":\"Request bad.\", "
                            + "\"status\":\"BAD_REQUEST\",\"code\":400}}")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.INVALID_ARGUMENT);

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));

    verify(mockEventLogger, times(1))
        .logModelInfoRetrieverFailure(
            any(),
            eq(ErrorCode.MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS),
            eq(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void downloadService_forbidden() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .withHeader(CustomModelDownloadService.IF_NONE_MATCH_HEADER_KEY, equalTo(MODEL_HASH))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_FORBIDDEN)
                    .withBody(
                        "{\"error\": "
                            + "{\"message\":\"Request no valid.\", "
                            + "\"status\":\"FORBIDDEN\",\"code\":403}}")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.PERMISSION_DENIED);

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));

    verify(mockEventLogger, times(1))
        .logModelInfoRetrieverFailure(
            any(),
            eq(ErrorCode.MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS),
            eq(HttpURLConnection.HTTP_FORBIDDEN));
  }

  @Test
  public void downloadService_internalError() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .withHeader(CustomModelDownloadService.IF_NONE_MATCH_HEADER_KEY, equalTo(MODEL_HASH))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_INTERNAL_ERROR)
                    .withBody(
                        "{\"error\": "
                            + "{\"message\":\"Request cannot reach server.\", "
                            + "\"status\":\"INTERNAL_ERROR\",\"code\":500}}")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(), FirebaseMlException.INTERNAL);

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));

    verify(mockEventLogger, times(1))
        .logModelInfoRetrieverFailure(
            any(),
            eq(ErrorCode.MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS),
            eq(HttpURLConnection.HTTP_INTERNAL_ERROR));
  }

  @Test
  public void downloadService_tooManyRequest() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .withHeader(CustomModelDownloadService.IF_NONE_MATCH_HEADER_KEY, equalTo(MODEL_HASH))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withBody(
                        "{\"error\": "
                            + "{\"message\":\"Request could not be process resource exhausted.\", "
                            + "\"status\":\"RESOURCE_EXHAUSTED\",\"code\":429}}")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.RESOURCE_EXHAUSTED);

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));

    verify(mockEventLogger, times(1))
        .logModelInfoRetrieverFailure(
            any(), eq(ErrorCode.MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS), eq(429));
  }

  @Test
  public void downloadService_authenticationIssue() {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, "", PROJECT_ID, MODEL_NAME);
    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(
                CustomModelDownloadService.CONTENT_TYPE,
                equalTo(CustomModelDownloadService.APPLICATION_JSON))
            .withHeader(CustomModelDownloadService.IF_NONE_MATCH_HEADER_KEY, equalTo(MODEL_HASH))
            .willReturn(
                aResponse()
                    .withStatus(HttpURLConnection.HTTP_UNAUTHORIZED) // not authorized
                    .withBody(
                        "{\"error\": {\"message\":\"Request is missing required authentication"
                            + " credential.\", \"status\":\"UNAUTHORIZED\",\"code\":401}}")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.PERMISSION_DENIED);
    Assert.assertTrue(
        modelTask
            .getException()
            .getMessage()
            .contains("Request is missing required authentication credential"));

    WireMock.verify(
        getRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .withHeader(CustomModelDownloadService.API_KEY_HEADER, equalTo(API_KEY))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_PACKAGE_HEADER,
                equalTo(ApplicationProvider.getApplicationContext().getPackageName()))
            .withHeader(
                CustomModelDownloadService.X_ANDROID_CERT_HEADER,
                equalTo(PACKAGE_FINGERPRINT_HASH)));

    verify(mockEventLogger, times(1))
        .logModelInfoRetrieverFailure(
            any(),
            eq(ErrorCode.MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS),
            eq(HttpURLConnection.HTTP_UNAUTHORIZED));
  }

  @Test
  public void downloadService_unauthenticatedToken() {
    when(installationsApiMock.getToken(anyBoolean()))
        .thenReturn(Tasks.forException(new IllegalArgumentException("bad request")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.UNAUTHENTICATED);
    Assert.assertTrue(modelTask.getException().getMessage().contains("authentication error"));

    verify(mockEventLogger, times(1))
        .logDownloadFailureWithReason(
            any(), eq(false), eq(ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED.getValue()));
  }

  @Test
  public void downloadService_nullModelHashPassedUnauthenticatedToken() {
    when(installationsApiMock.getToken(anyBoolean()))
        .thenReturn(Tasks.forException(new IllegalArgumentException("bad request")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, null);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.UNAUTHENTICATED);
    Assert.assertTrue(modelTask.getException().getMessage().contains("authentication error"));

    ArgumentCaptor<CustomModel> captor = ArgumentCaptor.forClass(CustomModel.class);
    verify(mockEventLogger, times(1))
        .logDownloadFailureWithReason(
            captor.capture(),
            eq(false),
            eq(ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED.getValue()));
    assertThat(captor.getValue().getModelHash()).isNotNull();
  }

  @Test
  public void downloadService_malFormedUrl() {
    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            "https7://localhost:8989/barUrl",
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.INVALID_ARGUMENT);
    Assert.assertTrue(modelTask.getException().getMessage().contains("download service"));

    verify(mockEventLogger, times(1))
        .logDownloadFailureWithReason(
            any(), eq(false), eq(ErrorCode.MODEL_INFO_DOWNLOAD_CONNECTION_FAILED.getValue()));
  }

  @Test
  public void downloadService_unauthenticatedToken_noNetworkConnection() {
    when(installationsApiMock.getToken(anyBoolean()))
        .thenReturn(Tasks.forException(new UnknownHostException("no connection")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(
            ApplicationProvider.getApplicationContext(),
            () -> installationsApiMock,
            directExecutor,
            API_KEY,
            PACKAGE_FINGERPRINT_HASH,
            TEST_ENDPOINT,
            mockEventLogger,
            modelFactory);

    Task<CustomModel> modelTask = service.getCustomModelDetails(PROJECT_ID, MODEL_NAME, MODEL_HASH);

    Assert.assertTrue(modelTask.getException() instanceof FirebaseMlException);
    Assert.assertEquals(
        ((FirebaseMlException) modelTask.getException()).getCode(),
        FirebaseMlException.NO_NETWORK_CONNECTION);
    Assert.assertTrue(modelTask.getException().getMessage().contains("no internet connection"));

    verify(mockEventLogger, times(1))
        .logDownloadFailureWithReason(
            any(), eq(false), eq(ErrorCode.NO_NETWORK_CONNECTION.getValue()));
  }
}
