// Copyright 2019 Google LLC
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class AndroidClientInfo {
  /** This comes from android.os.Build.VERSION.SDK_INT. */
  @Nullable
  public abstract Integer getSdkVersion();

  /**
   * Textual description of the client platform. e.g., "Nexus 4". This comes from
   * android.os.Build.MODEL.
   */
  @Nullable
  public abstract String getModel();

  /**
   * The name of the hardware (from the kernel command line or /proc). This comes from
   * android.os.Build.Hardware. e.g., "mako".
   */
  @Nullable
  public abstract String getHardware();

  /** The name of the industrial design. e.g., "mako". This comes from android.os.Build.Device. */
  @Nullable
  public abstract String getDevice();

  /** The name of the overall product. e.g., "occam". This comes from android.os.Build.Product. */
  @Nullable
  public abstract String getProduct();

  /** This comes from android.os.Build.ID. e.g., something like "JRN54F". */
  @Nullable
  public abstract String getOsBuild();

  /** The manufacturer of the hardware. This comes from android.os.Build.MANUFACTURER */
  @Nullable
  public abstract String getManufacturer();

  /** Device model/build fingerprint. This comes from android.os.Build.FINGERPRINT. */
  @Nullable
  public abstract String getFingerprint();

  /**
   * The chosen locale from the client. e.g., "en_US", "ko_KR", "en_GB". NB: Often set as just
   * locale-derived language; e.g., "en", "ko".
   */
  @Nullable
  public abstract String getLocale();

  /** Locale-derived country, chosen by the user; e.g., "US", "KR", "JP". */
  @Nullable
  public abstract String getCountry();

  /** The mobile country code / mobile network code (MCC/MNC). */
  @Nullable
  public abstract String getMccMnc();

  /**
   * The client application version. The java int version in the android package converted to
   * string.
   */
  @Nullable
  public abstract String getApplicationBuild();

  @NonNull
  public static Builder builder() {
    return new AutoValue_AndroidClientInfo.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public abstract Builder setSdkVersion(@Nullable Integer value);

    @NonNull
    public abstract Builder setModel(@Nullable String value);

    @NonNull
    public abstract Builder setHardware(@Nullable String value);

    @NonNull
    public abstract Builder setDevice(@Nullable String value);

    @NonNull
    public abstract Builder setProduct(@Nullable String value);

    @NonNull
    public abstract Builder setOsBuild(@Nullable String value);

    @NonNull
    public abstract Builder setManufacturer(@Nullable String value);

    @NonNull
    public abstract Builder setFingerprint(@Nullable String value);

    @NonNull
    public abstract Builder setCountry(@Nullable String value);

    @NonNull
    public abstract Builder setLocale(@Nullable String value);

    @NonNull
    public abstract Builder setMccMnc(@Nullable String value);

    @NonNull
    public abstract Builder setApplicationBuild(@Nullable String value);

    @NonNull
    public abstract AndroidClientInfo build();
  }
}
