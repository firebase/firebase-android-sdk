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

import android.util.SparseArray;
import androidx.annotation.Nullable;

public enum QosTier {
  DEFAULT(0),
  UNMETERED_ONLY(1),
  UNMETERED_OR_DAILY(2),
  FAST_IF_RADIO_AWAKE(3),
  NEVER(4),
  UNRECOGNIZED(-1);

  private final int value;

  private static final SparseArray<QosTier> valueMap = new SparseArray<>();

  static {
    valueMap.put(0, DEFAULT);
    valueMap.put(1, UNMETERED_ONLY);
    valueMap.put(2, UNMETERED_OR_DAILY);
    valueMap.put(3, FAST_IF_RADIO_AWAKE);
    valueMap.put(4, NEVER);
    valueMap.put(-1, UNRECOGNIZED);
  }

  private QosTier(int value) {
    this.value = value;
  }

  public final int getNumber() {
    return value;
  }

  @Nullable
  public static QosTier forNumber(int value) {
    switch (value) {
      case 0:
        return DEFAULT;
      case 1:
        return UNMETERED_ONLY;
      case 2:
        return UNMETERED_OR_DAILY;
      case 3:
        return FAST_IF_RADIO_AWAKE;
      case 4:
        return NEVER;
      default:
        return null;
    }
  }
}
