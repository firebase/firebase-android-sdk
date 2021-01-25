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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.storage.internal.ActivityLifecycleListener;
import com.google.firebase.storage.internal.SmartHandler;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/** Helper class to manage listener subscriptions on executor/activity. */
/*package*/

class TaskListenerImpl<ListenerTypeT, ResultT extends StorageTask.ProvideError> {
  private final Queue<ListenerTypeT> listenerQueue = new ConcurrentLinkedQueue<>();
  private final HashMap<ListenerTypeT, SmartHandler> handlerMap = new HashMap<>();
  private StorageTask<ResultT> task;
  private int targetStates;
  private OnRaise<ListenerTypeT, ResultT> onRaise;

  public TaskListenerImpl(
      @NonNull StorageTask<ResultT> task,
      int targetInternalStates,
      @NonNull OnRaise<ListenerTypeT, ResultT> onRaise) {
    this.task = task;
    this.targetStates = targetInternalStates;
    this.onRaise = onRaise;
  }

  /* For Test Only*/
  public int getListenerCount() {
    return Math.max(listenerQueue.size(), handlerMap.size());
  }

  public void addListener(
      @Nullable Activity activity,
      @Nullable Executor executor,
      @NonNull final ListenerTypeT listener) {
    Preconditions.checkNotNull(listener);

    boolean shouldFire = false;
    SmartHandler handler;
    synchronized (task.getSyncObject()) {
      if ((task.getInternalState() & targetStates) != 0) {
        shouldFire = true;
      }
      listenerQueue.add(listener);
      handler = new SmartHandler(executor);
      handlerMap.put(listener, handler);
      if (activity != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          Preconditions.checkArgument(!activity.isDestroyed(), "Activity is already destroyed!");
        }
        ActivityLifecycleListener.getInstance()
            .runOnActivityStopped(activity, listener, () -> removeListener(listener));
      }
    }

    if (shouldFire) {
      final ResultT snappedState = task.snapState();
      handler.callBack(() -> onRaise.raise(listener, snappedState));
    }
  }

  public void onInternalStateChanged() {
    if ((task.getInternalState() & targetStates) != 0) {
      final ResultT snappedState = task.snapState();
      for (ListenerTypeT c : listenerQueue) {
        final ListenerTypeT finalCallback = c;
        SmartHandler handler = handlerMap.get(c);
        if (handler != null) {
          handler.callBack(() -> onRaise.raise(finalCallback, snappedState));
        }
      }
    }
  }

  /** Removes a listener. */
  public void removeListener(@NonNull ListenerTypeT listener) {
    Preconditions.checkNotNull(listener);

    synchronized (task.getSyncObject()) {
      handlerMap.remove(listener);
      listenerQueue.remove(listener);
      ActivityLifecycleListener.getInstance().removeCookie(listener);
    }
  }

  interface OnRaise<ListenerTypeT, ResultT> {
    void raise(@NonNull ListenerTypeT listener, @NonNull ResultT snappedState);
  }
}
