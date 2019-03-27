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

import android.os.Build;
import android.support.annotation.VisibleForTesting;
import com.google.android.datatransport.cct.proto.AndroidClientInfo;
import com.google.android.datatransport.cct.proto.BatchedLogRequest;
import com.google.android.datatransport.cct.proto.ClientInfo;
import com.google.android.datatransport.cct.proto.LogEvent;
import com.google.android.datatransport.cct.proto.LogRequest;
import com.google.android.datatransport.cct.proto.LogResponse;
import com.google.android.datatransport.cct.proto.QosTierConfiguration;
import com.google.android.datatransport.runtime.BackendRequest;
import com.google.android.datatransport.runtime.BackendResponse;
import com.google.android.datatransport.runtime.BackendResponse.Status;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportBackend;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.UptimeClock;
import com.google.android.datatransport.runtime.time.WallTimeClock;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class GoogleTransportBackend implements TransportBackend {

  private static final Logger LOGGER = Logger.getLogger(GoogleTransportBackend.class.getName());
  private static final int CONNECTION_TIME_OUT = 30000;
  private static final int READ_TIME_OUT = 40000;
  private static final String CONTENT_ENCODING_HEADER_KEY = "Content-Encoding";
  private static final String GZIP_CONTENT_ENCODING = "gzip";
  private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
  private static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";
  private static final String SDK_VERSION_KEY = "SDK Version";
  private static final String MODEL_KEY = "Model";
  private static final String HARDWARE_KEY = "Hardware";
  private static final String DEVICE_KEY = "Device";
  private static final String PRODUCT_KEY = "Product";
  private static final String OS_BUILD_KEY = "OS Build";
  private static final String MANUFACTURER_KEY = "Manufacturer";
  private static final String FINGERPRINT_KEY = "Fingerprint";

  private final URL endPoint;
  private final Clock uptimeClock;
  private final Clock wallTimeClock;

  private static URL parseUrlOrThrow(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid url: " + url, e);
    }
  }

  @VisibleForTesting
  GoogleTransportBackend(String url, Clock wallTimeClock, Clock uptimeClock) {
    this.endPoint = parseUrlOrThrow(url);
    this.uptimeClock = uptimeClock;
    this.wallTimeClock = wallTimeClock;
  }

  public GoogleTransportBackend() {
    this("https://play.googleapis.com/log/batch", new WallTimeClock(), new UptimeClock());
  }

  @Override
  public EventInternal decorate(EventInternal eventInternal) {
    return eventInternal
        .toBuilder()
        .addMetadata(SDK_VERSION_KEY, String.valueOf(Build.VERSION.SDK_INT))
        .addMetadata(MODEL_KEY, Build.MODEL)
        .addMetadata(HARDWARE_KEY, Build.HARDWARE)
        .addMetadata(DEVICE_KEY, Build.DEVICE)
        .addMetadata(PRODUCT_KEY, Build.PRODUCT)
        .addMetadata(OS_BUILD_KEY, Build.ID)
        .addMetadata(MANUFACTURER_KEY, Build.MANUFACTURER)
        .addMetadata(FINGERPRINT_KEY, Build.FINGERPRINT)
        .build();
  }

  private BatchedLogRequest getRequestBody(BackendRequest backendRequest) {
    HashMap<String, List<EventInternal>> eventInternalMap = new HashMap<>();
    for (EventInternal eventInternal : backendRequest.getEvents()) {
      String key = eventInternal.getTransportName();
      if (!eventInternalMap.containsKey(key)) {
        List<EventInternal> eventInternalList = new ArrayList<EventInternal>();
        eventInternalList.add(eventInternal);
        eventInternalMap.put(key, eventInternalList);
      } else {
        eventInternalMap.get(key).add(eventInternal);
      }
    }
    BatchedLogRequest.Builder batchedRequestBuilder = BatchedLogRequest.newBuilder();
    for (Map.Entry<String, List<EventInternal>> entry : eventInternalMap.entrySet()) {
      Map<String, String> metadata = entry.getValue().get(0).getMetadata();
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
                              .setSdkVersion(Integer.valueOf(metadata.get(SDK_VERSION_KEY)))
                              .setModel(metadata.get(MODEL_KEY))
                              .setHardware(metadata.get(HARDWARE_KEY))
                              .setDevice(metadata.get(DEVICE_KEY))
                              .setProduct(metadata.get(PRODUCT_KEY))
                              .setOsBuild(metadata.get(OS_BUILD_KEY))
                              .setManufacturer(metadata.get(MANUFACTURER_KEY))
                              .setFingerprint(metadata.get(FINGERPRINT_KEY))
                              .build())
                      .build());
      for (EventInternal eventInternal : entry.getValue()) {
        LogEvent event =
            LogEvent.newBuilder()
                .setEventTimeMs(eventInternal.getEventMillis())
                .setEventUptimeMs(eventInternal.getUptimeMillis())
                // .setTimezoneOffsetSeconds(0) TODO set the time zone offset.
                .setSourceExtension(ByteString.copyFrom(eventInternal.getPayload()))
                .build();
        requestBuilder.addLogEvent(event);
      }
      batchedRequestBuilder.addLogRequest(requestBuilder.build());
    }
    return batchedRequestBuilder.build();
  }

  private BackendResponse doSend(BatchedLogRequest requestBody) throws IOException {
    long nextRequestMillis = -1;
    HttpURLConnection connection = (HttpURLConnection) endPoint.openConnection();
    connection.setConnectTimeout(CONNECTION_TIME_OUT);
    connection.setReadTimeout(READ_TIME_OUT);
    connection.setDoOutput(true);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod("POST");
    connection.setRequestProperty(CONTENT_ENCODING_HEADER_KEY, GZIP_CONTENT_ENCODING);
    connection.setRequestProperty(CONTENT_TYPE_HEADER_KEY, PROTOBUF_CONTENT_TYPE);
    try (WritableByteChannel channel = Channels.newChannel(connection.getOutputStream())) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output)) {
        requestBody.writeTo(gzipOutputStream);
      }
      channel.write(ByteBuffer.wrap(output.toByteArray()));
      int responseCode = connection.getResponseCode();
      LOGGER.info("Status Code: " + responseCode);
      try (InputStream inputStream = connection.getInputStream()) {
        try {
          nextRequestMillis = LogResponse.parseFrom(inputStream).getNextRequestWaitMillis();
        } catch (InvalidProtocolBufferException e) {
          return BackendResponse.create(Status.NONTRANSIENT_ERROR, -1);
        }
      }
      if (responseCode == 200) {
        return BackendResponse.create(Status.OK, nextRequestMillis);
      } else if (responseCode >= 500 || responseCode == 404) {
        return BackendResponse.create(Status.TRANSIENT_ERROR, -1);
      } else {
        return BackendResponse.create(Status.NONTRANSIENT_ERROR, -1);
      }
    }
  }

  @Override
  public BackendResponse send(BackendRequest request) {
    BatchedLogRequest requestBody = getRequestBody(request);
    try {
      return doSend(requestBody);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Could not make request to the backend", e);
      return BackendResponse.create(Status.TRANSIENT_ERROR, -1);
    }
  }
}
