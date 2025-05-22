// Copyright 2024 Google LLC
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

package com.google.firebase.firestore;

import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;
import com.google.android.gms.tasks.Task;
import com.google.common.base.Function;
import com.google.firebase.firestore.core.FirestoreClient;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.concurrent.Executor;

/**
 * The `FirestoreClientProvider` handles the life cycle of `FirestoreClient`s within a `Firestore`
 * instance.
 *
 * The instantiation of `FirestoreClient` is delayed until there is a need for the client. This
 * delay affords changes to configuration through the `Firestore` instance prior to performing a
 * query. After instantiation of the `FirestoreClient`, the `Firestore` instance is considered
 * configured, and any subsequent attempt to modify configuration will throw anexception.
 *
 *  Access to `FirestoreClient` is via synchronized indirection to ensure the `FirestoreClient` is
 * configured, instantiated and current. The `FirestoreClient` should be considered ephemeral, such
 * that no reference to `FirestoreClient` should be retained outside of this provider.
 *
 * All calls to the `FirestoreClient` should be done through access methods in the
 * `FirestoreClientProvider`. Access methods take a functional block of code as a parameter. The
 * most current `FirestoreClient` instance will be applied to the functional block of code.
 * Execution of the functional block of code will be synchronous to ensure the `FirestoreClient`
 * instance remains current during execution.
 *
 * Retaining a reference to `FirestoreClient` outside of `FirestoreClientProvider` risks calling a
 * no longer current `FirestoreClient`. Internally, the `FirestoreClient` may self reference, but
 * this is with intent to couple internal logic with a specific `FirestoreClient` instance.
 *
 * The life of a `FirestoreClient` is tightly coupled to the life the internal `AsyncQueue`. The
 * `AsyncQueue` is associated with exactly one `FirestoreClient`, and when that `FirestoreClient` is
 * terminated, the `AsyncQueue` is shutdown. Internal coupling within `FirestoreClient` relies on
 * `AsyncQueue` to stop processing upon shutdown. A terminated `FirestoreClient` will also rely on
 * `AsyncQueue` to safeguard against external access.
 */
final class FirestoreClientProvider {

  private final Function<AsyncQueue, FirestoreClient> clientFactory;

  @GuardedBy("this")
  private FirestoreClient client;

  @GuardedBy("this")
  private AsyncQueue asyncQueue;

  FirestoreClientProvider(Function<AsyncQueue, FirestoreClient> clientFactory) {
    this.clientFactory = clientFactory;
    this.asyncQueue = new AsyncQueue();
  }

  /**
   * Indicates whether `FirestoreClient` has been instantiated thereby preventing change to
   * configuration.
   */
  boolean isConfigured() {
    return client != null;
  }

  /**
   * Prevents further change to configuration, and instantiates the `FirestoreClient` instance
   * to be ready for use.
   */
  synchronized void ensureConfigured() {
    if (!isConfigured()) {
      client = clientFactory.apply(asyncQueue);
    }
  }

  /**
   * To facilitate calls to FirestoreClient without risk of FirestoreClient being terminated
   * or restarted mid call.
   */
  synchronized <T> T call(Function<FirestoreClient, T> call) {
    ensureConfigured();
    return call.apply(client);
  }

  /**
   * To facilitate calls to FirestoreClient without risk of FirestoreClient being terminated
   * or restarted mid call.
   */
  synchronized void procedure(Consumer<FirestoreClient> call) {
    ensureConfigured();
    call.accept(client);
  }

  /**
   * Conditional execution based on whether `FirestoreClient` is up and running.
   *
   * Handling the conditional logic as part of `FirestoreClientProvider` prevents possible race
   * condition between condition check and execution functional block of code.
   *
   * Example, clearing the cache can only be done while `FirestoreClient` is not running. Checking
   * whether `FirestoreClient` is running and then performing clearing of cache outside of a
   * synchronized code block, risks another thread instantiating `FirestoreClient` after check, but
   * before running code to clear cache.
   *
   * @param callIf Executes if client is shutdown or client hasn't been started yet.
   * @param callElse Executes if client is running.
   * @return Result of execution.
   */
  synchronized <T> T executeIfShutdown(
      Function<Executor, T> callIf, Function<Executor, T> callElse) {
    Executor executor = command -> asyncQueue.enqueueAndForgetEvenAfterShutdown(command);
    if (client == null || client.isTerminated()) {
      return callIf.apply(executor);
    } else {
      return callElse.apply(executor);
    }
  }

  /**
   * Shuts down the AsyncQueue and releases resources after which no progress will ever be made
   * again.
   */
  synchronized Task<Void> terminate() {
    // The client must be initialized to ensure that all subsequent API usage throws an exception.
    ensureConfigured();

    Task<Void> terminate = client.terminate();

    // Will cause the executor to de-reference all threads, the best we can do
    asyncQueue.shutdown();

    return terminate;
  }

  /**
   * Direct access to internal AsyncQueue.
   *
   * The danger of using this method is retaining non-synchronized direct access to AsyncQueue.
   *
   * @return internal AsyncQueue
   */
  @VisibleForTesting
  AsyncQueue getAsyncQueue() {
    return asyncQueue;
  }
}
