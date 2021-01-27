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

package com.google.firebase.storage.internal;

import android.app.Activity;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.google.android.gms.common.api.internal.LifecycleActivity;
import com.google.android.gms.common.api.internal.LifecycleCallback;
import com.google.android.gms.common.api.internal.LifecycleFragment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central class for hooking activity stops. Register a Runnable to be invoked (once) when a
 * particular Activity has Stopped. Once stopped, the entry is automatically removed.
 *
 * @hide
 */
@SuppressWarnings("JavaDoc")
public class ActivityLifecycleListener {

  private static final ActivityLifecycleListener instance = new ActivityLifecycleListener();
  private final Map<Object, LifecycleEntry> cookieMap = new HashMap<>();
  private final Object sync = new Object();

  private ActivityLifecycleListener() {}

  @NonNull
  public static ActivityLifecycleListener getInstance() {
    return instance;
  }

  public void runOnActivityStopped(
      @NonNull Activity activityToListenOn, @NonNull Object cookie, @NonNull Runnable runnable) {
    synchronized (sync) {
      LifecycleEntry entry = new LifecycleEntry(activityToListenOn, runnable, cookie);
      OnStopCallback.getInstance(activityToListenOn).addEntry(entry);
      cookieMap.put(cookie, entry);
    }
  }

  public void removeCookie(@NonNull Object cookie) {
    synchronized (sync) {
      LifecycleEntry entry = cookieMap.get(cookie);
      if (entry != null) {
        OnStopCallback.getInstance(entry.getActivity()).removeEntry(entry);
      }
    }
  }

  private static class LifecycleEntry {
    @NonNull private final Activity activity;
    @NonNull private final Runnable runnable;
    @NonNull private final Object cookie;

    public LifecycleEntry(
        @NonNull Activity activity, @NonNull Runnable runnable, @NonNull Object cookie) {

      this.activity = activity;
      this.runnable = runnable;
      this.cookie = cookie;
    }

    @Override
    public int hashCode() {
      return cookie.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof LifecycleEntry)) {
        return false;
      }
      LifecycleEntry other = (LifecycleEntry) obj;
      return other.cookie.equals(cookie)
          && other.runnable == runnable
          && other.activity == activity;
    }

    @NonNull
    public Activity getActivity() {
      return activity;
    }

    @NonNull
    public Runnable getRunnable() {
      return runnable;
    }

    @NonNull
    public Object getCookie() {
      return cookie;
    }
  }

  private static class OnStopCallback extends LifecycleCallback {
    private static final String TAG = "StorageOnStopCallback";
    private final List<LifecycleEntry> listeners = new ArrayList<>();

    private OnStopCallback(LifecycleFragment fragment) {
      super(fragment);
      mLifecycleFragment.addCallback(TAG, this);
    }

    public static OnStopCallback getInstance(Activity activity) {
      LifecycleFragment fragment = getFragment(new LifecycleActivity(activity));
      OnStopCallback callback = fragment.getCallbackOrNull(TAG, OnStopCallback.class);
      if (callback == null) {
        callback = new OnStopCallback(fragment);
      }
      return callback;
    }

    public void addEntry(LifecycleEntry entry) {
      synchronized (listeners) {
        listeners.add(entry);
      }
    }

    public void removeEntry(LifecycleEntry listener) {
      synchronized (listeners) {
        listeners.remove(listener);
      }
    }

    @Override
    @MainThread
    public void onStop() {
      ArrayList<LifecycleEntry> copy;
      synchronized (listeners) {
        copy = new ArrayList<>(listeners);
        listeners.clear();
      }
      for (LifecycleEntry entry : copy) {
        if (entry != null) {
          Log.d(TAG, "removing subscription from activity.");
          entry.getRunnable().run();
          // this should already be happening, but just in case (so we dont leak)
          ActivityLifecycleListener.getInstance().removeCookie(entry.getCookie());
        }
      }
    }
  }
}
