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

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.firestore.core.OnlineState;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.DelayedTask;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firebase.firestore.util.Logger;
import io.grpc.Status;
import java.util.Locale;

/**
 * A component used by the RemoteStore to track the OnlineState (that is, whether or not the client
 * as a whole should be considered to be online or offline), implementing the appropriate
 * heuristics.
 *
 * <p>In particular, when the client is trying to connect to the backend, we allow up to
 * MAX_WATCH_STREAM_FAILURES within ONLINE_STATE_TIMEOUT_MS for a connection to succeed. If we have
 * too many failures or the timeout elapses, then we set the OnlineState to OFFLINE, and the client
 * will behave as if it is offline (get() calls will return cached data, etc.).
 */
class OnlineStateTracker {

  interface OnlineStateCallback {
    /**
     * Called whenever the online state of the client changes. This is based on the watch stream for
     * now.
     */
    void handleOnlineStateChange(OnlineState onlineState);
  }

  // To deal with transient failures, we allow multiple stream attempts before giving up and
  // transitioning from OnlineState.UNKNOWN to OFFLINE.
  // TODO(mikelehen): This used to be set to 2 as a mitigation for b/66228394. @jdimond thinks that
  // bug is sufficiently fixed so that we can set this back to 1. If that works okay, we could
  // potentially remove this logic entirely.
  private static final int MAX_WATCH_STREAM_FAILURES = 1;

  // To deal with stream attempts that don't succeed or fail in a timely manner, we have a
  // timeout for OnlineState to reach ONLINE or OFFLINE. If the timeout is reached, we transition
  // to OFFLINE rather than waiting indefinitely.
  private static final int ONLINE_STATE_TIMEOUT_MS = 10 * 1000;

  /** The log tag to use for this class. */
  private static final String LOG_TAG = "OnlineStateTracker";

  // The current OnlineState.
  private OnlineState state;

  // A count of consecutive failures to open the stream. If it reaches the maximum defined by
  // MAX_WATCH_STREAM_FAILURES, we'll revert to OnlineState.OFFLINE.
  private int watchStreamFailures;

  // A timer that elapses after ONLINE_STATE_TIMEOUT_MS, at which point we transition from
  // OnlineState.UNKNOWN to OFFLINE without waiting for the stream to actually fail
  // (MAX_WATCH_STREAM_FAILURES times).
  private DelayedTask onlineStateTimer;

  // Whether the client should log a warning message if it fails to connect to the backend
  // (initially true, cleared after a successful stream, or if we've logged the message already).
  private boolean shouldWarnClientIsOffline;

  // The AsyncQueue to use for running timers (and calling OnlineStateCallback methods).
  private final AsyncQueue workerQueue;

  // The callback to notify on OnlineState changes.
  private final OnlineStateCallback onlineStateCallback;

  OnlineStateTracker(AsyncQueue workerQueue, OnlineStateCallback onlineStateCallback) {
    this.workerQueue = workerQueue;
    this.onlineStateCallback = onlineStateCallback;
    state = OnlineState.UNKNOWN;
    shouldWarnClientIsOffline = true;
  }

  /**
   * Called by RemoteStore when a watch stream is started (including on each backoff attempt).
   *
   * <p>If this is the first attempt, it sets the OnlineState to UNKNOWN and starts the
   * onlineStateTimer.
   */
  void handleWatchStreamStart() {
    if (watchStreamFailures == 0) {
      setAndBroadcastState(OnlineState.UNKNOWN);

      hardAssert(onlineStateTimer == null, "onlineStateTimer shouldn't be started yet");
      onlineStateTimer =
          workerQueue.enqueueAfterDelay(
              TimerId.ONLINE_STATE_TIMEOUT,
              ONLINE_STATE_TIMEOUT_MS,
              () -> {
                onlineStateTimer = null;
                hardAssert(
                    state == OnlineState.UNKNOWN,
                    "Timer should be canceled if we transitioned to a different state.");
                logClientOfflineWarningIfNecessary(
                    String.format(
                        Locale.ENGLISH,
                        "Backend didn't respond within %d seconds\n",
                        ONLINE_STATE_TIMEOUT_MS / 1000));
                setAndBroadcastState(OnlineState.OFFLINE);

                // NOTE: handleWatchStreamFailure() will continue to increment watchStreamFailures
                // even though we are already marked OFFLINE but this is non-harmful.
              });
    }
  }

  /**
   * Called by RemoteStore when a watch stream fails.
   *
   * <p>Updates our OnlineState as appropriate. The first failure moves us to OnlineState.UNKNOWN.
   * We then may allow multiple failures (based on MAX_WATCH_STREAM_FAILURES) before we actually
   * transition to OnlineState.OFFLINE.
   */
  void handleWatchStreamFailure(Status status) {
    if (state == OnlineState.ONLINE) {
      setAndBroadcastState(OnlineState.UNKNOWN);

      // To get to OnlineState.ONLINE, updateState() must have been called which would have reset
      // our heuristics.
      hardAssert(this.watchStreamFailures == 0, "watchStreamFailures must be 0");
      hardAssert(this.onlineStateTimer == null, "onlineStateTimer must be null");
    } else {
      watchStreamFailures++;
      if (watchStreamFailures >= MAX_WATCH_STREAM_FAILURES) {
        clearOnlineStateTimer();
        logClientOfflineWarningIfNecessary(
            String.format(
                Locale.ENGLISH,
                "Connection failed %d times. Most recent error: %s",
                MAX_WATCH_STREAM_FAILURES,
                status));
        setAndBroadcastState(OnlineState.OFFLINE);
      }
    }
  }

  /**
   * Explicitly sets the OnlineState to the specified state.
   *
   * <p>Note that this resets the timers / failure counters, etc. used by our offline heuristics, so
   * it must not be used in place of handleWatchStreamStart() and handleWatchStreamFailure().
   */
  void updateState(OnlineState newState) {
    clearOnlineStateTimer();
    watchStreamFailures = 0;

    if (newState == OnlineState.ONLINE) {
      // We've connected to watch at least once. Don't warn the developer about being offline going
      // forward.
      shouldWarnClientIsOffline = false;
    }

    setAndBroadcastState(newState);
  }

  private void setAndBroadcastState(OnlineState newState) {
    if (newState != state) {
      state = newState;
      onlineStateCallback.handleOnlineStateChange(newState);
    }
  }

  private void logClientOfflineWarningIfNecessary(String reason) {
    String message =
        String.format(
            "Could not reach Cloud Firestore backend. %s\n"
                + "This typically indicates that your device does not have a healthy Internet "
                + "connection at the moment. The client will operate in offline mode until it is "
                + "able to successfully connect to the backend.",
            reason);

    if (shouldWarnClientIsOffline) {
      Logger.warn(LOG_TAG, "%s", message);
      shouldWarnClientIsOffline = false;
    } else {
      Logger.debug(LOG_TAG, "%s", message);
    }
  }

  private void clearOnlineStateTimer() {
    if (onlineStateTimer != null) {
      onlineStateTimer.cancel();
      onlineStateTimer = null;
    }
  }
}
