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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.cct.proto.AndroidClientInfo;
import com.google.android.datatransport.cct.proto.BatchedLogRequest;
import com.google.android.datatransport.cct.proto.ClientInfo;
import com.google.android.datatransport.cct.proto.LogEvent;
import com.google.android.datatransport.cct.proto.LogRequest;
import com.google.android.datatransport.cct.proto.LogResponse;
import com.google.android.datatransport.cct.proto.NetworkConnectionInfo;
import com.google.android.datatransport.cct.proto.NetworkConnectionInfo.MobileSubtype;
import com.google.android.datatransport.cct.proto.NetworkConnectionInfo.NetworkType;
import com.google.android.datatransport.cct.proto.QosTierConfiguration;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

final class CctTransportBackend implements TransportBackend {

  private static final Logger LOGGER = Logger.getLogger(CctTransportBackend.class.getName());

  private static final int CONNECTION_TIME_OUT = 30000;
  private static final int READ_TIME_OUT = 40000;
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";
  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  private static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";

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
  private static final String KEY_TIMEZONE_OFFSET = "tz-offset";

  private final ConnectivityManager connectivityManager;
  private final URL endPoint;
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
      Context applicationContext,
      String url,
      Clock wallTimeClock,
      Clock uptimeClock,
      int readTimeout) {
    this.connectivityManager =
        (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    this.endPoint = parseUrlOrThrow(url);
    this.uptimeClock = uptimeClock;
    this.wallTimeClock = wallTimeClock;
    this.readTimeout = readTimeout;
  }

  CctTransportBackend(
      Context applicationContext, String url, Clock wallTimeClock, Clock uptimeClock) {
    this(applicationContext, url, wallTimeClock, uptimeClock, READ_TIME_OUT);
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
        .build();
  }

  private static int getNetTypeValue(NetworkInfo networkInfo) {
    // when the device is not connected networkInfo returned by ConnectivityManger is null.
    if (networkInfo == null) {
      return NetworkType.NONE_VALUE;
    }
    return networkInfo.getType();
  }

  private static int getNetSubtypeValue(NetworkInfo networkInfo) {
    // when the device is not connected networkInfo returned by ConnectivityManger is null.
    if (networkInfo == null) {
      return MobileSubtype.UNKNOWN_MOBILE_SUBTYPE_VALUE;
    }
    int subtype = networkInfo.getSubtype();
    if (subtype == -1) {
      return MobileSubtype.COMBINED_VALUE;
    }
    return MobileSubtype.forNumber(subtype) != null ? subtype : 0;
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
    BatchedLogRequest.Builder batchedRequestBuilder = BatchedLogRequest.newBuilder();
    for (Map.Entry<String, List<EventInternal>> entry : eventInternalMap.entrySet()) {
      EventInternal firstEvent = entry.getValue().get(0);
      LogRequest.Builder requestBuilder =
          LogRequest.newBuilder()
              .setLogSource(Integer.valueOf(entry.getKey()))
              .setQosTier(QosTierConfiguration.QosTier.DEFAULT)
              .setRequestTimeMs(wallTimeClock.getTime())
              .setRequestUptimeMs(uptimeClock.getTime())
              .setClientInfo(
                  ClientInfo.newBuilder()
                      .setClientType(ClientInfo.ClientType.ANDROID)
                      .setAndroidClientInfo(
                          AndroidClientInfo.newBuilder()
                              .setSdkVersion(firstEvent.getInteger(KEY_SDK_VERSION))
                              .setModel(firstEvent.get(KEY_MODEL))
                              .setHardware(firstEvent.get(KEY_HARDWARE))
                              .setDevice(firstEvent.get(KEY_DEVICE))
                              .setProduct(firstEvent.get(KEY_PRODUCT))
                              .setOsBuild(firstEvent.get(KEY_OS_BUILD))
                              .setManufacturer(firstEvent.get(KEY_MANUFACTURER))
                              .setFingerprint(firstEvent.get(KEY_FINGERPRINT))
                              .build())
                      .build());
      for (EventInternal eventInternal : entry.getValue()) {
        LogEvent.Builder event =
            LogEvent.newBuilder()
                .setEventTimeMs(eventInternal.getEventMillis())
                .setEventUptimeMs(eventInternal.getUptimeMillis())
                .setTimezoneOffsetSeconds(eventInternal.getLong(KEY_TIMEZONE_OFFSET))
                .setSourceExtension(ByteString.copyFrom(eventInternal.getPayload()))
                .setNetworkConnectionInfo(
                    NetworkConnectionInfo.newBuilder()
                        .setNetworkTypeValue(eventInternal.getInteger(KEY_NETWORK_TYPE))
                        .setMobileSubtypeValue(eventInternal.getInteger(KEY_MOBILE_SUBTYPE)));
        if (eventInternal.getCode() != null) {
          event.setEventCode(eventInternal.getCode());
        }
        requestBuilder.addLogEvent(event);
      }
      batchedRequestBuilder.addLogRequest(requestBuilder.build());
    }
    return batchedRequestBuilder.build();
  }

  private BackendResponse doSend(BatchedLogRequest requestBody) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) endPoint.openConnection();
    connection.setConnectTimeout(CONNECTION_TIME_OUT);
    connection.setReadTimeout(readTimeout);
    connection.setDoOutput(true);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod("POST");
    connection.setRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
    connection.setRequestProperty(CONTENT_TYPE_HEADER_KEY, PROTOBUF_CONTENT_TYPE);

    WritableByteChannel channel = Channels.newChannel(connection.getOutputStream());
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output);

      try {
        requestBody.writeTo(gzipOutputStream);
      } finally {
        gzipOutputStream.close();
      }
      channel.write(ByteBuffer.wrap(output.toByteArray()));
      int responseCode = connection.getResponseCode();
      LOGGER.info("Status Code: " + responseCode);

      long nextRequestMillis;
      InputStream inputStream = connection.getInputStream();
      try {
        try {
          nextRequestMillis = LogResponse.parseFrom(inputStream).getNextRequestWaitMillis();
        } catch (InvalidProtocolBufferException e) {
          return BackendResponse.fatalError();
        }
      } finally {
        inputStream.close();
      }
      if (responseCode == 200) {
        return BackendResponse.ok(nextRequestMillis);
      } else if (responseCode >= 500 || responseCode == 404) {
        return BackendResponse.transientError();
      } else {
        return BackendResponse.fatalError();
      }
    } finally {
      channel.close();
    }
  }

  @Override
  public BackendResponse send(BackendRequest request) {
    BatchedLogRequest requestBody = getRequestBody(request);
    try {
      return doSend(requestBody);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Could not make request to the backend", e);
      return BackendResponse.transientError();
    }
  }

  @VisibleForTesting
  static long getTzOffset() {
    Calendar.getInstance();
    TimeZone tz = TimeZone.getDefault();
    return tz.getOffset(Calendar.getInstance().getTimeInMillis()) / 1000;
  }
}
