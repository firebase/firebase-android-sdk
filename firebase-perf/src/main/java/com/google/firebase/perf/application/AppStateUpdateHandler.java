// Copyright 2020 Google LLC
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

package com.google.firebase.perf.internal;

import androidx.annotation.NonNull;
import com.google.firebase.perf.internal.AppStateMonitor.AppStateCallback;
import com.google.firebase.perf.v1.ApplicationProcessState;
import java.lang.ref.WeakReference;

/**
 * A client that can be registered with AppStateMonitor class to receive foreground/background app
 * state update.
 *
 * @hide
 */
/** @hide */
public abstract class AppStateUpdateHandler implements AppStateCallback {

  private AppStateMonitor mAppStateMonitor;
  private ApplicationProcessState mState =
      ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN;
  private boolean mIsRegisteredForAppState = false;
  // The weak reference to register/unregister with AppStateMonitor.
  // It must be a weak reference because unregisterForAppState() is called typically from
  // Trace.stop() and user may forget to call Trace.stop(), if it was a strong reference,
  // the registration in AppStateMonitor will prevent Trace to be deallocated.
  private WeakReference<AppStateCallback> mWeakRef;

  /** @hide */
  /** @hide */
  protected AppStateUpdateHandler() {
    this(AppStateMonitor.getInstance());
  }

  /** @hide */
  /** @hide */
  protected AppStateUpdateHandler(@NonNull AppStateMonitor appStateMonitor) {
    mAppStateMonitor = appStateMonitor;
    mWeakRef = new WeakReference<AppStateCallback>(this);
  }

  /** @hide */
  /** @hide */
  protected void registerForAppState() {
    if (mIsRegisteredForAppState) {
      return;
    }
    mState = mAppStateMonitor.getAppState();
    mAppStateMonitor.registerForAppState(mWeakRef);
    mIsRegisteredForAppState = true;
  }

  /** @hide */
  /** @hide */
  protected void unregisterForAppState() {
    if (!mIsRegisteredForAppState) {
      return;
    }
    mAppStateMonitor.unregisterForAppState(mWeakRef);
    mIsRegisteredForAppState = false;
  }

  /** @hide */
  /** @hide */
  protected void incrementTsnsCount(int count) {
    mAppStateMonitor.incrementTsnsCount(count);
  }

  /** @hide */
  /** @hide */
  @Override
  public void onUpdateAppState(ApplicationProcessState newState) {
    // For Trace and NetworkRequestMetricBuilder, the app state means all app states the app
    // has been through during the duration of the trace.
    if (mState == ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN) {
      mState = newState;
    } else if (mState != newState
        && newState != ApplicationProcessState.APPLICATION_PROCESS_STATE_UNKNOWN) {
      // newState is not unknown and they're not equal, which means one is foreground and the other
      // is background.
      mState = ApplicationProcessState.FOREGROUND_BACKGROUND;
    }
  }

  /** @hide */
  /** @hide */
  public ApplicationProcessState getAppState() {
    return mState;
  }
}
