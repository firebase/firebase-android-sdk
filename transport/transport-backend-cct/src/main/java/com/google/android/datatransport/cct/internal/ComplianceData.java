// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.datatransport.cct.internal;

import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ComplianceData {
  public enum ProductIdOrigin {
    NOT_SET(0),
    EVENT_OVERRIDE(5);

    private static final SparseArray<ProductIdOrigin> valueMap = new SparseArray<>();

    private final int value;

    static {
      valueMap.put(0, NOT_SET);
      valueMap.put(5, EVENT_OVERRIDE);
    }

    ProductIdOrigin(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    @Nullable
    public static ProductIdOrigin forNumber(int value) {
      return valueMap.get(value);
    }
  }

  @Nullable
  public abstract ExternalPrivacyContext getPrivacyContext();

  @Nullable
  public abstract ProductIdOrigin getProductIdOrigin();

  @NonNull
  public static Builder builder() {
    return new AutoValue_ComplianceData.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setPrivacyContext(@Nullable ExternalPrivacyContext value);

    @NonNull
    public abstract Builder setProductIdOrigin(@Nullable ProductIdOrigin value);

    @NonNull
    public abstract ComplianceData build();
  }
}
