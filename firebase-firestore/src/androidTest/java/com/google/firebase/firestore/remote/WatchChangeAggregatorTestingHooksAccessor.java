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

import android.os.SystemClock;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;

/**
 * Provides access to the {@link WatchChangeAggregatorTestingHooks} class and its methods.
 *
 * <p>The {@link WatchChangeAggregatorTestingHooks} class has default visibility, and, therefore, is
 * only visible to other classes declared in the same package. This class effectively "re-exports"
 * the functionality from {@link WatchChangeAggregatorTestingHooks} in a class with {@code public}
 * visibility so that tests written in other packages can access its functionality.
 */
public final class WatchChangeAggregatorTestingHooksAccessor {

  private WatchChangeAggregatorTestingHooksAccessor() {}

  /** @see WatchChangeAggregatorTestingHooks#addExistenceFilterMismatchListener */
  public static ListenerRegistration addExistenceFilterMismatchListener(
      @NonNull ExistenceFilterMismatchListener listener) {
    checkNotNull(listener, "a null listener is not allowed");
    return WatchChangeAggregatorTestingHooks.addExistenceFilterMismatchListener(
        new ExistenceFilterMismatchListenerWrapper(listener));
  }

  /** @see WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchListener */
  public interface ExistenceFilterMismatchListener {
    @AnyThread
    void onExistenceFilterMismatch(ExistenceFilterMismatchInfo info);
  }

  /** @see WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo */
  public interface ExistenceFilterMismatchInfo {
    int localCacheCount();

    int existenceFilterCount();
  }

  private static final class ExistenceFilterMismatchInfoImpl
      implements ExistenceFilterMismatchInfo {

    private final WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo info;

    ExistenceFilterMismatchInfoImpl(
        @NonNull WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo info) {
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
  }

  private static final class ExistenceFilterMismatchListenerWrapper
      implements WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchListener {

    private final ExistenceFilterMismatchListener wrappedListener;

    ExistenceFilterMismatchListenerWrapper(
        @NonNull ExistenceFilterMismatchListener listenerToWrap) {
      this.wrappedListener = listenerToWrap;
    }

    @Override
    public void onExistenceFilterMismatch(
        WatchChangeAggregatorTestingHooks.ExistenceFilterMismatchInfo info) {
      this.wrappedListener.onExistenceFilterMismatch(new ExistenceFilterMismatchInfoImpl(info));
    }
  }

  public static final class ExistenceFilterMismatchAccumulator {

    private ExistenceFilterMismatchListenerImpl listener;
    private ListenerRegistration listenerRegistration = null;

    /** Registers the accumulator to begin listening for existence filter mismatches. */
    public synchronized void register() {
      if (listener != null) {
        throw new IllegalStateException("already registered");
      }
      listener = new ExistenceFilterMismatchListenerImpl();
      listenerRegistration =
          WatchChangeAggregatorTestingHooksAccessor.addExistenceFilterMismatchListener(listener);
    }

    /** Unregisters the accumulator from listening for existence filter mismatches. */
    public synchronized void unregister() {
      if (listener == null) {
        return;
      }
      listenerRegistration.remove();
      listenerRegistration = null;
      listener = null;
    }

    @Nullable
    public WatchChangeAggregatorTestingHooksAccessor.ExistenceFilterMismatchInfo
        waitForExistenceFilterMismatch(long timeoutMillis) throws InterruptedException {
      ExistenceFilterMismatchListenerImpl capturedListener;
      synchronized (this) {
        capturedListener = listener;
      }
      if (capturedListener == null) {
        throw new IllegalStateException(
            "must be registered before waiting for an existence filter mismatch");
      }
      return capturedListener.waitForExistenceFilterMismatch(timeoutMillis);
    }

    private static final class ExistenceFilterMismatchListenerImpl
        implements WatchChangeAggregatorTestingHooksAccessor.ExistenceFilterMismatchListener {

      private final ArrayList<ExistenceFilterMismatchInfo> existenceFilterMismatches =
          new ArrayList<>();

      @Override
      public void onExistenceFilterMismatch(
          WatchChangeAggregatorTestingHooksAccessor.ExistenceFilterMismatchInfo info) {
        synchronized (existenceFilterMismatches) {
          existenceFilterMismatches.add(info);
          existenceFilterMismatches.notifyAll();
        }
      }

      @Nullable
      WatchChangeAggregatorTestingHooksAccessor.ExistenceFilterMismatchInfo
          waitForExistenceFilterMismatch(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis <= 0) {
          throw new IllegalArgumentException("invalid timeout: " + timeoutMillis);
        }
        synchronized (existenceFilterMismatches) {
          long endTimeMillis = SystemClock.uptimeMillis() + timeoutMillis;
          while (true) {
            if (existenceFilterMismatches.size() > 0) {
              return existenceFilterMismatches.remove(0);
            }
            long currentWaitMillis = endTimeMillis - SystemClock.uptimeMillis();
            if (currentWaitMillis <= 0) {
              return null;
            }
            existenceFilterMismatches.wait(currentWaitMillis);
          }
        }
      }
    }
  }
}
