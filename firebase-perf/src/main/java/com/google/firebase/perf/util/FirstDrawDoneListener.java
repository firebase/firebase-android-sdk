package com.google.firebase.perf.util;

import android.os.Build.VERSION_CODES;
import android.view.View;
import android.view.ViewTreeObserver;


import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.concurrent.atomic.AtomicReference;

@RequiresApi(VERSION_CODES.JELLY_BEAN)
public class FirstDrawDoneListener implements ViewTreeObserver.OnDrawListener {
  private final AtomicReference<@Nullable View> viewReference;

  private FirstOnDrawListener(View view) {
    this.viewReference = new AtomicReference<>(view);
  }

  @Override
  public void onDraw() {
    View view = viewReference.getAndSet(null);
    // OnDrawListeners cannot be removed within onDraw, so we remove it with a
    // GlobalLayoutListener
    view.getViewTreeObserver().addOnGlobalLayoutListener(new LayoutChangeListener(view, this));
    ThreadUtil.getMainThreadHandler()
            .postAtFrontOfQueue(
                    () -> StartupFlowCollectorImpl.this.collectAndLogActivityFirstDrawDone(successCode));
  }

  @RequiresApi(VERSION_CODES.JELLY_BEAN)
  private static final class LayoutChangeListener implements ViewTreeObserver.OnGlobalLayoutListener {
    private final View view;
    private final FirstDrawDoneListener listener;

    private LayoutChangeListener(View view, FirstDrawDoneListener firstDrawDoneListener) {
      this.view = view;
      this.listener = firstDrawDoneListener;
    }

    @Override
    public void onGlobalLayout() {
      view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
      view.getViewTreeObserver().removeOnDrawListener(listener);
    }
  }
}
