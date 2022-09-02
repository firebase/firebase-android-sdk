// Copyright 2022 Google LLC
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
package com.google.firebase.perf.util;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnDrawListener;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Unit tests for {@link FirstDrawDoneListener}. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class FirstDrawDoneListenerTest {
  private View testView;

  @Before
  public void setUp() {
    testView = new View(ApplicationProvider.getApplicationContext());
  }

  @Test
  @Config(sdk = 25)
  public void registerForNextDraw_delaysAddingListenerForAPIsBelow26()
      throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
    ArrayList<OnDrawListener> mOnDrawListeners =
        initViewTreeObserverWithListener(testView.getViewTreeObserver());
    assertThat(mOnDrawListeners.size()).isEqualTo(0);

    // OnDrawListener is not registered, it is delayed for later
    FirstDrawDoneListener.registerForNextDraw(testView, () -> {});
    assertThat(mOnDrawListeners.size()).isEqualTo(0);

    // Register listener after the view is attached to a window
    List<View.OnAttachStateChangeListener> attachListeners = dispatchAttachedToWindow(testView);
    assertThat(mOnDrawListeners.size()).isEqualTo(1);
    assertThat(mOnDrawListeners.get(0)).isInstanceOf(FirstDrawDoneListener.class);
    assertThat(attachListeners).isEmpty();
  }

  @Test
  @Config(sdk = 26)
  public void registerForNextDraw_directlyAddsListenerForApi26AndAbove()
      throws NoSuchFieldException, IllegalAccessException {
    ArrayList<OnDrawListener> mOnDrawListeners =
        initViewTreeObserverWithListener(testView.getViewTreeObserver());
    assertThat(mOnDrawListeners.size()).isEqualTo(0);

    // Immediately register an OnDrawListener to ViewTreeObserver
    FirstDrawDoneListener.registerForNextDraw(testView, () -> {});
    assertThat(mOnDrawListeners.size()).isEqualTo(1);
    assertThat(mOnDrawListeners.get(0)).isInstanceOf(FirstDrawDoneListener.class);
  }

  @Test
  @Config(sdk = 26)
  public void onDraw_postsCallbackToFrontOfQueue() {
    Handler handler = new Handler(Looper.getMainLooper());
    Runnable drawDoneCallback = mock(Runnable.class);
    Runnable otherCallback = mock(Runnable.class);
    InOrder inOrder = inOrder(drawDoneCallback, otherCallback);

    FirstDrawDoneListener.registerForNextDraw(testView, drawDoneCallback);
    handler.post(otherCallback); // 3rd in queue
    handler.postAtFrontOfQueue(otherCallback); // 2nd in queue
    testView.getViewTreeObserver().dispatchOnDraw(); // 1st in queue
    verify(drawDoneCallback, never()).run();
    verify(otherCallback, never()).run();

    // Execute all posted tasks
    shadowOf(Looper.getMainLooper()).idle();
    inOrder.verify(drawDoneCallback, times(1)).run();
    inOrder.verify(otherCallback, times(2)).run();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  @Config(sdk = 26)
  public void onDraw_unregistersItself_inLayoutChangeListener()
      throws NoSuchFieldException, IllegalAccessException {
    ArrayList<OnDrawListener> mOnDrawListeners =
        initViewTreeObserverWithListener(testView.getViewTreeObserver());
    FirstDrawDoneListener.registerForNextDraw(testView, () -> {});
    assertThat(mOnDrawListeners.size()).isEqualTo(1);

    // Does not remove OnDrawListener before onDraw, even if OnGlobalLayout is triggered
    testView.getViewTreeObserver().dispatchOnGlobalLayout();
    assertThat(mOnDrawListeners.size()).isEqualTo(1);

    // Removes OnDrawListener in the next OnGlobalLayout after onDraw
    testView.getViewTreeObserver().dispatchOnDraw();
    testView.getViewTreeObserver().dispatchOnGlobalLayout();
    assertThat(mOnDrawListeners.size()).isEqualTo(0);
  }

  /**
   * Returns ViewTreeObserver.mOnDrawListeners field through reflection. Since reflections are
   * employed, prefer to be used in tests with fixed API level using @Config(sdk = X).
   *
   * @param vto ViewTreeObserver instance to initialize and return the mOnDrawListeners from
   */
  private static ArrayList<OnDrawListener> initViewTreeObserverWithListener(ViewTreeObserver vto)
      throws NoSuchFieldException, IllegalAccessException {
    // Adding a listener forces ViewTreeObserver.mOnDrawListeners to be initialized and non-null.
    OnDrawListener placeHolder = () -> {};
    vto.addOnDrawListener(placeHolder);
    vto.removeOnDrawListener(placeHolder);

    // Obtain mOnDrawListeners field through reflection
    Field mOnDrawListeners =
        android.view.ViewTreeObserver.class.getDeclaredField("mOnDrawListeners");
    mOnDrawListeners.setAccessible(true);
    ArrayList<OnDrawListener> listeners = (ArrayList<OnDrawListener>) mOnDrawListeners.get(vto);
    assertThat(listeners).isNotNull();
    assertThat(listeners.size()).isEqualTo(0);
    return listeners;
  }

  /**
   * Simulates {@link View}'s dispatchAttachedToWindow() on API 25 using reflection.
   *
   * <p>This only simulates the part where dispatchAttachedToWindow() notifies the list of {@link
   * View.OnAttachStateChangeListener}.
   *
   * @param view the view in which we are simulating dispatchAttachedToWindow().
   * @return list of {@link View.OnAttachStateChangeListener} from the input {@link View}
   */
  private static List<View.OnAttachStateChangeListener> dispatchAttachedToWindow(View view)
      throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    assert Build.VERSION.SDK_INT == 25;
    Class<?> listenerInfo = Class.forName("android.view.View$ListenerInfo");
    Field mListenerInfo = View.class.getDeclaredField("mListenerInfo");
    mListenerInfo.setAccessible(true);
    Object li = mListenerInfo.get(view);
    assertThat(li).isNotNull();
    Field mOnAttachStateChangeListeners =
        listenerInfo.getDeclaredField("mOnAttachStateChangeListeners");
    mOnAttachStateChangeListeners.setAccessible(true);
    CopyOnWriteArrayList<View.OnAttachStateChangeListener> listeners =
        (CopyOnWriteArrayList<View.OnAttachStateChangeListener>)
            mOnAttachStateChangeListeners.get(li);
    assertThat(listeners).isNotNull();
    assertThat(listeners).isNotEmpty();
    for (View.OnAttachStateChangeListener listener : listeners) {
      listener.onViewAttachedToWindow(view);
    }
    return listeners;
  }
}
