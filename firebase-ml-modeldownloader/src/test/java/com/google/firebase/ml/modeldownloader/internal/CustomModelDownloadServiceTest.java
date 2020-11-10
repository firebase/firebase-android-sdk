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
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link CustomModelDownloadService}. */
@RunWith(RobolectricTestRunner.class)
public class CustomModelDownloadServiceTest {

  private final String TEST_EXPIRATION_TIMESTAMP = "2020-11-04T17:56:34.215Z";
  private final long TEST_EXPIRATION_IN_MS = 1604530594215L;
  private final String INCORRECT_EXPIRATION_TIMESTAMP = "2345";
  private final String PROJECT_ID = "md-androidtest";
  private final String MODEL_NAME = "ModelDownloaderTest";
  private final String API_KEY = "replace_with_real";

  private static final String INSTALLATION_TOKEN = "installation_token";
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

  @Rule public WireMockRule wireMockRule = new WireMockRule(8999);

  private ExecutorService directExecutor;
  private FirebaseInstallationsApi installationsApiMock;

  @Before
  public void setUp() throws Exception {
    directExecutor = MoreExecutors.newDirectExecutorService();
    installationsApiMock = mock(FirebaseInstallationsApi.class);
    when(installationsApiMock.getToken(anyBoolean()))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));
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
  public void testDownloadService_noHashSuccess() throws Exception {
    String downloadPath =
        String.format(CustomModelDownloadService.DOWNLOAD_MODEL_REGEX, PROJECT_ID, MODEL_NAME);

    System.out.println("url stub: " + urlEqualTo(downloadPath));

    System.out.println(
        "stubfor: "
            + get(urlEqualTo(downloadPath))
                .withHeader(
                    CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                    equalTo(INSTALLATION_TOKEN)));

    stubFor(
        get(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("Hello world")));

    CustomModelDownloadService service =
        new CustomModelDownloadService(installationsApiMock, directExecutor, API_KEY);

    service.getNewDownloadUrlWithExpiry(PROJECT_ID, MODEL_NAME);

    verify(
        postRequestedFor(urlEqualTo(downloadPath))
            .withHeader(
                CustomModelDownloadService.INSTALLATIONS_AUTH_TOKEN_HEADER,
                equalTo(INSTALLATION_TOKEN)));
  }
}
