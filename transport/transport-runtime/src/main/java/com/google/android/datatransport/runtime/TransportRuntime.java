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

package com.google.android.datatransport.runtime;

import android.content.Context;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.TransportScheduleCallback;
import com.google.android.datatransport.runtime.scheduling.Scheduler;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkInitializer;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The central entry point to the event transport infrastructure.
 *
 * <p>Handles event scheduling, persistence and batching. Default {@link TransportFactory}
 * implementations delegate to this class.
 */
@Singleton
public class TransportRuntime implements TransportInternal {

  private static volatile TransportRuntimeComponent instance = null;

  private final Clock eventClock;
  private final Clock uptimeClock;
  private final Scheduler scheduler;
  private final Uploader uploader;

  @Inject
  TransportRuntime(
      @WallTime Clock eventClock,
      @Monotonic Clock uptimeClock,
      Scheduler scheduler,
      Uploader uploader,
      WorkInitializer initializer) {
    this.eventClock = eventClock;
    this.uptimeClock = uptimeClock;
    this.scheduler = scheduler;
    this.uploader = uploader;

    initializer.ensureContextsScheduled();
  }

  /**
   * Initializes transport runtime with an application context.
   *
   * <p>This method must be called before {@link #getInstance()}.
   */
  public static void initialize(Context applicationContext) {
    if (instance == null) {
      synchronized (TransportRuntime.class) {
        if (instance == null) {
          instance =
              DaggerTransportRuntimeComponent.builder()
                  .setApplicationContext(applicationContext)
                  .build();
        }
      }
    }
    // send warning
  }

  /**
   * Returns the global singleton instance of {@link TransportRuntime}.
   *
   * @throws IllegalStateException if {@link #initialize(Context)} is not called before this method.
   */
  public static TransportRuntime getInstance() {
    TransportRuntimeComponent localRef = instance;
    if (localRef == null) {
      throw new IllegalStateException("Not initialized!");
    }
    return localRef.getTransportRuntime();
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  static void withInstance(TransportRuntimeComponent component, Callable<Void> callable)
      throws Throwable {
    TransportRuntimeComponent original;
    synchronized (TransportRuntime.class) {
      original = instance;
      instance = component;
    }
    try {
      callable.call();
    } finally {
      synchronized (TransportRuntime.class) {
        instance = original;
      }
    }
  }

  /** Returns a {@link TransportFactory} for a given {@code backendName}. */
  @Deprecated
  public TransportFactory newFactory(String backendName) {
    return new TransportFactoryImpl(
        getSupportedEncodings(null),
        TransportContext.builder().setBackendName(backendName).build(),
        this);
  }

  /** Returns a {@link TransportFactory} for a given {@code backendName}. */
  public TransportFactory newFactory(Destination destination) {
    return new TransportFactoryImpl(
        getSupportedEncodings(destination),
        TransportContext.builder()
            .setBackendName(destination.getName())
            .setExtras(destination.getExtras())
            .build(),
        this);
  }

  private static Set<Encoding> getSupportedEncodings(Destination destination) {
    if (destination instanceof EncodedDestination) {
      EncodedDestination encodedDestination = (EncodedDestination) destination;
      return Collections.unmodifiableSet(encodedDestination.getSupportedEncodings());
    }
    return Collections.singleton(Encoding.of("proto"));
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public Uploader getUploader() {
    return uploader;
  }

  @Override
  public void send(SendRequest request, TransportScheduleCallback callback) {
    scheduler.schedule(
        request.getTransportContext().withPriority(request.getEvent().getPriority()),
        convert(request),
        callback);
  }

  private EventInternal convert(SendRequest request) {
    return EventInternal.builder()
        .setEventMillis(eventClock.getTime())
        .setUptimeMillis(uptimeClock.getTime())
        .setTransportName(request.getTransportName())
        .setEncodedPayload(new EncodedPayload(request.getEncoding(), request.getPayload()))
        .setCode(request.getEvent().getCode())
        .build();
  }
}
