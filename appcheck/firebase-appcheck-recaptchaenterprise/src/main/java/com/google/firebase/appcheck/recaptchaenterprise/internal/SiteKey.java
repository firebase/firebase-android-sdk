package com.google.firebase.appcheck.recaptchaenterprise.internal;

import androidx.annotation.NonNull;
import java.util.Objects;

public class SiteKey {
  private final String value;

  public SiteKey(@NonNull String value) {
    this.value = Objects.requireNonNull(value, "Site key cannot be null");
  }

  @NonNull
  public String value() {
    return value;
  }
}
