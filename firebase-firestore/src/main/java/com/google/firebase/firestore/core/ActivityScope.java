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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.app.Activity;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;

/**
 * Scopes the lifetime of a ListenerRegistration to an Activity.
 *
 * <p>Regarding activity-scoped listeners, Android provides lifecycle callbacks (eg onStop()) that
 * custom `Activity`s can implement via subclassing. But we can't take advantage of that, since we
 * need to be usable with a generic Activity. So instead, we create a custom Fragment, and add that
 * Fragment to the given Activity. When the Activity stops, it will automatically stop the attached
 * Fragments too.
 *
 * <p>One difficulty with this approach is that how you get a Fragment and attach it to an Activity
 * differs based on the type of Activity. If the Activity is actually a FragmentActivity, then you
 * must use the android.support.v4.app.FragmentManager to do so. Otherwise, you need to use the
 * deprecated android.app.FragmentManager.
 *
 * <p>Possible improvements:
 *
 * <ol>
 *   <li>Allow other lifecycle callbacks other than just 'onStop'.
 *   <li>Use LifecycleOwner (which FragmentActivity implements, but Activity does not) to register
 *       for lifecycle callbacks instead of creating/attaching a Fragment.
 * </ol>
 */
public class ActivityScope {

  private static class CallbackList {
    void run() {
      for (Runnable callback : callbacks) {
        if (callback != null) {
          callback.run();
        }
      }
    }

    synchronized void add(Runnable callback) {
      callbacks.add(callback);
    }

    private final List<Runnable> callbacks = new ArrayList<>();
  }

  public static class StopListenerSupportFragment extends Fragment {
    CallbackList callbacks = new CallbackList();

    @Override
    @SuppressWarnings("SynchronizeOnNonFinalField")
    public void onStop() {
      super.onStop();

      CallbackList callbacksCopy;
      // Synchronize to ensure we don't drop callbacks if the user registers another onStop callback
      // at the same time as the callbacks are executing. (See the synchronized
      // CallbackList#add(Runnable) method.) Once the callbacks instance has been reassigned, we can
      // allow the user to add more callbacks again (which would only be invoked if the Fragment was
      // restarted and stopped).
      synchronized (callbacks) {
        callbacksCopy = callbacks;
        callbacks = new CallbackList();
      }
      callbacksCopy.run();
    }
  }

  @SuppressWarnings("deprecation")
  public static class StopListenerFragment extends android.app.Fragment {
    CallbackList callbacks = new CallbackList();

    @Override
    @SuppressWarnings("SynchronizeOnNonFinalField")
    public void onStop() {
      super.onStop();

      CallbackList callbacksCopy;
      // See sync comments in the StopListenerSupportFragment implementation.
      synchronized (callbacks) {
        callbacksCopy = callbacks;
        callbacks = new CallbackList();
      }
      callbacksCopy.run();
    }
  }

  @Nullable
  private static <T> T castFragment(Class<T> fragmentClass, @Nullable Object fragment, String tag) {
    try {
      if (fragment == null) {
        return null;
      }
      return fragmentClass.cast(fragment);
    } catch (ClassCastException e) {
      throw new IllegalStateException(
          "Fragment with tag '"
              + tag
              + "' is a "
              + fragment.getClass().getName()
              + " but should be a "
              + fragmentClass.getName());
    }
  }

  private static final String SUPPORT_FRAGMENT_TAG = "FirestoreOnStopObserverSupportFragment";
  private static final String FRAGMENT_TAG = "FirestoreOnStopObserverFragment";

  /**
   * Implementation for non-FragmentActivity Activities. Unfortunately, all Fragment related
   * classes/methods with nonFragmentActivityActivities are deprecated, implying that almost
   * everything in this function is deprecated.
   */
  @SuppressWarnings("deprecation")
  private static void onActivityStopCallOnce(Activity activity, Runnable callback) {
    hardAssert(
        !(activity instanceof FragmentActivity),
        "onActivityStopCallOnce must be called with a *non*-FragmentActivity Activity.");

    activity.runOnUiThread(
        () -> {
          StopListenerFragment fragment =
              castFragment(
                  StopListenerFragment.class,
                  activity.getFragmentManager().findFragmentByTag(FRAGMENT_TAG),
                  FRAGMENT_TAG);

          if (fragment == null || fragment.isRemoving()) {
            fragment = new StopListenerFragment();
            activity
                .getFragmentManager()
                .beginTransaction()
                .add(fragment, FRAGMENT_TAG)
                .commitAllowingStateLoss();

            activity.getFragmentManager().executePendingTransactions();
          }

          fragment.callbacks.add(callback);
        });
  }

  private static void onFragmentActivityStopCallOnce(FragmentActivity activity, Runnable callback) {
    activity.runOnUiThread(
        () -> {
          StopListenerSupportFragment fragment =
              castFragment(
                  StopListenerSupportFragment.class,
                  activity.getSupportFragmentManager().findFragmentByTag(SUPPORT_FRAGMENT_TAG),
                  SUPPORT_FRAGMENT_TAG);

          if (fragment == null || fragment.isRemoving()) {
            fragment = new StopListenerSupportFragment();
            activity
                .getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, SUPPORT_FRAGMENT_TAG)
                .commitAllowingStateLoss();

            activity.getSupportFragmentManager().executePendingTransactions();
          }

          fragment.callbacks.add(callback);
        });
  }

  /** Binds the given registration to the lifetime of the activity. */
  public static ListenerRegistration bind(
      @Nullable Activity activity, ListenerRegistration registration) {
    if (activity != null) {
      if (activity instanceof FragmentActivity) {
        onFragmentActivityStopCallOnce((FragmentActivity) activity, registration::remove);
      } else {
        onActivityStopCallOnce(activity, registration::remove);
      }
    }
    return registration;
  }
}
