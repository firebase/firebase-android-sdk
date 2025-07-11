package com.google.firebase.appcheck.recaptchaenterprise.internal;

import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;

import java.util.concurrent.Executor;

/**
 * This class encapsulates a {@link com.google.firebase.annotations.concurrent.Lightweight}
 * executor and a {@link com.google.firebase.annotations.concurrent.Blocking} executor,
 * making them available for various asynchronous operations related to reCAPTCHA Enterprise
 * App Check.
 **/
public class FirebaseExecutors {
  private final Executor liteExecutor;
  private final Executor blockingExecutor;

  public FirebaseExecutors(@Lightweight Executor liteExecutor, @Blocking Executor blockingExecutor) {
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
  }

  public Executor getLiteExecutor() {
    return liteExecutor;
  }

  public Executor getBlockingExecutor() {
    return blockingExecutor;
  }
}
