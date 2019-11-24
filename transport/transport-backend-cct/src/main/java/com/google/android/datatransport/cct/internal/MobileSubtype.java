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
  COMBINED(100),
  UNRECOGNIZED(-1),
  ;

  @Nullable
  public static MobileSubtype forNumber(int value) {
    switch (value) {
      case 0:
        return UNKNOWN_MOBILE_SUBTYPE;
      case 1:
        return GPRS;
      case 2:
        return EDGE;
      case 3:
        return UMTS;
      case 4:
        return CDMA;
      case 5:
        return EVDO_0;
      case 6:
        return EVDO_A;
      case 7:
        return RTT;
      case 8:
        return HSDPA;
      case 9:
        return HSUPA;
      case 10:
        return HSPA;
      case 11:
        return IDEN;
      case 12:
        return EVDO_B;
      case 13:
        return LTE;
      case 14:
        return EHRPD;
      case 15:
        return HSPAP;
      case 16:
        return GSM;
      case 17:
        return TD_SCDMA;
      case 18:
        return IWLAN;
      case 19:
        return LTE_CA;
      case 100:
        return COMBINED;
      default:
        return null;
    }
  }

  final int value;

  MobileSubtype(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
