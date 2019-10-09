// Copyright 2019 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.firebase.remoteconfig.BuildConfig;
import com.google.firebase.remoteconfig.proto.ClientMetrics.ClientLogEvent;
import com.google.firebase.remoteconfig.proto.ClientMetrics.ClientLogEvent.EventType;
import com.google.firebase.remoteconfig.proto.ClientMetrics.FetchEvent;

/**
 * Lightweight client for logging client side metrics for Firebase Remote Config.
 *
 * @author Jacqueline Doan
 */
public class ConfigLogger {

  private final Transport<ClientLogEvent> transport;

  public ConfigLogger(Transport<ClientLogEvent> transport) {
    this.transport = transport;
  }

  void logFetchEvent(
      String appId, String namespace, String fid, long timestampMillis, long networkLatencyMillis) {

    ClientLogEvent.Builder clientLogEventBuilder =
        createClientLogEventBuilder(
            appId, namespace, fid, timestampMillis, /* sdkVersion= */ BuildConfig.VERSION_NAME);

    ClientLogEvent fetchEvent = createFetchEvent(clientLogEventBuilder, networkLatencyMillis);

    transport.send(Event.ofData(fetchEvent));
  }

  private ClientLogEvent createFetchEvent(
      ClientLogEvent.Builder clientLogEventBuilder, long networkLatencyMillis) {
    return clientLogEventBuilder
        .setEventType(EventType.FETCH)
        .setFetchEvent(
            FetchEvent.newBuilder().setNetworkLatencyMillis(networkLatencyMillis).build())
        .build();
  }

  private ClientLogEvent.Builder createClientLogEventBuilder(
      String appId, String namespace, String fid, long timestampMillis, String sdkVersion) {

    return ClientLogEvent.newBuilder()
        .setAppId(appId)
        .setNamespaceId(namespace)
        .setFid(fid)
        .setTimestampMillis(timestampMillis)
        .setSdkVersion(sdkVersion);
  }
}
