package com.google.firebase.crashlytics.masking;

import androidx.annotation.Nullable;

/** The strategy that does not mask exception message. */
public class NoMaskStrategy implements ThrowableMessageMaskingStrategy {
  @Nullable
  @Override
  public String getMaskedMessage(@Nullable String originalMessage) {
    return originalMessage;
  }
}
