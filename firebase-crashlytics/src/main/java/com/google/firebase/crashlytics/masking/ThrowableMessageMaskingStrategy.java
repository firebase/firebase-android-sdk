package com.google.firebase.crashlytics.masking;

import androidx.annotation.Nullable;

public interface ThrowableMessageMaskingStrategy {
  @Nullable
  String getMaskedMessage(@Nullable String originalMessage);
}
