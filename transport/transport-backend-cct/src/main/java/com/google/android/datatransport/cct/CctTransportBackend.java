// Copyright 2018 Google LLC
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

package com.google.android.datatransport.cct;

import static com.google.android.datatransport.runtime.retries.Retries.retry;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.backend.cct.BuildConfig;
import com.google.android.datatransport.cct.internal.AndroidClientInfo;
import com.google.android.datatransport.cct.internal.BatchedLogRequest;
import com.google.android.datatransport.cct.internal.ClientInfo;
import com.google.android.datatransport.cct.internal.LogEvent;
import com.google.android.datatransport.cct.internal.LogRequest;
import com.google.android.datatransport.cct.internal.LogResponse;
import com.google.android.datatransport.cct.internal.NetworkConnectionInfo;
import com.google.android.datatransport.cct.internal.QosTier;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.logging.Logging;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.EncodingException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class CctTransportBackend implements TransportBackend {

  private static final String LOG_TAG = "CctTransportBackend";

  private static final int CONNECTION_TIME_OUT = 30000;
  private static final int READ_TIME_OUT = 40000;
  private static final int INVALID_VERSION_CODE = -1;
  private static final String ACCEPT_ENCODING_HEADER_KEY = "Accept-Encoding";
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";
  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  static final String API_KEY_HEADER_KEY = "X-Goog-Api-Key";
  private static final String JSON_CONTENT_TYPE = "application/json";

  @VisibleForTesting static final String KEY_NETWORK_TYPE = "net-type";
  @VisibleForTesting static final String KEY_MOBILE_SUBTYPE = "mobile-subtype";

  private static final String KEY_SDK_VERSION = "sdk-version";
  private static final String KEY_MODEL = "model";
  private static final String KEY_HARDWARE = "hardware";
  private static final String KEY_DEVICE = "device";
  private static final String KEY_PRODUCT = "product";
  private static final String KEY_OS_BUILD = "os-uild";
  private static final String KEY_MANUFACTURER = "manufacturer";
  private static final String KEY_FINGERPRINT = "fingerprint";
  private static final String KEY_LOCALE = "locale";
  private static final String KEY_COUNTRY = "country";
  private static final String KEY_MCC_MNC = "mcc_mnc";
  private static final String KEY_TIMEZONE_OFFSET = "tz-offset";
  private static final String KEY_APPLICATION_BUILD = "application_build";

  private final DataEncoder dataEncoder = BatchedLogRequest.createDataEncoder();

  private final ConnectivityManager connectivityManager;
  private final Context applicationContext;
  final URL endPoint;
  private final Clock uptimeClock;
  private final Clock wallTimeClock;
  private final int readTimeout;

  private static URL parseUrlOrThrow(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid url: " + url, e);
    }
  }

  CctTransportBackend(
      Context applicationContext, Clock wallTimeClock, Clock uptimeClock, int readTimeout) {
    this.applicationContext = applicationContext;
    this.connectivityManager =
        (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    this.endPoint = parseUrlOrThrow(CCTDestination.DEFAULT_END_POINT);
    this.uptimeClock = uptimeClock;
    this.wallTimeClock = wallTimeClock;
    this.readTimeout = readTimeout;
  }

  CctTransportBackend(Context applicationContext, Clock wallTimeClock, Clock uptimeClock) {
    this(applicationContext, wallTimeClock, uptimeClock, READ_TIME_OUT);
  }

  private static TelephonyManager getTelephonyManager(Context context) {
    return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
  }

  private static int getPackageVersionCode(Context context) {
    try {
      int packageVersionCode =
          context
              .getPackageManager()
              .getPackageInfo(context.getPackageName(), /* flags= */ 0)
              .versionCode;
      return packageVersionCode;
    } catch (PackageManager.NameNotFoundException e) {
      Logging.e(LOG_TAG, "Unable to find version code for package", e);
    }
    return INVALID_VERSION_CODE;
  }

  @Override
  public EventInternal decorate(EventInternal eventInternal) {
    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

    return eventInternal
        .toBuilder()
        .addMetadata(KEY_SDK_VERSION, Build.VERSION.SDK_INT)
        .addMetadata(KEY_MODEL, Build.MODEL)
        .addMetadata(KEY_HARDWARE, Build.HARDWARE)
        .addMetadata(KEY_DEVICE, Build.DEVICE)
        .addMetadata(KEY_PRODUCT, Build.PRODUCT)
        .addMetadata(KEY_OS_BUILD, Build.ID)
        .addMetadata(KEY_MANUFACTURER, Build.MANUFACTURER)
        .addMetadata(KEY_FINGERPRINT, Build.FINGERPRINT)
        .addMetadata(KEY_TIMEZONE_OFFSET, getTzOffset())
        .addMetadata(KEY_NETWORK_TYPE, getNetTypeValue(networkInfo))
        .addMetadata(KEY_MOBILE_SUBTYPE, getNetSubtypeValue(networkInfo))
        .addMetadata(KEY_COUNTRY, Locale.getDefault().getCountry())
        .addMetadata(KEY_LOCALE, Locale.getDefault().getLanguage())
        .addMetadata(KEY_MCC_MNC, getTelephonyManager(applicationContext).getSimOperator())
        .addMetadata(
            KEY_APPLICATION_BUILD, Integer.toString(getPackageVersionCode(applicationContext)))
        .build();
  }

  private static int getNetTypeValue(NetworkInfo networkInfo) {
    // when the device is not connected networkInfo returned by ConnectivityManger is null.
    if (networkInfo == null) {
      return NetworkConnectionInfo.NetworkType.NONE.getValue();
    }
    return networkInfo.getType();
  }

  private static int getNetSubtypeValue(NetworkInfo networkInfo) {
    // when the device is not connected networkInfo returned by ConnectivityManger is null.
    if (networkInfo == null) {
      return NetworkConnectionInfo.MobileSubtype.UNKNOWN_MOBILE_SUBTYPE.getValue();
    }
    int subtype = networkInfo.getSubtype();
    if (subtype == -1) {
      return NetworkConnectionInfo.MobileSubtype.COMBINED.getValue();
    }
    return NetworkConnectionInfo.MobileSubtype.forNumber(subtype) != null ? subtype : 0;
  }

  private BatchedLogRequest getRequestBody(BackendRequest backendRequest) {
    HashMap<String, List<EventInternal>> eventInternalMap = new HashMap<>();
    for (EventInternal eventInternal : backendRequest.getEvents()) {
      String key = eventInternal.getTransportName();

      if (!eventInternalMap.containsKey(key)) {
        List<EventInternal> eventInternalList = new ArrayList<>();
        eventInternalList.add(eventInternal);
        eventInternalMap.put(key, eventInternalList);
      } else {
        eventInternalMap.get(key).add(eventInternal);
      }
    }
    List<LogRequest> batchedRequests = new ArrayList<>();
    for (Map.Entry<String, List<EventInternal>> entry : eventInternalMap.entrySet()) {
      EventInternal firstEvent = entry.getValue().get(0);
      LogRequest.Builder requestBuilder =
          LogRequest.builder()
              .setQosTier(QosTier.DEFAULT)
              .setRequestTimeMs(wallTimeClock.getTime())
              .setRequestUptimeMs(uptimeClock.getTime())
              .setClientInfo(
                  ClientInfo.builder()
                      .setClientType(ClientInfo.ClientType.ANDROID_FIREBASE)
                      .setAndroidClientInfo(
                          AndroidClientInfo.builder()
                              .setSdkVersion(firstEvent.getInteger(KEY_SDK_VERSION))
                              .setModel(firstEvent.get(KEY_MODEL))
                              .setHardware(firstEvent.get(KEY_HARDWARE))
                              .setDevice(firstEvent.get(KEY_DEVICE))
                              .setProduct(firstEvent.get(KEY_PRODUCT))
                              .setOsBuild(firstEvent.get(KEY_OS_BUILD))
                              .setManufacturer(firstEvent.get(KEY_MANUFACTURER))
                              .setFingerprint(firstEvent.get(KEY_FINGERPRINT))
                              .setCountry(firstEvent.get(KEY_COUNTRY))
                              .setLocale(firstEvent.get(KEY_LOCALE))
                              .setMccMnc(firstEvent.get(KEY_MCC_MNC))
                              .setApplicationBuild(firstEvent.get(KEY_APPLICATION_BUILD))
                              .build())
                      .build());

      // set log source to either its numeric value or its name.
      try {
        requestBuilder.setSource(Integer.parseInt(entry.getKey()));
      } catch (NumberFormatException ex) {
        requestBuilder.setSource(entry.getKey());
      }

      List<LogEvent> logEvents = new ArrayList<>();
      for (EventInternal eventInternal : entry.getValue()) {
        EncodedPayload encodedPayload = eventInternal.getEncodedPayload();
        Encoding encoding = encodedPayload.getEncoding();

        LogEvent.Builder event;
        if (encoding.equals(Encoding.of("proto"))) {
          event = LogEvent.protoBuilder(encodedPayload.getBytes());
        } else if (encoding.equals(Encoding.of("json"))) {
          event =
              LogEvent.jsonBuilder(new String(encodedPayload.getBytes(), Charset.forName("UTF-8")));
        } else {
          Logging.w(LOG_TAG, "Received event of unsupported encoding %s. Skipping...", encoding);
          continue;
        }

        event
            .setEventTimeMs(eventInternal.getEventMillis())
            .setEventUptimeMs(eventInternal.getUptimeMillis())
            .setTimezoneOffsetSeconds(eventInternal.getLong(KEY_TIMEZONE_OFFSET))
            .setNetworkConnectionInfo(
                NetworkConnectionInfo.builder()
                    .setNetworkType(
                        NetworkConnectionInfo.NetworkType.forNumber(
                            eventInternal.getInteger(KEY_NETWORK_TYPE)))
                    .setMobileSubtype(
                        NetworkConnectionInfo.MobileSubtype.forNumber(
                            eventInternal.getInteger(KEY_MOBILE_SUBTYPE)))
                    .build());

        if (eventInternal.getCode() != null) {
          event.setEventCode(eventInternal.getCode());
        }
        logEvents.add(event.build());
      }
      requestBuilder.setLogEvents(logEvents);
      batchedRequests.add(requestBuilder.build());
    }

    return BatchedLogRequest.create(batchedRequests);
  }

  private HttpResponse doSend(HttpRequest request) throws IOException {

    Logging.d(LOG_TAG, "Making request to: %s", request.url);
    HttpURLConnection connection = (HttpURLConnection) request.url.openConnection();
    connection.setConnectTimeout(CONNECTION_TIME_OUT);
    connection.setReadTimeout(readTimeout);
    connection.setDoOutput(true);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod("POST");
    connection.setRequestProperty(
        "User-Agent", String.format("datatransport/%s android/", BuildConfig.VERSION_NAME));
    connection.setRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
    connection.setRequestProperty(CONTENT_TYPE_HEADER_KEY, JSON_CONTENT_TYPE);
    connection.setRequestProperty(ACCEPT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);

    if (request.apiKey != null) {
      connection.setRequestProperty(API_KEY_HEADER_KEY, request.apiKey);
    }

    try (OutputStream conn = connection.getOutputStream();
        OutputStream outputStream = new GZIPOutputStream(conn)) {
      // note: it's very important to use a BufferedWriter for efficient use of resources as the
      // JsonWriter often writes one character at a time.
      dataEncoder.encode(
          request.requestBody, new BufferedWriter(new OutputStreamWriter(outputStream)));
    } catch (ConnectException | UnknownHostException e) {
      Logging.e(LOG_TAG, "Couldn't open connection, returning with 500", e);
      return new HttpResponse(500, null, 0);
    } catch (EncodingException | IOException e) {
      Logging.e(LOG_TAG, "Couldn't encode request, returning with 400", e);
      return new HttpResponse(400, null, 0);
    }

    int responseCode = connection.getResponseCode();
    Logging.i(LOG_TAG, "Status Code: " + responseCode);
    Logging.i(LOG_TAG, "Content-Type: " + connection.getHeaderField("Content-Type"));
    Logging.i(LOG_TAG, "Content-Encoding: " + connection.getHeaderField("Content-Encoding"));

    if (responseCode == 302 || responseCode == 301 || responseCode == 307) {
      String redirect = connection.getHeaderField("Location");
      return new HttpResponse(responseCode, new URL(redirect), 0);
    }
    if (responseCode != 200) {
      return new HttpResponse(responseCode, null, 0);
    }

    try (InputStream connStream = connection.getInputStream();
        InputStream inputStream =
            maybeUnGzip(connStream, connection.getHeaderField(CONTENT_ENCODING_HEADER_KEY))) {
      long nextRequestMillis =
          LogResponse.fromJson(new BufferedReader(new InputStreamReader(inputStream)))
              .getNextRequestWaitMillis();
      return new HttpResponse(responseCode, null, nextRequestMillis);
    }
  }

  private static InputStream maybeUnGzip(InputStream input, String contentEncoding)
      throws IOException {
    if (GZIP_CONTENT_ENCODING.equals(contentEncoding)) {
      return new GZIPInputStream(input);
    }
    return input;
  }

  @Override
  public BackendResponse send(BackendRequest request) {
    BatchedLogRequest requestBody = getRequestBody(request);
    // CCT backend supports 2 different endpoints
    // We route to CCT backend if extras are null and to LegacyFlg otherwise.
    // This (anti-) pattern should not be required for other backends
    String apiKey = null;
    URL actualEndPoint = endPoint;
    if (request.getExtras() != null) {
      try {
        CCTDestination destination = CCTDestination.fromByteArray(request.getExtras());
        if (destination.getAPIKey() != null) {
          apiKey = destination.getAPIKey();
        }
        if (destination.getEndPoint() != null) {
          actualEndPoint = parseUrlOrThrow(destination.getEndPoint());
        }
      } catch (IllegalArgumentException e) {
        return BackendResponse.fatalError();
      }
    }

    try {
      HttpResponse response =
          retry(
              5,
              new HttpRequest(actualEndPoint, requestBody, apiKey),
              this::doSend,
              (req, resp) -> {
                if (resp.redirectUrl != null) {
                  // retry with different url
                  Logging.d(LOG_TAG, "Following redirect to: %s", resp.redirectUrl);
                  return req.withUrl(resp.redirectUrl);
                }
                // don't retry
                return null;
              });

      if (response.code == 200) {
        return BackendResponse.ok(response.nextRequestMillis);
      } else if (response.code >= 500 || response.code == 404) {
        return BackendResponse.transientError();
      } else {
        return BackendResponse.fatalError();
      }
    } catch (IOException e) {
      Logging.e(LOG_TAG, "Could not make request to the backend", e);
      return BackendResponse.transientError();
    }
  }

  @VisibleForTesting
  static long getTzOffset() {
    Calendar.getInstance();
    TimeZone tz = TimeZone.getDefault();
    return tz.getOffset(Calendar.getInstance().getTimeInMillis()) / 1000;
  }

  static final class HttpResponse {
    final int code;
    @Nullable final URL redirectUrl;
    final long nextRequestMillis;

    HttpResponse(int code, @Nullable URL redirectUrl, long nextRequestMillis) {
      this.code = code;
      this.redirectUrl = redirectUrl;
      this.nextRequestMillis = nextRequestMillis;
    }
  }

  static final class HttpRequest {
    final URL url;
    final BatchedLogRequest requestBody;
    @Nullable final String apiKey;

    HttpRequest(URL url, BatchedLogRequest requestBody, @Nullable String apiKey) {
      this.url = url;
      this.requestBody = requestBody;
      this.apiKey = apiKey;
    }

    HttpRequest withUrl(URL newUrl) {
      return new HttpRequest(newUrl, requestBody, apiKey);
    }
  }
}
