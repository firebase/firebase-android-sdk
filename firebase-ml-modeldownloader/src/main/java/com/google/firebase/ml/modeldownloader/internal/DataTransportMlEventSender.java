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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class is responsible for sending Firebase ML Log Events to Firebase through Google
 * DataTransport.
 *
 * <p>These will be equivalent to LogEvent.proto internally.
 *
 * @hide
 */
@Singleton
public class DataTransportMlEventSender {
  private static final String FIREBASE_ML_LOG_SDK_NAME = "FIREBASE_ML_LOG_SDK";
  private final Transport<FirebaseMlLogEvent> transport;

  @Inject
  DataTransportMlEventSender(TransportFactory transportFactory) {
    this(
        transportFactory.getTransport(
            FIREBASE_ML_LOG_SDK_NAME,
            FirebaseMlLogEvent.class,
            Encoding.of("json"),
            FirebaseMlLogEvent.getFirebaseMlJsonTransformer()));
  }

  @VisibleForTesting
  DataTransportMlEventSender(Transport<FirebaseMlLogEvent> transport) {
    this.transport = transport;
  }

  public void sendEvent(@NonNull FirebaseMlLogEvent firebaseMlLogEvent) {
    transport.send(Event.ofData(firebaseMlLogEvent));
  }
}
