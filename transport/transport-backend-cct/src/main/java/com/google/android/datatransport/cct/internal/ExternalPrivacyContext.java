package com.google.android.datatransport.cct.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ExternalPrivacyContext {
  @Nullable
  public abstract ExternalPRequestContext getPrequest();

  @NonNull
  public static Builder builder() {
    return new AutoValue_ExternalPrivacyContext.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setPrequest(@Nullable ExternalPRequestContext value);

    @NonNull
    public abstract ExternalPrivacyContext build();
  }
}
