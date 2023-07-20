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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.auto.value.AutoValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.WatchChangeAggregator.BloomFilterApplicationStatus;
import com.google.firestore.v1.BloomFilter;
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
   * Synchronously notifies all registered {@link ExistenceFilterMismatchListener}` listeners
   * registered via {@link #addExistenceFilterMismatchListener}.
   *
   * @param info Information about the existence filter mismatch to deliver to the listeners.
   */
  void notifyOnExistenceFilterMismatch(@NonNull ExistenceFilterMismatchInfo info) {
    for (AtomicReference<ExistenceFilterMismatchListener> listenerRef :
        existenceFilterMismatchListeners) {
      ExistenceFilterMismatchListener listener = listenerRef.get();
      if (listener != null) {
        listener.onExistenceFilterMismatch(info);
      }
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
   * <p>The listener callbacks are performed synchronously in `NotifyOnExistenceFilterMismatch()`;
   * therefore, listeners should perform their work as quickly as possible and return to avoid
   * blocking any critical work. In particular, the listener callbacks should <em>not</em> block or
   * perform long-running operations.
   *
   * @param listener the listener to register.
   * @return an object that unregisters the given listener via its {@link
   *     ListenerRegistration#remove} method; only the first unregistration request does anything;
   *     all subsequent requests do nothing. Note that due to inherent race conditions it is
   *     technically possible, although unlikely, that callbacks could still occur <em>after</em>
   *     unregistering.
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
    static ExistenceFilterMismatchInfo create(
        int localCacheCount,
        int existenceFilterCount,
        String projectId,
        String databaseId,
        @Nullable ExistenceFilterBloomFilterInfo bloomFilter) {
      return new AutoValue_TestingHooks_ExistenceFilterMismatchInfo(
          localCacheCount, existenceFilterCount, projectId, databaseId, bloomFilter);
    }

    /** Returns the number of documents that matched the query in the local cache. */
    abstract int localCacheCount();

    /**
     * Returns the number of documents that matched the query on the server, as specified in the
     * ExistenceFilter message's `count` field.
     */
    abstract int existenceFilterCount();

    /** The projectId used when checking documents for membership in the bloom filter. */
    abstract String projectId();

    /** The databaseId used when checking documents for membership in the bloom filter. */
    abstract String databaseId();

    /**
     * Returns information about the bloom filter provided by Watch in the ExistenceFilter message's
     * `unchangedNames` field. A `null` return value means that Watch did _not_ provide a bloom
     * filter.
     */
    @Nullable
    abstract ExistenceFilterBloomFilterInfo bloomFilter();

    /**
     * Convenience method to create and return a new instance of {@link ExistenceFilterMismatchInfo}
     * with the values taken from the given arguments.
     */
    static ExistenceFilterMismatchInfo from(
        int localCacheCount,
        ExistenceFilter existenceFilter,
        DatabaseId databaseId,
        @Nullable com.google.firebase.firestore.remote.BloomFilter bloomFilter,
        BloomFilterApplicationStatus bloomFilterStatus) {
      return create(
          localCacheCount,
          existenceFilter.getCount(),
          databaseId.getProjectId(),
          databaseId.getDatabaseId(),
          ExistenceFilterBloomFilterInfo.from(bloomFilter, bloomFilterStatus, existenceFilter));
    }
  }

  @AutoValue
  abstract static class ExistenceFilterBloomFilterInfo {

    static ExistenceFilterBloomFilterInfo create(
        @Nullable com.google.firebase.firestore.remote.BloomFilter bloomFilter,
        boolean applied,
        int hashCount,
        int bitmapLength,
        int padding) {
      return new AutoValue_TestingHooks_ExistenceFilterBloomFilterInfo(
          bloomFilter, applied, hashCount, bitmapLength, padding);
    }

    /** The BloomFilter created from the existence filter; may be null if creating it failed. */
    @Nullable
    abstract com.google.firebase.firestore.remote.BloomFilter bloomFilter();

    /**
     * Returns whether a full requery was averted by using the bloom filter. If false, then
     * something happened, such as a false positive, to prevent using the bloom filter to avoid a
     * full requery.
     */
    abstract boolean applied();

    /** Returns the number of hash functions used in the bloom filter. */
    abstract int hashCount();

    /** Returns the number of bytes in the bloom filter's bitmask. */
    abstract int bitmapLength();

    /** Returns the number of bits of padding in the last byte of the bloom filter. */
    abstract int padding();

    @Nullable
    static ExistenceFilterBloomFilterInfo from(
        @Nullable com.google.firebase.firestore.remote.BloomFilter bloomFilter,
        BloomFilterApplicationStatus bloomFilterStatus,
        ExistenceFilter existenceFilter) {
      BloomFilter unchangedNames = existenceFilter.getUnchangedNames();
      if (unchangedNames == null) {
        return null;
      }
      return create(
          bloomFilter,
          /*bloomFilterApplied=*/ bloomFilterStatus == BloomFilterApplicationStatus.SUCCESS,
          unchangedNames.getHashCount(),
          unchangedNames.getBits().getBitmap().size(),
          unchangedNames.getBits().getPadding());
    }
  }
}
