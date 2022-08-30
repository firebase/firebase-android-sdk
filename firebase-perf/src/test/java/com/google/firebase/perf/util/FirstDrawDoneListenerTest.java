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

/** Unit tests for {@link Timer}. */
@RunWith(RobolectricTestRunner.class)
public class FirstDrawDoneListenerTest {
  @Mock Handler handler;
  @Captor ArgumentCaptor<Runnable> callbackCaptor;

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
    testView.getViewTreeObserver().addOnDrawListener(() -> {});
    Field mOnDrawListeners =
        android.view.ViewTreeObserver.class.getDeclaredField("mOnDrawListeners");
    mOnDrawListeners.setAccessible(true);
    ArrayList<ViewTreeObserver.OnDrawListener> listOfListeners =
        (ArrayList<ViewTreeObserver.OnDrawListener>)
            mOnDrawListeners.get(testView.getViewTreeObserver());
    assertThat(listOfListeners).isNotNull();
    assertThat(listOfListeners.size()).isEqualTo(1);

    FirstDrawDoneListener.registerForNextDraw(testView, () -> {});
    assertThat(listOfListeners.size()).isEqualTo(1);
  }

  @Test
  @Config(sdk = 26)
  public void registerForNextDraw_directlyAddsListenerForApi26AndAbove()
      throws NoSuchFieldException, IllegalAccessException {
    testView.getViewTreeObserver().addOnDrawListener(() -> {});
    Field mOnDrawListeners =
        android.view.ViewTreeObserver.class.getDeclaredField("mOnDrawListeners");
    mOnDrawListeners.setAccessible(true);
    ArrayList<ViewTreeObserver.OnDrawListener> listOfListeners =
        (ArrayList<ViewTreeObserver.OnDrawListener>)
            mOnDrawListeners.get(testView.getViewTreeObserver());
    assertThat(listOfListeners).isNotNull();
    assertThat(listOfListeners.size()).isEqualTo(1);

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

    testView.getViewTreeObserver().dispatchOnGlobalLayout();
    assertThat(listOfListeners.size()).isEqualTo(1);

    testView.getViewTreeObserver().dispatchOnDraw();
    testView.getViewTreeObserver().dispatchOnGlobalLayout();
    assertThat(listOfListeners.size()).isEqualTo(0);
  }
}
