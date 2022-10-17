package com.google.firebase.crashlytics.masking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The strategy for masking all a original exception message. This class replaces all characters
 * with place holder characters.
 */
public class AllMaskStrategy implements ThrowableMessageMaskingStrategy {
  @NonNull private final String placeHolder;

  public AllMaskStrategy() {
    placeHolder = "*";
  }

  public AllMaskStrategy(@NonNull String placeHolder) {
    this.placeHolder = placeHolder;
  }

  @Nullable
  @Override
  public String getMaskedMessage(@Nullable String originalMessage) {
    if (originalMessage == null) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < originalMessage.length(); i++) {
      sb.append(placeHolder);
    }
    return sb.toString();
  }
}
