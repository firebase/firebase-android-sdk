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

import com.google.firebase.firestore.ListenerRegistration;

/**
 * Provides access to the {@link WatchChangeAggregatorTestingHooks} class and its methods.
 *
 * The {@link WatchChangeAggregatorTestingHooks} class has default visibility, and, therefore, is
 * only visible to other classes declared in the same package. This class effectively "re-exports"
 * the functionality from {@link WatchChangeAggregatorTestingHooks} in a class with {@code public}
 * visibility so that tests written in other packages can access its functionality.
 */
public final class WatchChangeAggregatorTestingHooksAccessor {

  private WatchChangeAggregatorTestingHooksAccessor() {}

  /**
   * @see WatchChangeAggregatorTestingHooks#addExistenceFilterMismatchListener
   */
  public static ListenerRegistration addExistenceFilterMismatchListener(
          @NonNull ExistenceFilterMismatchListener listener) {
    checkNotNull(listener, "a null listener is not allowed");
    return WatchChangeAggregatorTestingHooks.addExistenceFilterMismatchListener(new ExistenceFilterMismatchListenerWrapper(listener));
  }

  /**
   * @see WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchListener
   */
  public interface ExistenceFilterMismatchListener {
    @AnyThread
    void onExistenceFilterMismatch(ExistenceFilterMismatchInfo info);
  }

  /**
   * @see WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo
   */
  public interface ExistenceFilterMismatchInfo {
    int localCacheCount();
    int existenceFilterCount();
    @Nullable ExistenceFilterBloomFilterInfo bloomFilter();
  }

  /**
   * @see WatchChangeAggregatorTestingHooks.ExistenceFilterBloomFilterInfo
   */
  public interface ExistenceFilterBloomFilterInfo {
    boolean applied();
    int hashCount();
    int bitmapLength();
    int padding();
  }

  private static final class ExistenceFilterMismatchInfoImpl implements ExistenceFilterMismatchInfo {

    private final WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo info;

    ExistenceFilterMismatchInfoImpl(@NonNull WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo info) {
      this.info = info;
    }

    @Override
    public int localCacheCount() {
      return info.localCacheCount();
    }

    @Override
    public int existenceFilterCount() {
      return info.existenceFilterCount();
    }

    @Nullable
    @Override
    public ExistenceFilterBloomFilterInfo bloomFilter() {
      WatchChangeAggregatorTestingHooks.ExistenceFilterBloomFilterInfo bloomFilterInfo = info.bloomFilter();
      return bloomFilterInfo == null ? null : new ExistenceFilterBloomFilterInfoImpl(bloomFilterInfo);
    }
  }

  private static final class ExistenceFilterBloomFilterInfoImpl implements ExistenceFilterBloomFilterInfo {

    private final WatchChangeAggregatorTestingHooks.ExistenceFilterBloomFilterInfo info;

    ExistenceFilterBloomFilterInfoImpl(@NonNull WatchChangeAggregatorTestingHooks.ExistenceFilterBloomFilterInfo info) {
      this.info = info;
    }

    @Override
    public boolean applied() {
      return info.applied();
    }

    @Override
    public int hashCount() {
      return info.hashCount();
    }

    @Override
    public int bitmapLength() {
      return info.bitmapLength();
    }

    @Override
    public int padding() {
      return info.padding();
    }
  }

  private static final class ExistenceFilterMismatchListenerWrapper implements WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchListener {

    private final ExistenceFilterMismatchListener wrappedListener;

    ExistenceFilterMismatchListenerWrapper(@NonNull ExistenceFilterMismatchListener listenerToWrap) {
      this.wrappedListener = listenerToWrap;
    }

    @Override
    public void onExistenceFilterMismatch(WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo info) {
      this.wrappedListener.onExistenceFilterMismatch(new ExistenceFilterMismatchInfoImpl(info));
    }
  }

}
