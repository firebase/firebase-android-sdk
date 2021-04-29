// Copyright 2020 Google LLC
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

package com.google.firebase.perf.transport;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.inject.Provider;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.v1.PerfMetric;

/** Manages Flg client for dispatching performance events. */
final class FlgTransport {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private final String logSourceName;
  private final Provider<TransportFactory> flgTransportFactoryProvider;

  private Transport<PerfMetric> flgTransport;

  FlgTransport(Provider<TransportFactory> flgTransportFactoryProvider, final String logSourceName) {
    this.logSourceName = logSourceName;
    this.flgTransportFactoryProvider = flgTransportFactoryProvider;
  }

  /**
   * Logs the {@code perfMetric} event to the Flg Transport.
   *
   * @implNote This method does not itself guarantee Thread safety (even though the Flg Transport
   *     API might provide it under the hood) and so it's the responsibility of the caller to take
   *     care of that by itself.
   */
  @WorkerThread
  public void log(@NonNull PerfMetric perfMetric) {
    if (!initializeFlgTransportClient()) {
      logger.warn("Unable to dispatch event because Flg Transport is not available");
      return;
    }

    flgTransport.send(Event.ofData(perfMetric));
  }

  private boolean initializeFlgTransportClient() {
    if (flgTransport == null) {
      // Calling ".get()" on the "Provider<TransportFactory>" may return "null" if the
      // TransportFactory is not available.
      TransportFactory factory = flgTransportFactoryProvider.get();

      if (factory != null) {
        flgTransport =
            factory.getTransport(
                logSourceName, PerfMetric.class, Encoding.of("proto"), PerfMetric::toByteArray);

      } else {
        logger.warn("Flg TransportFactory is not available at the moment");
      }
    }

    return flgTransport != null;
  }
}
