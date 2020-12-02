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

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.gms.tasks.Task;
import com.google.firebase.encoders.EncodingException;

/**
 * This class is responsible for sending FirebaseMl Stats and log objects to Firebase through Google
 * DataTransport.
 *
 * <p>These will be equivalent to FirebaseMlLogEvent.proto internally.
 */
public class DataTransportMlStatsSender {
  public static final String TAG = "FirebaseMlDownloader";
  private static final String FIREBASE_ML_STATS_NAME = "FIREBASE_ML_STATS";
  private final Transport<FirebaseMlStat> transport;

  public static DataTransportMlStatsSender create(Context context) {
    TransportRuntime.initialize(context);
    final Transport<FirebaseMlStat> transport =
        TransportRuntime.getInstance()
            .newFactory(CCTDestination.LEGACY_INSTANCE)
            .getTransport(
                FIREBASE_ML_STATS_NAME,
                FirebaseMlStat.class,
                Encoding.of("json"),
                FirebaseMlStat::getBytes);
    return new DataTransportMlStatsSender(transport);
  }

  DataTransportMlStatsSender(Transport<FirebaseMlStat> transport) {
    this.transport = transport;
  }

  @NonNull
  public Task<FirebaseMlStat> sendStats(@NonNull FirebaseMlStat stat) {

    try {
      // TODO(b/145299499): offload encoding to Firelog Thread
      transport.send(Event.ofData(stat));
    } catch (EncodingException e) {
      Log.d(TAG, "Failed to encode Firebase Ml Stat payload. Skip sending");
    }
  }
}
