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

import static com.google.firebase.firestore.remote.Datastore.SSL_DEPENDENCY_ERROR_MESSAGE;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.firestore.remote.Stream.StreamCallback;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.DelayedTask;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firebase.firestore.util.ExponentialBackoff;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Util;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An AbstractStream is an abstract base class that implements the Stream interface.
 *
 * @param <ReqT> The proto type that will be sent in this stream
 * @param <RespT> The proto type that is received through this stream
 * @param <CallbackT> The type which is used for stream specific callbacks.
 */
abstract class AbstractStream<ReqT, RespT, CallbackT extends StreamCallback>
    implements Stream<CallbackT> {

  /**
   * A "runner" that runs operations but only if closeCount remains unchanged. This allows us to
   * turn auth / stream callbacks into no-ops if the stream is closed / re-opened, etc.
   *
   * <p>PORTING NOTE: Because all the stream callbacks already happen on the workerQueue, we don't
   * need to dispatch onto the queue, and so we instead only expose a run() method which asserts
   * that we're already on the workerQueue.
   */
  class CloseGuardedRunner {
    private final long initialCloseCount;

    CloseGuardedRunner(long initialCloseCount) {
      this.initialCloseCount = initialCloseCount;
    }

    void run(Runnable task) {
      workerQueue.verifyIsCurrentThread();
      if (closeCount == initialCloseCount) {
        task.run();
      } else {
        Logger.debug(
            AbstractStream.this.getClass().getSimpleName(),
            "stream callback skipped by CloseGuardedRunner.");
      }
    }
  }

  /** Implementation of IncomingStreamObserver that runs callbacks via CloseGuardedRunner. */
  class StreamObserver implements IncomingStreamObserver<RespT> {
    private final CloseGuardedRunner dispatcher;

    StreamObserver(CloseGuardedRunner dispatcher) {
      this.dispatcher = dispatcher;
    }

    @Override
    public void onHeaders(Metadata headers) {
      dispatcher.run(
          () -> {
            if (Logger.isDebugEnabled()) {
              Map<String, String> allowlistedHeaders = new HashMap<>();
              for (String header : headers.keys()) {
                if (Datastore.WHITE_LISTED_HEADERS.contains(header.toLowerCase(Locale.ENGLISH))) {
                  allowlistedHeaders.put(
                      header,
                      headers.get(Metadata.Key.of(header, Metadata.ASCII_STRING_MARSHALLER)));
                }
              }
              if (!allowlistedHeaders.isEmpty()) {
                Logger.debug(
                    AbstractStream.this.getClass().getSimpleName(),
                    "(%x) Stream received headers: %s",
                    System.identityHashCode(AbstractStream.this),
                    allowlistedHeaders);
              }
            }
          });
    }

    @Override
    public void onNext(RespT response) {
      dispatcher.run(
          () -> {
            if (Logger.isDebugEnabled()) {
              Logger.debug(
                  AbstractStream.this.getClass().getSimpleName(),
                  "(%x) Stream received: %s",
                  System.identityHashCode(AbstractStream.this),
                  response);
            }
            AbstractStream.this.onNext(response);
          });
    }

    @Override
    public void onOpen() {
      dispatcher.run(
          () -> {
            Logger.debug(
                AbstractStream.this.getClass().getSimpleName(),
                "(%x) Stream is open",
                System.identityHashCode(AbstractStream.this));
            AbstractStream.this.onOpen();
          });
    }

    @Override
    public void onClose(Status status) {
      dispatcher.run(
          () -> {
            if (status.isOk()) {
              Logger.debug(
                  AbstractStream.this.getClass().getSimpleName(),
                  "(%x) Stream closed.",
                  System.identityHashCode(AbstractStream.this));
            } else {
              Logger.warn(
                  AbstractStream.this.getClass().getSimpleName(),
                  "(%x) Stream closed with status: %s.",
                  System.identityHashCode(AbstractStream.this),
                  status);
            }
            AbstractStream.this.handleServerClose(status);
          });
    }
  }

  /** A Runnable that is scheduled to run after the stream has idled too long. */
  @VisibleForTesting
  class IdleTimeoutRunnable implements Runnable {
    @Override
    public void run() {
      handleIdleCloseTimer();
    }
  }

  /**
   * Initial backoff time in milliseconds after an error. Set to 1s according to
   * https://cloud.google.com/apis/design/errors.
   */
  private static final long BACKOFF_INITIAL_DELAY_MS = TimeUnit.SECONDS.toMillis(1);

  private static final long BACKOFF_MAX_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
  private static final double BACKOFF_FACTOR = 1.5;

  /** The time a stream stays open after it is marked idle. */
  private static final long IDLE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1);

  /**
   * Maximum backoff time for reconnecting when we know the connection is failed on the client-side.
   */
  private static final long BACKOFF_CLIENT_NETWORK_FAILURE_MAX_DELAY_MS =
      TimeUnit.SECONDS.toMillis(10);

  @Nullable private DelayedTask idleTimer;

  private final FirestoreChannel firestoreChannel;
  private final MethodDescriptor<ReqT, RespT> methodDescriptor;
  private final IdleTimeoutRunnable idleTimeoutRunnable;

  private final AsyncQueue workerQueue;
  private final TimerId idleTimerId;
  private State state = State.Initial;

  /**
   * A close count that's incremented every time the stream is closed; used by CloseGuardedRunner to
   * invalidate callbacks that happen after close.
   */
  private long closeCount = 0;

  private ClientCall<ReqT, RespT> call;
  final ExponentialBackoff backoff;
  final CallbackT listener;

  AbstractStream(
      FirestoreChannel channel,
      MethodDescriptor<ReqT, RespT> methodDescriptor,
      AsyncQueue workerQueue,
      TimerId connectionTimerId,
      TimerId idleTimerId,
      CallbackT listener) {
    this.firestoreChannel = channel;
    this.methodDescriptor = methodDescriptor;
    this.workerQueue = workerQueue;
    this.idleTimerId = idleTimerId;
    this.listener = listener;
    this.idleTimeoutRunnable = new IdleTimeoutRunnable();

    backoff =
        new ExponentialBackoff(
            workerQueue,
            connectionTimerId,
            BACKOFF_INITIAL_DELAY_MS,
            BACKOFF_FACTOR,
            BACKOFF_MAX_DELAY_MS);
  }

  @Override
  public boolean isStarted() {
    workerQueue.verifyIsCurrentThread();
    return state == State.Starting || state == State.Open || state == State.Backoff;
  }

  @Override
  public boolean isOpen() {
    workerQueue.verifyIsCurrentThread();
    return state == State.Open;
  }

  @Override
  public void start() {
    workerQueue.verifyIsCurrentThread();
    hardAssert(call == null, "Last call still set");
    hardAssert(idleTimer == null, "Idle timer still set");

    if (state == State.Error) {
      performBackoff();
      return;
    }

    hardAssert(state == State.Initial, "Already started");

    CloseGuardedRunner closeGuardedRunner = new CloseGuardedRunner(closeCount);
    StreamObserver streamObserver = new StreamObserver(closeGuardedRunner);
    call = firestoreChannel.runBidiStreamingRpc(methodDescriptor, streamObserver);

    state = State.Starting;
  }

  /**
   * Closes the stream and cleans up as necessary:
   *
   * <ul>
   *   <li>closes the underlying GRPC stream;
   *   <li>calls the onClose handler with the given 'status';
   *   <li>sets internal stream state to 'finalState';
   *   <li>adjusts the backoff timer based on status
   * </ul>
   *
   * <p>A new stream can be opened by calling {@link #start).
   *
   * @param finalState the intended state of the stream after closing.
   * @param status the status to emit to the listener.
   */
  private void close(State finalState, Status status) {
    hardAssert(isStarted(), "Only started streams should be closed.");
    hardAssert(
        finalState == State.Error || status.equals(Status.OK),
        "Can't provide an error when not in an error state.");
    workerQueue.verifyIsCurrentThread();

    if (Datastore.isMissingSslCiphers(status)) {
      // The Android device is missing required SSL Ciphers. This error is non-recoverable and must
      // be addressed by the app developer (see https://bit.ly/2XFpdma).
      Util.crashMainThread(
          new IllegalStateException(SSL_DEPENDENCY_ERROR_MESSAGE, status.getCause()));
    }

    // Cancel any outstanding timers (they're guaranteed not to execute).
    cancelIdleCheck();
    this.backoff.cancel();

    // Invalidates any stream-related callbacks (e.g. from auth or the underlying stream),
    // guaranteeing they won't execute.
    this.closeCount++;

    Code code = status.getCode();
    if (code == Code.OK) {
      // If this is an intentional close ensure we don't delay our next connection attempt.
      backoff.reset();
    } else if (code == Code.RESOURCE_EXHAUSTED) {
      Logger.debug(
          getClass().getSimpleName(),
          "(%x) Using maximum backoff delay to prevent overloading the backend.",
          System.identityHashCode(this));
      backoff.resetToMax();
    } else if (code == Code.UNAUTHENTICATED) {
      // "unauthenticated" error means the token was rejected. Try force refreshing it in case it
      // just expired.
      firestoreChannel.invalidateToken();
    } else if (code == Code.UNAVAILABLE) {
      // This exception is thrown when the gRPC connection fails on the client side, To shorten
      // reconnect time, we can use a shorter max delay when reconnecting.
      if (status.getCause() instanceof java.net.UnknownHostException
          || status.getCause() instanceof java.net.ConnectException) {
        backoff.setTemporaryMaxDelay(BACKOFF_CLIENT_NETWORK_FAILURE_MAX_DELAY_MS);
      }
    }

    if (finalState != State.Error) {
      Logger.debug(
          getClass().getSimpleName(),
          "(%x) Performing stream teardown",
          System.identityHashCode(this));
      tearDown();
    }

    if (call != null) {
      // Clean up the underlying RPC. If this close() is in response to an error, don't attempt to
      // call half-close to avoid secondary failures.
      if (status.isOk()) {
        Logger.debug(
            getClass().getSimpleName(),
            "(%x) Closing stream client-side",
            System.identityHashCode(this));
        call.halfClose();
      }
      call = null;
    }

    // This state must be assigned before calling listener.onClose to allow the callback to
    // inhibit backoff or otherwise manipulate the state in its non-started state.
    this.state = finalState;

    // Notify the listener that the stream closed.
    listener.onClose(status);
  }

  /**
   * Can be overridden to perform additional cleanup before the stream is closed. Calling
   * super.tearDown() is not required.
   */
  protected void tearDown() {}

  @Override
  public void stop() {
    if (isStarted()) {
      close(State.Initial, Status.OK);
    }
  }

  @Override
  public void inhibitBackoff() {
    hardAssert(!isStarted(), "Can only inhibit backoff after in a stopped state");
    workerQueue.verifyIsCurrentThread();

    state = State.Initial;
    backoff.reset();
  }

  protected void writeRequest(ReqT message) {
    workerQueue.verifyIsCurrentThread();
    Logger.debug(
        getClass().getSimpleName(),
        "(%x) Stream sending: %s",
        System.identityHashCode(this),
        message);
    cancelIdleCheck();
    call.sendMessage(message);
  }

  /** Called by the idle timer when the stream should close due to inactivity. */
  private void handleIdleCloseTimer() {
    if (this.isOpen()) {
      // When timing out an idle stream there's no reason to force the stream into backoff when
      // it restarts so set the stream state to Initial instead of Error.
      close(State.Initial, Status.OK);
    }
  }

  /** Called when GRPC closes the stream, which should always be due to some error. */
  @VisibleForTesting
  void handleServerClose(Status status) {
    hardAssert(isStarted(), "Can't handle server close on non-started stream!");

    // In theory the stream could close cleanly, however, in our current model we never expect this
    // to happen because if we stop a stream ourselves, this callback will never be called. To
    // prevent cases where we retry without a backoff accidentally, we set the stream to error
    // in all cases.
    close(State.Error, status);
  }

  /** Marks the stream as available. */
  private void onOpen() {
    state = State.Open;
    this.listener.onOpen();
  }

  public abstract void onNext(RespT change);

  private void performBackoff() {
    hardAssert(state == State.Error, "Should only perform backoff in an error state");
    state = State.Backoff;

    backoff.backoffAndRun(
        () -> {
          hardAssert(state == State.Backoff, "State should still be backoff but was %s", state);
          // Momentarily set state to Initial as start() expects it.
          state = State.Initial;
          start();
          hardAssert(isStarted(), "Stream should have started");
        });
  }

  /**
   * Marks this stream as idle. If no further actions are performed on the stream for one minute,
   * the stream will automatically close itself and notify the stream's onClose() handler with
   * Status.OK. The stream will then be in a !isStarted() state, requiring the caller to start the
   * stream again before further use.
   *
   * <p>Only streams that are in state 'Open' can be marked idle, as all other states imply pending
   * network operations.
   */
  void markIdle() {
    // Starts the idle timer if we are in state 'Open' and are not yet already running a timer (in
    // which case the previous idle timeout still applies).
    if (this.isOpen() && idleTimer == null) {
      idleTimer =
          workerQueue.enqueueAfterDelay(this.idleTimerId, IDLE_TIMEOUT_MS, idleTimeoutRunnable);
    }
  }

  private void cancelIdleCheck() {
    if (idleTimer != null) {
      idleTimer.cancel();
      idleTimer = null;
    }
  }
}
