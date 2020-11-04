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

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.FETCH_REGEX_URL;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.ANALYTICS_USER_PROPERTIES;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.APP_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.APP_VERSION;
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
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.PERSONALIZATION_METADATA;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.STATE;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;
import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.firebase.remoteconfig.BuildConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigServerException;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Lightweight client for fetching data from the Firebase Remote Config server.
 *
 * @author Lucas Png
 */
public class ConfigFetchHttpClient {
  private static final String API_KEY_HEADER = "X-Goog-Api-Key";
  private static final String ETAG_HEADER = "ETag";
  private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
  private static final String X_ANDROID_PACKAGE_HEADER = "X-Android-Package";
  private static final String X_ANDROID_CERT_HEADER = "X-Android-Cert";
  private static final String X_GOOGLE_GFE_CAN_RETRY = "X-Google-GFE-Can-Retry";
  private static final String INSTALLATIONS_AUTH_TOKEN_HEADER =
      "X-Goog-Firebase-Installations-Auth";

  private final Context context;
  private final String appId;
  private final String apiKey;
  private final String projectNumber;
  private final String namespace;
  private final long connectTimeoutInSeconds;
  private final long readTimeoutInSeconds;

  /** Creates a client for {@link #fetch}ing data from the Firebase Remote Config server. */
  public ConfigFetchHttpClient(
      Context context,
      String appId,
      String apiKey,
      String namespace,
      long connectTimeoutInSeconds,
      long readTimeoutInSeconds) {
    this.context = context;
    this.appId = appId;
    this.apiKey = apiKey;
    this.projectNumber = extractProjectNumberFromAppId(appId);
    this.namespace = namespace;
    this.connectTimeoutInSeconds = connectTimeoutInSeconds;
    this.readTimeoutInSeconds = readTimeoutInSeconds;
  }

  /** Used to verify that the timeout is being set correctly. */
  @VisibleForTesting
  public long getConnectTimeoutInSeconds() {
    return connectTimeoutInSeconds;
  }

  /** Used to verify that the timeout is being set correctly. */
  @VisibleForTesting
  public long getReadTimeoutInSeconds() {
    return readTimeoutInSeconds;
  }

  /**
   * A regular expression for the GMP App Id format. The first group (index 1) is the project
   * number.
   */
  private static final Pattern GMP_APP_ID_PATTERN =
      Pattern.compile("^[^:]+:([0-9]+):(android|ios|web):([0-9a-f]+)");

  private static String extractProjectNumberFromAppId(String gmpAppId) {
    Matcher matcher = GMP_APP_ID_PATTERN.matcher(gmpAppId);
    return matcher.matches() ? matcher.group(1) : null;
  }

  /**
   * Initializes a {@link HttpURLConnection} for fetching data from the Firebase Remote Config
   * server.
   */
  HttpURLConnection createHttpURLConnection() throws FirebaseRemoteConfigException {
    try {
      URL url = new URL(getFetchUrl(projectNumber, namespace));
      return (HttpURLConnection) url.openConnection();
    } catch (IOException e) {
      throw new FirebaseRemoteConfigException(e.getMessage());
    }
  }

  /**
   * Returns a {@link JSONObject} that contains the latest fetched ETag and a status field that
   * denotes if the Firebase Remote Config (FRC) server has updated configs or A/B Testing
   * experiments. If there has been a change since the last fetch, the {@link JSONObject} also
   * contains an "entries" field with parameters fetched from the FRC server.
   *
   * @param urlConnection a {@link HttpURLConnection} created by a call to {@link
   *     #createHttpURLConnection}.
   * @param installationId the Firebase installation ID that identifies a Firebase App Instance.
   * @param installationAuthToken a valid Firebase installation auth token that authenticates a
   *     Firebase App Instance.
   * @param analyticsUserProperties a map of Google Analytics User Properties and the device's
   *     corresponding values.
   * @param lastFetchETag the ETag returned by the last successful fetch call to the FRC server. The
   *     server uses this ETag to determine if there has been a change in the response body since
   *     the last fetch.
   * @param customHeaders custom HTTP headers that will be sent to the FRC server.
   * @param currentTime the current time on the device that is performing the fetch.
   */
  // TODO(issues/263): Set custom headers in ConfigFetchHttpClient's constructor.
  @Keep
  FetchResponse fetch(
      HttpURLConnection urlConnection,
      String installationId,
      String installationAuthToken,
      Map<String, String> analyticsUserProperties,
      String lastFetchETag,
      Map<String, String> customHeaders,
      Date currentTime)
      throws FirebaseRemoteConfigException {
    setUpUrlConnection(urlConnection, lastFetchETag, installationAuthToken, customHeaders);

    String fetchResponseETag;
    JSONObject fetchResponse;
    try {
      byte[] requestBody =
          createFetchRequestBody(installationId, installationAuthToken, analyticsUserProperties)
              .toString()
              .getBytes("utf-8");
      setFetchRequestBody(urlConnection, requestBody);

      urlConnection.connect();

      int responseCode = urlConnection.getResponseCode();
      if (responseCode != 200) {
        throw new FirebaseRemoteConfigServerException(
            responseCode, urlConnection.getResponseMessage());
      }
      fetchResponseETag = urlConnection.getHeaderField(ETAG_HEADER);
      fetchResponse = getFetchResponseBody(urlConnection);
    } catch (IOException | JSONException e) {
      throw new FirebaseRemoteConfigClientException(
          "The client had an error while calling the backend!", e);
    } finally {
      urlConnection.disconnect();
      // Explicitly close the input stream due to a bug in the Android okhttp implementation.
      try {
        urlConnection.getInputStream().close();
      } catch (IOException e) {
      }
    }

    if (!backendHasUpdates(fetchResponse)) {
      return FetchResponse.forBackendHasNoUpdates(currentTime);
    }

    ConfigContainer fetchedConfigs = extractConfigs(fetchResponse, currentTime);
    return FetchResponse.forBackendUpdatesFetched(fetchedConfigs, fetchResponseETag);
  }

  private void setUpUrlConnection(
      HttpURLConnection urlConnection,
      String lastFetchEtag,
      String installationAuthToken,
      Map<String, String> customHeaders) {
    urlConnection.setDoOutput(true);
    urlConnection.setConnectTimeout((int) SECONDS.toMillis(connectTimeoutInSeconds));
    urlConnection.setReadTimeout((int) SECONDS.toMillis(readTimeoutInSeconds));

    // Send the last successful Fetch ETag to the FRC Server to calculate if there has been any
    // change in the Fetch Response since the last fetch call.
    urlConnection.setRequestProperty(IF_NONE_MATCH_HEADER, lastFetchEtag);

    setCommonRequestHeaders(urlConnection, installationAuthToken);
    setCustomRequestHeaders(urlConnection, customHeaders);
  }

  private String getFetchUrl(String projectNumber, String namespace) {
    return String.format(FETCH_REGEX_URL, projectNumber, namespace);
  }

  private void setCommonRequestHeaders(
      HttpURLConnection urlConnection, String installationAuthToken) {
    urlConnection.setRequestProperty(API_KEY_HEADER, apiKey);

    // Headers required for Android API Key Restrictions.
    urlConnection.setRequestProperty(X_ANDROID_PACKAGE_HEADER, context.getPackageName());
    urlConnection.setRequestProperty(X_ANDROID_CERT_HEADER, getFingerprintHashForPackage());

    // Header to denote request is retryable on the server.
    urlConnection.setRequestProperty(X_GOOGLE_GFE_CAN_RETRY, "yes");

    // Header for FIS auth token
    urlConnection.setRequestProperty(INSTALLATIONS_AUTH_TOKEN_HEADER, installationAuthToken);

    // Headers to denote that the request body is a JSONObject.
    urlConnection.setRequestProperty("Content-Type", "application/json");
    urlConnection.setRequestProperty("Accept", "application/json");
  }

  /** Sends developer specified custom headers to the Remote Config server. */
  private void setCustomRequestHeaders(
      HttpURLConnection urlConnection, Map<String, String> customHeaders) {
    for (Map.Entry<String, String> customHeaderEntry : customHeaders.entrySet()) {
      urlConnection.setRequestProperty(customHeaderEntry.getKey(), customHeaderEntry.getValue());
    }
  }

  /** Gets the Android package's SHA-1 fingerprint. */
  private String getFingerprintHashForPackage() {
    byte[] hash;

    try {
      hash = AndroidUtilsLight.getPackageCertificateHashBytes(context, context.getPackageName());

      if (hash == null) {
        Log.e(TAG, "Could not get fingerprint hash for package: " + context.getPackageName());
        return null;
      } else {
        return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "No such package: " + context.getPackageName(), e);
      return null;
    }
  }

  /**
   * Returns a request body serialized as a {@link JSONObject}.
   *
   * <p>The FRC server's fetch endpoint expects a POST request with a request body, which can be
   * serialized as a JSON.
   */
  private JSONObject createFetchRequestBody(
      String installationId,
      String installationAuthToken,
      Map<String, String> analyticsUserProperties)
      throws FirebaseRemoteConfigClientException {
    Map<String, Object> requestBodyMap = new HashMap<>();

    if (installationId == null) {
      throw new FirebaseRemoteConfigClientException(
          "Fetch failed: Firebase installation id is null.");
    }
    requestBodyMap.put(INSTANCE_ID, installationId);

    requestBodyMap.put(INSTANCE_ID_TOKEN, installationAuthToken);
    requestBodyMap.put(APP_ID, appId);

    Locale locale = context.getResources().getConfiguration().locale;
    requestBodyMap.put(COUNTRY_CODE, locale.getCountry());

    // Locale#toLanguageTag() was added in API level 21 (Lollipop)
    String languageCode =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? locale.toLanguageTag()
            : locale.toString();
    requestBodyMap.put(LANGUAGE_CODE, languageCode);

    requestBodyMap.put(PLATFORM_VERSION, Integer.toString(android.os.Build.VERSION.SDK_INT));

    requestBodyMap.put(TIME_ZONE, TimeZone.getDefault().getID());

    try {
      PackageInfo packageInfo =
          context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      if (packageInfo != null) {
        requestBodyMap.put(APP_VERSION, packageInfo.versionName);
      }
    } catch (NameNotFoundException e) {
      // Leave app version blank if package cannot be found.
    }

    requestBodyMap.put(PACKAGE_NAME, context.getPackageName());
    requestBodyMap.put(SDK_VERSION, BuildConfig.VERSION_NAME);

    requestBodyMap.put(ANALYTICS_USER_PROPERTIES, new JSONObject(analyticsUserProperties));

    return new JSONObject(requestBodyMap);
  }

  private void setFetchRequestBody(HttpURLConnection urlConnection, byte[] requestBody)
      throws IOException {
    urlConnection.setFixedLengthStreamingMode(requestBody.length);
    OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
    out.write(requestBody);
    out.flush();
    out.close();
  }

  private JSONObject getFetchResponseBody(URLConnection urlConnection)
      throws IOException, JSONException {
    BufferedReader br =
        new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "utf-8"));
    StringBuilder responseStringBuilder = new StringBuilder();
    int current = 0;
    while ((current = br.read()) != -1) {
      responseStringBuilder.append((char) current);
    }

    return new JSONObject(responseStringBuilder.toString());
  }

  /** Returns true if the backend has updated fetch values. */
  private boolean backendHasUpdates(JSONObject response) {
    try {
      return !response.get(STATE).equals("NO_CHANGE");
    } catch (JSONException e) {
      // The V2 server does not return a state, so assume a null state means there is a valid
      // update.
      return true;
    }
  }

  /**
   * Converts the given {@link JSONObject} Fetch response into a {@link ConfigContainer}.
   *
   * @param fetchResponse The fetch response from the FRC server.
   * @param fetchTime The time, in millis since epoch, when the fetch request was made.
   * @return A {@link ConfigContainer} representing the fetch response from the server.
   */
  private static ConfigContainer extractConfigs(JSONObject fetchResponse, Date fetchTime)
      throws FirebaseRemoteConfigClientException {
    try {
      ConfigContainer.Builder containerBuilder =
          ConfigContainer.newBuilder().withFetchTime(fetchTime);

      JSONObject entries = null;
      try {
        entries = fetchResponse.getJSONObject(ENTRIES);
      } catch (JSONException e) {
        // Do nothing if entries do not exist.
      }
      if (entries != null) {
        containerBuilder.replaceConfigsWith(entries);
      }

      JSONArray experimentDescriptions = null;
      try {
        experimentDescriptions = fetchResponse.getJSONArray(EXPERIMENT_DESCRIPTIONS);
      } catch (JSONException e) {
        // Do nothing if entries do not exist.
      }
      if (experimentDescriptions != null) {
        containerBuilder.withAbtExperiments(experimentDescriptions);
      }

      JSONObject personalizationMetadata = null;
      try {
        personalizationMetadata = fetchResponse.getJSONObject(PERSONALIZATION_METADATA);
      } catch (JSONException e) {
        // Do nothing if personalizationMetadata does not exist.
      }
      if (personalizationMetadata != null) {
        containerBuilder.withPersonalizationMetadata(personalizationMetadata);
      }

      return containerBuilder.build();
    } catch (JSONException e) {
      throw new FirebaseRemoteConfigClientException(
          "Fetch failed: fetch response could not be parsed.", e);
    }
  }
}
