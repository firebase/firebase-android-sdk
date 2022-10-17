package com.google.firebase.crashlytics.masking;

import androidx.annotation.Nullable;

public class NoMaskStrategy implements ThrowableMessageMaskingStrategy {
  @Nullable
  @Override
  public String getMaskedMessage(@Nullable String originalMessage) {
    return originalMessage;
  }
}
