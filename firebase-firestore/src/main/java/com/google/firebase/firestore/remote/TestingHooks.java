// Copyright 2023 Google LLC
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

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.auto.value.AutoValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.util.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages "testing hooks", hooks into the internals of the SDK to verify internal state and events
 * during integration tests.
 *
 * <p>Do not use this class except for testing purposes.
 */
@VisibleForTesting
final class TestingHooks {

  private static final TestingHooks instance = new TestingHooks();

  // Use CopyOnWriteArrayList to store the listeners so that we don't need to worry about
  // synchronizing adds, removes, and traversals.
  private final CopyOnWriteArrayList<AtomicReference<ExistenceFilterMismatchListener>>
      existenceFilterMismatchListeners = new CopyOnWriteArrayList<>();

  private TestingHooks() {}

  /** Returns the singleton instance of this class. */
  @NonNull
  static TestingHooks getInstance() {
    return instance;
  }

  /**
   * Asynchronously notifies all registered {@link ExistenceFilterMismatchListener}` listeners
   * registered via {@link #addExistenceFilterMismatchListener}.
   *
   * @param info Information about the existence filter mismatch to deliver to the listeners.
   */
  void notifyOnExistenceFilterMismatch(@NonNull ExistenceFilterMismatchInfo info) {
    for (AtomicReference<ExistenceFilterMismatchListener> listenerRef :
        existenceFilterMismatchListeners) {
      Executors.BACKGROUND_EXECUTOR.execute(
          () -> {
            ExistenceFilterMismatchListener listener = listenerRef.get();
            if (listener != null) {
              listener.onExistenceFilterMismatch(info);
            }
          });
    }
  }

  /**
   * Registers a {@link ExistenceFilterMismatchListener} to be notified when an existence filter
   * mismatch occurs in the Watch listen stream.
   *
   * <p>The relative order in which callbacks are notified is unspecified; do not rely on any
   * particular ordering. If a given callback is registered multiple times then it will be notified
   * multiple times, once per registration.
   *
   * <p>The thread on which the callback occurs is unspecified; listeners should perform their work
   * as quickly as possible and return to avoid blocking any critical work. In particular, the
   * listener callbacks should <em>not</em> block or perform long-running operations. Listener
   * callbacks can occur concurrently with other callbacks on the same and other listeners.
   *
   * @param listener the listener to register.
   * @return an object that unregisters the given listener via its {@link
   *     ListenerRegistration#remove} method; only the first unregistration request does anything;
   *     all subsequent requests do nothing.
   */
  ListenerRegistration addExistenceFilterMismatchListener(
      @NonNull ExistenceFilterMismatchListener listener) {
    checkNotNull(listener, "a null listener is not allowed");

    AtomicReference<ExistenceFilterMismatchListener> listenerRef = new AtomicReference<>(listener);
    existenceFilterMismatchListeners.add(listenerRef);

    return () -> {
      listenerRef.set(null);
      existenceFilterMismatchListeners.remove(listenerRef);
    };
  }

  /**
   * Implementations of this interface can be registered with {@link
   * #addExistenceFilterMismatchListener}.
   */
  interface ExistenceFilterMismatchListener {

    /**
     * Invoked when an existence filter mismatch occurs.
     *
     * @param info information about the existence filter mismatch.
     */
    @AnyThread
    void onExistenceFilterMismatch(@NonNull ExistenceFilterMismatchInfo info);
  }

  /**
   * Information about an existence filter mismatch, as specified to listeners registered with
   * {@link #addExistenceFilterMismatchListener}.
   */
  @AutoValue
  abstract static class ExistenceFilterMismatchInfo {

    /**
     * Creates and returns a new instance of {@link ExistenceFilterMismatchInfo} with the given
     * values.
     */
    static ExistenceFilterMismatchInfo create(int localCacheCount, int existenceFilterCount) {
      return new AutoValue_TestingHooks_ExistenceFilterMismatchInfo(
          localCacheCount, existenceFilterCount);
    }

    /** Returns the number of documents that matched the query in the local cache. */
    abstract int localCacheCount();

    /**
     * Returns the number of documents that matched the query on the server, as specified in the
     * ExistenceFilter message's `count` field.
     */
    abstract int existenceFilterCount();

    /**
     * Convenience method to create and return a new instance of {@link ExistenceFilterMismatchInfo}
     * with the values taken from the given arguments.
     */
    static ExistenceFilterMismatchInfo from(int localCacheCount, ExistenceFilter existenceFilter) {
      return create(localCacheCount, existenceFilter.getCount());
    }
  }
}
