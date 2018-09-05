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

package com.google.firebase.storage;

import android.app.Activity;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.annotations.PublicApi;
import com.google.firebase.storage.internal.ActivityLifecycleListener;
import com.google.firebase.storage.internal.SmartHandler;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/** Helper class to manage listener subscriptions on executor/activity. */
/*package*/
@PublicApi
class TaskListenerImpl<TListenerType, TResult extends StorageTask.ProvideError> {
  private final Queue<TListenerType> mListenerQueue = new ConcurrentLinkedQueue<>();
  private final HashMap<TListenerType, SmartHandler> mHandlerMap = new HashMap<>();
  private StorageTask<TResult> mTask;
  private int mTargetStates;
  private OnRaise<TListenerType, TResult> mOnRaise;

  @PublicApi
  public TaskListenerImpl(
      @NonNull StorageTask<TResult> task,
      int targetInternalStates,
      @NonNull OnRaise<TListenerType, TResult> onRaise) {
    mTask = task;
    mTargetStates = targetInternalStates;
    mOnRaise = onRaise;
  }

  /* For Test Only*/
  public int getListenerCount() {
    return Math.max(mListenerQueue.size(), mHandlerMap.size());
  }

  @PublicApi
  public void addListener(
      @Nullable Activity activity,
      @Nullable Executor executor,
      @NonNull final TListenerType listener) {
    Preconditions.checkNotNull(listener);

    boolean shouldFire = false;
    SmartHandler handler;
    synchronized (mTask.getSyncObject()) {
      if ((mTask.getInternalState() & mTargetStates) != 0) {
        shouldFire = true;
      }
      mListenerQueue.add(listener);
      handler = new SmartHandler(executor);
      mHandlerMap.put(listener, handler);
      if (activity != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          Preconditions.checkArgument(!activity.isDestroyed(), "Activity is already destroyed!");
        }
        ActivityLifecycleListener.getInstance()
            .runOnActivityStopped(activity, listener, () -> removeListener(listener));
      }
    }

    if (shouldFire) {
      final TResult snappedState = mTask.snapState();
      handler.callBack(() -> mOnRaise.raise(listener, snappedState));
    }
  }

  @PublicApi
  public void onInternalStateChanged() {
    if ((mTask.getInternalState() & mTargetStates) != 0) {
      final TResult snappedState = mTask.snapState();
      for (TListenerType c : mListenerQueue) {
        final TListenerType finalCallback = c;
        SmartHandler handler = mHandlerMap.get(c);
        if (handler != null) {
          handler.callBack(() -> mOnRaise.raise(finalCallback, snappedState));
        }
      }
    }
  }

  /** Removes a listener. */
  @PublicApi
  public void removeListener(@NonNull TListenerType listener) {
    Preconditions.checkNotNull(listener);

    synchronized (mTask.getSyncObject()) {
      mHandlerMap.remove(listener);
      mListenerQueue.remove(listener);
      ActivityLifecycleListener.getInstance().removeCookie(listener);
    }
  }

  interface OnRaise<TListenerType, TResult> {
    void raise(@NonNull TListenerType listener, @NonNull TResult snappedState);
  }
}
