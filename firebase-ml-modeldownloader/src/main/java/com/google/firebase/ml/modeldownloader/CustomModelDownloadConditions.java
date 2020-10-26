// Copyright 2020 Google LLC
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

package com.google.firebase.ml.modeldownloader;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.gms.common.internal.Objects;

/** Conditions to allow download of custom models. */
public class CustomModelDownloadConditions {
  private final boolean isChargingRequired;
  private final boolean isWifiRequired;
  private final boolean isDeviceIdleRequired;

  private CustomModelDownloadConditions(
      boolean isChargingRequired, boolean isWifiRequired, boolean isDeviceIdleRequired) {
    this.isChargingRequired = isChargingRequired;
    this.isWifiRequired = isWifiRequired;
    this.isDeviceIdleRequired = isDeviceIdleRequired;
  }

  /** @return true if charging is required for download. */
  public boolean isChargingRequired() {
    return isChargingRequired;
  }

  /** @return true if wifi is required for download. */
  public boolean isWifiRequired() {
    return isWifiRequired;
  }

  /** @return true if device idle is required for download. */
  public boolean isDeviceIdleRequired() {
    return isDeviceIdleRequired;
  }

  /** Builder of {@link CustomModelDownloadConditions}. */
  public static class Builder {
    private boolean isChargingRequired = false;
    private boolean isWifiRequired = false;
    private boolean isDeviceIdleRequired = false;

    /** Sets whether charging is required. Only works on Android N and above. */
    @NonNull
    @RequiresApi(VERSION_CODES.N)
    @TargetApi(VERSION_CODES.N)
    public Builder requireCharging() {
      this.isChargingRequired = true;
      return this;
    }

    /** Sets whether wifi is required. */
    @NonNull
    public Builder requireWifi() {
      this.isWifiRequired = true;
      return this;
    }

    /**
     * Sets whether device idle is required.
     *
     * <p>Idle mode is a loose definition provided by the system, which means that the device is not
     * in use, and has not been in use for some time.
     *
     * <p>Only works on Android N and above.
     */
    @NonNull
    @RequiresApi(VERSION_CODES.N)
    @TargetApi(VERSION_CODES.N)
    public Builder requireDeviceIdle() {
      this.isDeviceIdleRequired = true;
      return this;
    }

    /** Builds {@link CustomModelDownloadConditions}. */
    @NonNull
    public CustomModelDownloadConditions build() {
      return new CustomModelDownloadConditions(
          isChargingRequired, isWifiRequired, isDeviceIdleRequired);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof CustomModelDownloadConditions)) {
      return false;
    }

    CustomModelDownloadConditions other = (CustomModelDownloadConditions) o;
    return isChargingRequired == other.isChargingRequired
        && isDeviceIdleRequired == other.isDeviceIdleRequired
        && isWifiRequired == other.isWifiRequired;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(isChargingRequired, isWifiRequired, isDeviceIdleRequired);
  }
}
