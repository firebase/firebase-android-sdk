// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.EXPERIMENT_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.VARIANT_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.FETCH_REGEX_URL;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.ANALYTICS_USER_PROPERTIES;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.APP_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.COUNTRY_CODE;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.INSTANCE_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.INSTANCE_ID_TOKEN;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.LANGUAGE_CODE;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.PACKAGE_NAME;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.PLATFORM_VERSION;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.SDK_VERSION;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.TIME_ZONE;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.ENTRIES;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.EXPERIMENT_DESCRIPTIONS;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.STATE;
import static com.google.firebase.remoteconfig.testutil.Assert.assertThrows;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import android.os.Build;
import com.google.android.gms.common.util.MockClock;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.remoteconfig.BuildConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link ConfigFetchHttpClient}.
 *
 * @author Lucas Png
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigFetchHttpClientTest {
  private static final String API_KEY = "fake_api_key";
  private static final String FAKE_APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String PROJECT_NUMBER = "14368190084";
  private static final String INSTALLATION_ID_STRING = "'fL71_VyL3uo9jNMWu1L60S";
  private static final String INSTALLATION_AUTH_TOKEN_STRING =
      "eyJhbGciOiJF.eyJmaWQiOiJmaXMt.AB2LPV8wRQIhAPs4NvEgA3uhubH";
  private static final String DEFAULT_NAMESPACE = RemoteConfigComponent.DEFAULT_NAMESPACE;
  private static final String ETAG_FORMAT =
      "etag-" + PROJECT_NUMBER + "-" + DEFAULT_NAMESPACE + "-fetch-%d";
  private static final String FIRST_ETAG = String.format(ETAG_FORMAT, 1);
  private static final String SECOND_ETAG = String.format(ETAG_FORMAT, 2);

  private Context context;
  private ConfigFetchHttpClient configFetchHttpClient;
  private JSONObject hasChangeResponseBody;
  private JSONObject noChangeResponseBody;
  private FakeHttpURLConnection fakeHttpURLConnection;

  private MockClock mockClock;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    context = RuntimeEnvironment.application;
    configFetchHttpClient =
        new ConfigFetchHttpClient(
            context,
            FAKE_APP_ID,
            API_KEY,
            DEFAULT_NAMESPACE,
            /* connectTimeoutInSeconds= */ 10L,
            /* readTimeoutInSeconds= */ 10L);

    hasChangeResponseBody =
        new JSONObject()
            .put(STATE, "UPDATE")
            .put(
                ENTRIES,
                new JSONObject()
                    .put("key_1", "value_1")
                    .put("key2", "value_2")
                    .put("key_emoji", "\uD83C\uDDFA\uD83C\uDDF3"))
            .put(
                EXPERIMENT_DESCRIPTIONS,
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put(EXPERIMENT_ID, "Experiment1")
                            .put(VARIANT_ID, "variant_1")
                            .put("trigger_event", "event_trigger_1")
                            .put("experiment_start_time", "2017-10-30T21:46:40Z")
                            .put("trigger_timeout", "15552000s")
                            .put("time_to_live", "7776000s")));
    noChangeResponseBody = new JSONObject().put(STATE, "NO_CHANGE");

    fakeHttpURLConnection =
        new FakeHttpURLConnection(
            new URL(String.format(FETCH_REGEX_URL, PROJECT_NUMBER, DEFAULT_NAMESPACE)));

    mockClock = new MockClock(0L);
  }

  @Test
  public void createHttpURLConnection_returnsHttpURLConnectionWithValidFetchURL() throws Exception {
    HttpURLConnection urlConnection = configFetchHttpClient.createHttpURLConnection();

    assertThat(urlConnection.getURL().toString())
        .isEqualTo(String.format(FETCH_REGEX_URL, PROJECT_NUMBER, DEFAULT_NAMESPACE));
  }

  @Test
  public void fetch_newValues_responseSet() throws Exception {
    setServerResponseTo(hasChangeResponseBody, SECOND_ETAG);

    FetchResponse response = fetch(FIRST_ETAG);

    assertThat(response.getFetchTime()).isEqualTo(new Date(mockClock.currentTimeMillis()));
    assertThat(response.getLastFetchETag()).isEqualTo(SECOND_ETAG);
    assertThat(response.getFetchedConfigs().getConfigs().toString())
        .isEqualTo(hasChangeResponseBody.getJSONObject(ENTRIES).toString());
    assertThat(response.getFetchedConfigs().getAbtExperiments().toString())
        .isEqualTo(hasChangeResponseBody.getJSONArray(EXPERIMENT_DESCRIPTIONS).toString());
    assertThat(response.getFetchedConfigs().getFetchTime())
        .isEqualTo(new Date(mockClock.currentTimeMillis()));
  }

  @Test
  public void fetch_noChange_responseNotSet() throws Exception {
    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);

    FetchResponse response = fetch(SECOND_ETAG);

    assertThat(response.getLastFetchETag()).isNull();
    assertThat(response.getFetchedConfigs()).isNull();
  }

  @Test
  public void fetch_setsAllHeaders_sendsAllHeadersToServer() throws Exception {
    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);
    Map<String, String> customHeaders = ImmutableMap.of("x-enable-fetch", "true");

    Map<String, String> expectedHeaders = new HashMap<>();
    // eTag header for diffing.
    expectedHeaders.put("If-None-Match", FIRST_ETAG);
    // Common headers.
    expectedHeaders.put("X-Goog-Api-Key", API_KEY);
    expectedHeaders.put("X-Android-Package", context.getPackageName());
    expectedHeaders.put("X-Android-Cert", null);
    expectedHeaders.put("X-Google-GFE-Can-Retry", "yes");
    expectedHeaders.put("X-Goog-Firebase-Installations-Auth", INSTALLATION_AUTH_TOKEN_STRING);
    expectedHeaders.put("Content-Type", "application/json");
    expectedHeaders.put("Accept", "application/json");
    // Custom user-defined headers.
    expectedHeaders.putAll(customHeaders);

    fetch(FIRST_ETAG, /* userProperties= */ ImmutableMap.of(), customHeaders);

    assertThat(fakeHttpURLConnection.getRequestHeaders()).isEqualTo(expectedHeaders);
  }

  @Test
  public void fetch_setsAllElementsOfRequestBody_sendsRequestBodyToServer() throws Exception {
    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);
    Map<String, String> userProperties = ImmutableMap.of("up1", "hello", "up2", "world");

    fetch(FIRST_ETAG, userProperties);

    JSONObject requestBody = new JSONObject(fakeHttpURLConnection.getOutputStream().toString());
    assertThat(requestBody.get(INSTANCE_ID)).isEqualTo(INSTALLATION_ID_STRING);
    assertThat(requestBody.get(INSTANCE_ID_TOKEN)).isEqualTo(INSTALLATION_AUTH_TOKEN_STRING);
    assertThat(requestBody.get(APP_ID)).isEqualTo(FAKE_APP_ID);
    Locale locale = context.getResources().getConfiguration().locale;
    assertThat(requestBody.get(COUNTRY_CODE)).isEqualTo(locale.getCountry());
    assertThat(requestBody.get(LANGUAGE_CODE)).isEqualTo(locale.toLanguageTag());
    assertThat(requestBody.getInt(PLATFORM_VERSION)).isEqualTo(android.os.Build.VERSION.SDK_INT);
    assertThat(requestBody.get(TIME_ZONE)).isEqualTo(TimeZone.getDefault().getID());
    assertThat(requestBody.get(PACKAGE_NAME)).isEqualTo(context.getPackageName());
    assertThat(requestBody.get(SDK_VERSION)).isEqualTo(BuildConfig.VERSION_NAME);
    assertThat(requestBody.getJSONObject(ANALYTICS_USER_PROPERTIES).toString())
        .isEqualTo(new JSONObject(userProperties).toString());
  }

  @Test
  public void fetch_requestEncodesLanguageSubtags() throws Exception {
    String languageTag = "zh-Hant-TW"; // Taiwan Chinese in traditional script
    context.getResources().getConfiguration().setLocale(Locale.forLanguageTag(languageTag));

    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);

    Map<String, String> userProperties = ImmutableMap.of("up1", "hello", "up2", "world");
    fetch(FIRST_ETAG, userProperties);

    JSONObject requestBody = new JSONObject(fakeHttpURLConnection.getOutputStream().toString());
    assertThat(requestBody.get(LANGUAGE_CODE)).isEqualTo(languageTag);
  }

  @Test
  @Config(sdk = Build.VERSION_CODES.KITKAT /* 19 */)
  public void fetch_localeUsesToStringBelowLollipop() throws Exception {
    String languageTag = "zh-Hant-TW"; // Taiwan Chinese in traditional script
    String languageString = "zh_TW_#Hant";
    context.getResources().getConfiguration().setLocale(Locale.forLanguageTag(languageTag));

    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);

    Map<String, String> userProperties = ImmutableMap.of("up1", "hello", "up2", "world");
    fetch(FIRST_ETAG, userProperties);

    JSONObject requestBody = new JSONObject(fakeHttpURLConnection.getOutputStream().toString());
    assertThat(requestBody.get(LANGUAGE_CODE)).isEqualTo(languageString);
  }

  @Test
  public void fetch_installationIdIsNull_throwsFRCClientException() throws Exception {
    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);

    FirebaseRemoteConfigClientException frcException =
        assertThrows(FirebaseRemoteConfigClientException.class, () -> fetchWithoutInstallationId());

    assertThat(frcException).hasMessageThat().contains("installation id is null");
  }

  @Test
  public void fetch_installationAuthTokenIsNull_doesNotThrowException() throws Exception {
    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);

    FetchResponse fetchResponse = fetchWithoutInstallationAuthToken();

    assertWithMessage("Fetch() failed with null installation auth token!")
        .that(fetchResponse)
        .isNotNull();
  }

  @Test
  public void fetch_setsTimeouts_urlConnectionHasTimeouts() throws Exception {
    configFetchHttpClient =
        new ConfigFetchHttpClient(
            context,
            APP_ID,
            API_KEY,
            DEFAULT_NAMESPACE,
            /* connectTimeoutInSeconds= */ 15L,
            /* readTimeoutInSeconds= */ 20L);
    setServerResponseTo(noChangeResponseBody, SECOND_ETAG);

    fetch(FIRST_ETAG);

    assertThat(fakeHttpURLConnection.getConnectTimeout()).isEqualTo(15000L);
    assertThat(fakeHttpURLConnection.getReadTimeout()).isEqualTo(20000L);
  }

  @Test
  public void fetch_serverReturnsException_throwsFirebaseRemoteConfigException() {
    setServerResponseTo404Error();

    FirebaseRemoteConfigException exception =
        assertThrows(FirebaseRemoteConfigException.class, () -> fetch(FIRST_ETAG));

    assertThat(exception).hasMessageThat().isEqualTo("Bad Request");
  }

  private FetchResponse fetch(String eTag) throws Exception {
    return configFetchHttpClient.fetch(
        fakeHttpURLConnection,
        INSTALLATION_ID_STRING,
        INSTALLATION_AUTH_TOKEN_STRING,
        /* analyticsUserProperties= */ ImmutableMap.of(),
        eTag,
        /* customHeaders= */ ImmutableMap.of(),
        /* currentTime= */ new Date(mockClock.currentTimeMillis()));
  }

  private FetchResponse fetch(String eTag, Map<String, String> userProperties) throws Exception {
    return configFetchHttpClient.fetch(
        fakeHttpURLConnection,
        INSTALLATION_ID_STRING,
        INSTALLATION_AUTH_TOKEN_STRING,
        userProperties,
        eTag,
        /* customHeaders= */ ImmutableMap.of(),
        new Date(mockClock.currentTimeMillis()));
  }

  private FetchResponse fetch(
      String eTag, Map<String, String> userProperties, Map<String, String> customHeaders)
      throws Exception {
    return configFetchHttpClient.fetch(
        fakeHttpURLConnection,
        INSTALLATION_ID_STRING,
        INSTALLATION_AUTH_TOKEN_STRING,
        userProperties,
        eTag,
        customHeaders,
        new Date(mockClock.currentTimeMillis()));
  }

  private FetchResponse fetchWithoutInstallationId() throws Exception {
    return configFetchHttpClient.fetch(
        fakeHttpURLConnection,
        /* installationId= */ null,
        INSTALLATION_AUTH_TOKEN_STRING,
        /* analyticsUserProperties= */ ImmutableMap.of(),
        /* lastFetchETag= */ "bogus-etag",
        /* customHeaders= */ ImmutableMap.of(),
        new Date(mockClock.currentTimeMillis()));
  }

  private FetchResponse fetchWithoutInstallationAuthToken() throws Exception {
    return configFetchHttpClient.fetch(
        fakeHttpURLConnection,
        INSTALLATION_ID_STRING,
        /* installationAuthToken= */ null,
        /* analyticsUserProperties= */ ImmutableMap.of(),
        /* lastFetchETag= */ "bogus-etag",
        /* customHeaders= */ ImmutableMap.of(),
        new Date(mockClock.currentTimeMillis()));
  }

  private void setServerResponseTo(JSONObject requestBody, String eTag) {
    fakeHttpURLConnection.setFakeResponse(requestBody.toString().getBytes(Charsets.UTF_8), eTag);
  }

  private void setServerResponseTo404Error() {
    // If no response is set, the fakeHttpURLConnection returns the 404 status code.
  }
}
