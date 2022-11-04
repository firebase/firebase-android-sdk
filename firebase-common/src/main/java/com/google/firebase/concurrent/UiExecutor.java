package com.google.firebase.concurrent;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;

/** @hide */
public enum UiExecutor implements Executor {
  INSTANCE;

  private static final Handler HANDLER = new Handler(Looper.getMainLooper());

  @Override
  public void execute(Runnable command) {
    HANDLER.post(command);
  }
}
