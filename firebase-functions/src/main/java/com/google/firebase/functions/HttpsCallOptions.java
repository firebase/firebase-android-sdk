package com.google.firebase.functions;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * An internal class for keeping track of options applied to an HttpsCallableReference.
 */
class HttpsCallOptions {

  // The default timeout to use for all calls.
  final static private long DEFAULT_TIMEOUT = 60;
  final static private TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS;

  // The timeout to use for calls from references created by this Functions.
  private long timeout = DEFAULT_TIMEOUT;
  private TimeUnit timeoutUnits = DEFAULT_TIMEOUT_UNITS;

  /**
   * Changes the timeout for calls from this instance of Functions. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  void setTimeout(long timeout, TimeUnit units) {
    this.timeout = timeout;
    this.timeoutUnits = units;
  }

  /**
   * Creates a new OkHttpClient with these options applied to it.
   */
  OkHttpClient apply(OkHttpClient client) {
    return client.newBuilder().callTimeout(timeout, timeoutUnits).build();
  }
}
