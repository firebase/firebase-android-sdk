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
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.os.Handler;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for {@link FirstDrawDoneListener}. */
@RunWith(RobolectricTestRunner.class)
public class FirstDrawDoneListenerTest {
  @Mock private Handler handler;
  @Captor private ArgumentCaptor<Runnable> callbackCaptor;

  private View testView;

  @Before
  public void setUp() {
    initMocks(this);
    testView = new View(ApplicationProvider.getApplicationContext());
  }

  @Test
  @Config(sdk = 25)
  public void registerForNextDraw_delaysAddingListenerForAPIsBelow26()
      throws NoSuchFieldException, IllegalAccessException {
    // Add a listener first so ViewTreeObserver.mOnDrawListeners is initialized and non-null
    testView.getViewTreeObserver().addOnDrawListener(() -> {});
    Field mOnDrawListeners =
        android.view.ViewTreeObserver.class.getDeclaredField("mOnDrawListeners");
    mOnDrawListeners.setAccessible(true);
    ArrayList<ViewTreeObserver.OnDrawListener> listOfListeners =
        (ArrayList<ViewTreeObserver.OnDrawListener>)
            mOnDrawListeners.get(testView.getViewTreeObserver());
    assertThat(listOfListeners).isNotNull();
    assertThat(listOfListeners.size()).isEqualTo(1);

    // OnDrawListener is not registered, it is delayed for later
    FirstDrawDoneListener.registerForNextDraw(testView, () -> {});
    assertThat(listOfListeners.size()).isEqualTo(1);
  }

  @Test
  @Config(sdk = 26)
  public void registerForNextDraw_directlyAddsListenerForApi26AndAbove()
      throws NoSuchFieldException, IllegalAccessException {
    // Add a listener first so ViewTreeObserver.mOnDrawListeners is initialized and non-null
    testView.getViewTreeObserver().addOnDrawListener(() -> {});
    Field mOnDrawListeners =
        android.view.ViewTreeObserver.class.getDeclaredField("mOnDrawListeners");
    mOnDrawListeners.setAccessible(true);
    ArrayList<ViewTreeObserver.OnDrawListener> listOfListeners =
        (ArrayList<ViewTreeObserver.OnDrawListener>)
            mOnDrawListeners.get(testView.getViewTreeObserver());
    assertThat(listOfListeners).isNotNull();
    assertThat(listOfListeners.size()).isEqualTo(1);

    // Immediately register an OnDrawListener to ViewTreeObserver
    FirstDrawDoneListener.registerForNextDraw(testView, () -> {});
    assertThat(listOfListeners.size()).isEqualTo(2);
  }

  @Test
  public void onDraw_postsCallbackToFrontOfQueue() {
    Runnable callback = () -> {};
    testView
        .getViewTreeObserver()
        .addOnDrawListener(new FirstDrawDoneListener(testView, callback, handler));
    testView.getViewTreeObserver().dispatchOnDraw();
    verify(handler).postAtFrontOfQueue(callbackCaptor.capture());
    assertThat(callbackCaptor.getValue()).isSameInstanceAs(callback);
  }

  @Test
  @Config(sdk = 26)
  public void onDraw_unregistersItself_inLayoutChangeListener()
      throws NoSuchFieldException, IllegalAccessException {
    FirstDrawDoneListener.registerForNextDraw(testView, () -> {});
    Field mOnDrawListeners =
        android.view.ViewTreeObserver.class.getDeclaredField("mOnDrawListeners");
    mOnDrawListeners.setAccessible(true);
    ArrayList<ViewTreeObserver.OnDrawListener> listOfListeners =
        (ArrayList<ViewTreeObserver.OnDrawListener>)
            mOnDrawListeners.get(testView.getViewTreeObserver());
    assertThat(listOfListeners).isNotNull();
    assertThat(listOfListeners.size()).isEqualTo(1);

    // Does not remove itself before onDraw, even if OnGlobalLayout is triggered
    testView.getViewTreeObserver().dispatchOnGlobalLayout();
    assertThat(listOfListeners.size()).isEqualTo(1);

    // Removes itself after onDraw, in the next OnGlobalLayout
    testView.getViewTreeObserver().dispatchOnDraw();
    testView.getViewTreeObserver().dispatchOnGlobalLayout();
    assertThat(listOfListeners.size()).isEqualTo(0);
  }
}
