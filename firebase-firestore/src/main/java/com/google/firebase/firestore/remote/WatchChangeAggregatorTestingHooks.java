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
import com.google.firebase.firestore.util.Executors;
import com.google.firestore.v1.BloomFilter;
import java.util.HashMap;
import java.util.Map;

final class WatchChangeAggregatorTestingHooks {

  private WatchChangeAggregatorTestingHooks() {}

  private static final Map<Object, ExistenceFilterMismatchListener>
      existenceFilterMismatchListeners = new HashMap<>();

  /**
   * Notifies all registered {@link ExistenceFilterMismatchListener}` listeners registered via
   * {@link #addExistenceFilterMismatchListener}.
   *
   * @param info Information about the existence filter mismatch to deliver to the listeners.
   */
  static void notifyOnExistenceFilterMismatch(ExistenceFilterMismatchInfo info) {
    synchronized (existenceFilterMismatchListeners) {
      for (ExistenceFilterMismatchListener listener : existenceFilterMismatchListeners.values()) {
        Executors.BACKGROUND_EXECUTOR.execute(() -> listener.onExistenceFilterMismatch(info));
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
  @VisibleForTesting
  static ListenerRegistration addExistenceFilterMismatchListener(
      @NonNull ExistenceFilterMismatchListener listener) {
    checkNotNull(listener, "a null listener is not allowed");

    Object listenerId = new Object();
    synchronized (existenceFilterMismatchListeners) {
      existenceFilterMismatchListeners.put(listenerId, listener);
    }

    return () -> {
      synchronized (existenceFilterMismatchListeners) {
        existenceFilterMismatchListeners.remove(listenerId);
      }
    };
  }

  interface ExistenceFilterMismatchListener {
    @AnyThread
    void onExistenceFilterMismatch(ExistenceFilterMismatchInfo info);
  }

  @AutoValue
  abstract static class ExistenceFilterMismatchInfo {

    static ExistenceFilterMismatchInfo create(
        int localCacheCount,
        int existenceFilterCount,
        @Nullable ExistenceFilterBloomFilterInfo bloomFilter) {
      return new AutoValue_WatchChangeAggregatorTestingHooks_ExistenceFilterMismatchInfo(
          localCacheCount, existenceFilterCount, bloomFilter);
    }

    /** Returns the number of documents that matched the query in the local cache. */
    abstract int localCacheCount();

    /**
     * Returns the number of documents that matched the query on the server, as specified in the
     * ExistenceFilter message's `count` field.
     */
    abstract int existenceFilterCount();

    /**
     * Returns information about the bloom filter provided by Watch in the ExistenceFilter message's
     * `unchangedNames` field. A `null` return value means that Watch did _not_ provide a bloom
     * filter.
     */
    @Nullable
    abstract ExistenceFilterBloomFilterInfo bloomFilter();

    static ExistenceFilterMismatchInfo from(
        boolean bloomFilterApplied, int localCacheCount, ExistenceFilter existenceFilter) {
      return create(
          localCacheCount,
          existenceFilter.getCount(),
          ExistenceFilterBloomFilterInfo.from(bloomFilterApplied, existenceFilter));
    }
  }

  @AutoValue
  abstract static class ExistenceFilterBloomFilterInfo {

    static ExistenceFilterBloomFilterInfo create(
        boolean applied, int hashCount, int bitmapLength, int padding) {
      return new AutoValue_WatchChangeAggregatorTestingHooks_ExistenceFilterBloomFilterInfo(
          applied, hashCount, bitmapLength, padding);
    }

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

    static ExistenceFilterBloomFilterInfo from(
        boolean bloomFilterApplied, ExistenceFilter existenceFilter) {
      BloomFilter unchangedNames = existenceFilter.getUnchangedNames();
      if (unchangedNames == null) {
        return null;
      }
      return create(
          bloomFilterApplied,
          unchangedNames.getHashCount(),
          unchangedNames.getBits().getBitmap().size(),
          unchangedNames.getBits().getPadding());
    }
  }
}
