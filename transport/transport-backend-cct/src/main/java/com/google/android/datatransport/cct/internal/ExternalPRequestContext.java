package com.google.android.datatransport.cct.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ExternalPRequestContext {
  @Nullable
  public abstract Integer getOriginAssociatedProductId();

  @NonNull
  public static Builder builder() {
    return new AutoValue_ExternalPRequestContext.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setOriginAssociatedProductId(@Nullable Integer value);

    @NonNull
    public abstract ExternalPRequestContext build();
  }
}
