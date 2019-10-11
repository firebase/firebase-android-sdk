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
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.iid.FirebaseInstanceId;
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

  private final Clock clock;

  private final String appId;
  private final FirebaseInstanceId firebaseInstanceId;
  private final Transport<ClientLogEvent> transport;
  private String namespace;

  public ConfigLogger(
      Transport<ClientLogEvent> transport,
      String appId,
      String namespace,
      FirebaseInstanceId firebaseInstanceId,
      Clock clock) {

    this.transport = transport;
    this.appId = appId;
    this.namespace = namespace;
    this.firebaseInstanceId = firebaseInstanceId;
    this.clock = clock;
  }

  @VisibleForTesting
  public String getNamespace() {
    return this.namespace;
  }

  /**
   * Creates and log a {@link ClientLogEvent} that contains metrics related to a {@link FetchEvent}
   */
  void logFetchEvent(long networkLatencyMillis) {

    ClientLogEvent fetchEvent =
        createClientLogEventBuilder()
            .setEventType(EventType.FETCH)
            .setFetchEvent(
                FetchEvent.newBuilder().setNetworkLatencyMillis(networkLatencyMillis).build())
            .build();

    transport.send(Event.ofData(fetchEvent));
  }

  /**
   * Returns a {@link ClientLogEvent.Builder} that instantiates general fields for client-side
   * metrics.
   */
  private ClientLogEvent.Builder createClientLogEventBuilder() {
    return ClientLogEvent.newBuilder()
        .setAppId(this.appId)
        .setNamespaceId(this.namespace)
        .setFid(firebaseInstanceId.getId())
        .setTimestampMillis(clock.currentTimeMillis())
        .setSdkVersion(BuildConfig.VERSION_NAME);
  }
}
