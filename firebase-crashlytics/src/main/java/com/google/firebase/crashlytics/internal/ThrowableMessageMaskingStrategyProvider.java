package com.google.firebase.crashlytics.internal;

import com.google.firebase.crashlytics.masking.NoMaskStrategy;
import com.google.firebase.crashlytics.masking.ThrowableMessageMaskingStrategy;

public class ThrowableMessageMaskingStrategyProvider {
  private ThrowableMessageMaskingStrategy throwableMessageMaskingStrategy;

  public ThrowableMessageMaskingStrategyProvider() {
    this.throwableMessageMaskingStrategy = new NoMaskStrategy();
  }

  public ThrowableMessageMaskingStrategy getMaskingStrategy() {
    return throwableMessageMaskingStrategy;
  }

  public void setMaskingStrategy(ThrowableMessageMaskingStrategy maskingStrategy) {
    this.throwableMessageMaskingStrategy = maskingStrategy;
  }
}
