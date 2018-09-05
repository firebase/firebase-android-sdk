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

import com.google.firebase.firestore.remote.Stream.StreamCallback;
import io.grpc.Status;

/**
 * A Stream is an interface that represents a streaming RPC to the Firestore backend. It's built on
 * top of GRPC's own support for streaming RPCs, and adds several critical features for our clients:
 *
 * <ul>
 *   <li>Exponential backoff on failure
 *   <li>Authentication via CredentialsProvider
 *   <li>Dispatching all callbacks into the shared worker queue
 *   <li>Closing idle streams after 60 seconds of inactivity
 * </ul>
 *
 * <p>Implementations of Stream should use AbstractStream and provide their own serialization of
 * models to and from the protocol buffers for a specific streaming RPC.
 *
 * <p>## Starting and Stopping
 *
 * <p>Streaming RPCs are stateful and need to be {@code start}ed before messages can be sent and
 * received. The Stream will call its onOpen once the stream is ready to accept requests.
 *
 * <p>Should a @{code start} fail, Stream will call the onClose method of the provided listener.
 *
 * @param <CallbackType> The type which is used for stream specific callbacks.
 */
public interface Stream<CallbackType extends StreamCallback> {

  /** Returns true if the RPC has been created locally and has started the process of connecting. */
  boolean isStarted();

  /** Returns true if the RPC will accept messages to send. */
  boolean isOpen();

  /**
   * Starts the RPC. Only allowed if {@code isStarted()} returns false. The stream is immediately
   * ready for use.
   *
   * <p>When start returns, {@code isStarted()} will return true.
   */
  void start();

  /**
   * Stops the RPC. This is guaranteed *not* to call the onClose of the listener in order to ensure
   * that any recovery logic there does not attempt to reuse the stream.
   *
   * <p>When stop returns {@code isStarted()} will return false.
   */
  void stop();

  /**
   * After an error the stream will usually back off on the next attempt to start it. If the error
   * warrants an immediate restart of the stream, the sender can use this to indicate that the
   * receiver should not back off.
   *
   * <p>Each error will call the {@code onClose()} method of the listener. That listener can decide
   * to inhibit backoff if required.
   */
  void inhibitBackoff();

  /**
   * AbstractStream can be in one of 5 states (each described in detail below) based on the
   * following state transition diagram:
   *
   * <pre>
   *          start() called             auth & connection succeeded
   * INITIAL ----------------> STARTING -----------------------------> OPEN
   *                             ^  |                                   |
   *                             |  |                    error occurred |
   *                             |  \-----------------------------v-----/
   *                             |                                |
   *                    backoff  |                                |
   *                    elapsed  |              start() called    |
   *                             \--- BACKOFF <---------------- ERROR
   *
   * [any state] --------------------------> INITIAL
   *               stop() called or
   *               idle timer expired
   * </pre>
   */
  enum State {
    /**
     * The streaming RPC is not yet running and there is no error condition. Calling {@code start()}
     * will start the stream immediately without backoff. While in this state {@code isStarted()}
     * will return false.
     */
    Initial,

    /**
     * The stream is starting, either waiting for an auth token or for the stream to successfully
     * open. While in this state, {@code isStarted()} will return true but {@code isOpen()} will
     * return false.
     *
     * <p>Porting Note: Auth is handled transparently by gRPC in this implementation, so this state
     * is used as intermediate state until the {@code onOpen()} callback is called.
     */
    Starting,

    /**
     * The streaming RPC is up and running. Requests and responses can flow freely. Both {@code
     * isStarted()} and {@code isOpen()} will return true.
     */
    Open,

    /**
     * The stream encountered an error. The next start attempt will back off. While in this state
     * {@code isStarted()} will return false.
     */
    Error,

    /**
     * An in-between state after an error where the stream is waiting before re-starting. After
     * waiting is complete, the stream will try to open. While in this state {@code isStarted()}
     * will return true but {@code isOpen()} will return false.
     */
    Backoff,
  }

  /**
   * A (super-interface) for the stream callbacks. Implementations of Stream should provide their
   * own interface that extends this interface.
   */
  interface StreamCallback {
    /** The stream is now open and is accepting messages */
    void onOpen();

    /** The stream has closed. If there was an error, the status will be != OK. */
    void onClose(Status status);
  }
}
