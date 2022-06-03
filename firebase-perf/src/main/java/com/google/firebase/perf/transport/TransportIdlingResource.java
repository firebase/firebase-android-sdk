package com.google.firebase.perf.transport;

import androidx.test.espresso.idling.CountingIdlingResource;

public class TransportIdlingResource {
  private static final CountingIdlingResource idlingResource =
      new CountingIdlingResource("FIREPERF_TRANSPORT_THREADS");

  public static CountingIdlingResource get() {
    return idlingResource;
  }

  public static void increment() {
    idlingResource.increment();
  }

  public static void decrement() {
    idlingResource.decrement();
  }
}
