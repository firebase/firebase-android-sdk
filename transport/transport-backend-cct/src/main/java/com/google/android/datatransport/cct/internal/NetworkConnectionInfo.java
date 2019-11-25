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

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NetworkConnectionInfo {

  public enum NetworkType {
    MOBILE(0),
    WIFI(1),
    MOBILE_MMS(2),
    MOBILE_SUPL(3),
    MOBILE_DUN(4),
    MOBILE_HIPRI(5),
    WIMAX(6),
    BLUETOOTH(7),
    DUMMY(8),
    ETHERNET(9),
    MOBILE_FOTA(10),
    MOBILE_IMS(11),
    MOBILE_CBS(12),
    WIFI_P2P(13),
    MOBILE_IA(14),
    MOBILE_EMERGENCY(15),
    PROXY(16),
    VPN(17),

    NONE(-1);

    private final int value;

    NetworkType(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  public enum MobileSubtype {
    UNKNOWN_MOBILE_SUBTYPE(0),
    GPRS(1),
    EDGE(2),
    UMTS(3),
    CDMA(4),
    EVDO_0(5),
    EVDO_A(6),
    RTT(7),
    HSDPA(8),
    HSUPA(9),
    HSPA(10),
    IDEN(11),
    EVDO_B(12),
    LTE(13),
    EHRPD(14),
    HSPAP(15),
    GSM(16),
    TD_SCDMA(17),
    IWLAN(18),
    LTE_CA(19),

    // COMBINED has value -1 in NetworkIdentity.java, but is given the value
    // 100 here to save (disk) space. The value -1 takes up the full 10 bytes in
    // a varint for enums, but the value 100 only takes up 1 byte.
    COMBINED(100);

    private final int value;

    MobileSubtype(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  @Nullable
  public abstract NetworkType getNetworkType();

  @Nullable
  public abstract MobileSubtype getMobileSubtype();

  static Builder builder() {
    return new AutoValue_NetworkConnectionInfo.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setNetworkType(NetworkType value);

    abstract Builder setMobileSubtype(MobileSubtype value);

    abstract NetworkConnectionInfo build();
  }
}
