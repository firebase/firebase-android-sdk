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
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.components.Lazy;
import com.google.firebase.inject.Provider;
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
  private final Provider<Transport<FirebaseMlLogEvent>> transport;

  @Inject
  DataTransportMlEventSender(Provider<TransportFactory> transportFactory) {
    this.transport =
        new Lazy<>(
            () ->
                transportFactory
                    .get()
                    .getTransport(
                        FIREBASE_ML_LOG_SDK_NAME,
                        FirebaseMlLogEvent.class,
                        Encoding.of("json"),
                        FirebaseMlLogEvent.getFirebaseMlJsonTransformer()));
  }

  public void sendEvent(@NonNull FirebaseMlLogEvent firebaseMlLogEvent) {
    transport.get().send(Event.ofData(firebaseMlLogEvent));
  }
}
