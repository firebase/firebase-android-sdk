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

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;

/**
 * Provides a mechanism for tests to listen for existence filter mismatches in the Watch "listen"
 * stream.
 */
public final class ExistenceFilterMismatchListener {

  private TestingHooksExistenceFilterMismatchListenerImpl listener;
  private ListenerRegistration listenerRegistration;

  /**
   * Starts listening for existence filter mismatches.
   *
   * @throws IllegalStateException if this object is already started.
   * @see #stopListening
   */
  public synchronized void startListening() {
    if (listener != null) {
      throw new IllegalStateException("already registered");
    }
    listener = new TestingHooksExistenceFilterMismatchListenerImpl();
    listenerRegistration = TestingHooks.getInstance().addExistenceFilterMismatchListener(listener);
  }

  /**
   * Stops listening for existence filter mismatches.
   *
   * <p>If listening has not been started then this method does nothing.
   *
   * @see #startListening
   */
  public synchronized void stopListening() {
    if (listenerRegistration != null) {
      listenerRegistration.remove();
    }
    listenerRegistration = null;
    listener = null;
  }

  /**
   * Returns the oldest existence filter mismatch observed, waiting if none has yet been observed.
   *
   * <p>The oldest existence filter mismatch observed since the most recent successful invocation of
   * {@link #startListening} will be returned. A subsequent invocation of this method will return
   * the second-oldest existence filter mismatch observed, and so on. An invocation of {@link
   * #stopListening} followed by another invocation of {@link #startListening} will discard any
   * existence filter mismatches that occurred while previously started and will start observing
   * afresh.
   *
   * @param timeoutMillis the maximum amount of time, in milliseconds, to wait for an existence
   *     filter mismatch to occur.
   * @return information about the existence filter mismatch that occurred.
   * @throws InterruptedException if waiting is interrupted.
   * @throws IllegalStateException if this object has not been started by {@link #startListening}.
   * @throws IllegalArgumentException if the given timeout is less than or equal to zero.
   */
  @Nullable
  public ExistenceFilterMismatchInfo getOrWaitForExistenceFilterMismatch(long timeoutMillis)
      throws InterruptedException {
    if (timeoutMillis <= 0) {
      throw new IllegalArgumentException("invalid timeout: " + timeoutMillis);
    }

    TestingHooksExistenceFilterMismatchListenerImpl registeredListener;
    synchronized (this) {
      registeredListener = listener;
    }

    if (registeredListener == null) {
      throw new IllegalStateException(
          "must be registered before waiting for an existence filter mismatch");
    }

    return registeredListener.getOrWaitForExistenceFilterMismatch(timeoutMillis);
  }

  private static final class TestingHooksExistenceFilterMismatchListenerImpl
      implements TestingHooks.ExistenceFilterMismatchListener {

    private final ArrayList<ExistenceFilterMismatchInfo> existenceFilterMismatches =
        new ArrayList<>();

    @Override
    public synchronized void onExistenceFilterMismatch(
        @NonNull TestingHooks.ExistenceFilterMismatchInfo info) {
      existenceFilterMismatches.add(new ExistenceFilterMismatchInfo(info));
      notifyAll();
    }

    @Nullable
    synchronized ExistenceFilterMismatchInfo getOrWaitForExistenceFilterMismatch(long timeoutMillis)
        throws InterruptedException {
      if (timeoutMillis <= 0) {
        throw new IllegalArgumentException("invalid timeout: " + timeoutMillis);
      }

      long endTimeMillis = SystemClock.uptimeMillis() + timeoutMillis;
      while (true) {
        if (existenceFilterMismatches.size() > 0) {
          return existenceFilterMismatches.remove(0);
        }
        long currentWaitMillis = endTimeMillis - SystemClock.uptimeMillis();
        if (currentWaitMillis <= 0) {
          return null;
        }

        wait(currentWaitMillis);
      }
    }
  }

  /**
   * @see TestingHooks.ExistenceFilterMismatchInfo
   */
  public static final class ExistenceFilterMismatchInfo {

    private final TestingHooks.ExistenceFilterMismatchInfo info;

    ExistenceFilterMismatchInfo(@NonNull TestingHooks.ExistenceFilterMismatchInfo info) {
      this.info = info;
    }

    public int localCacheCount() {
      return info.localCacheCount();
    }

    public int existenceFilterCount() {
      return info.existenceFilterCount();
    }
  }
}
